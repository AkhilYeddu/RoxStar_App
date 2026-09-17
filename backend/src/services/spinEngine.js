const { v4: uuidv4 } = require('uuid');
const Spin = require('../models/Spin');
const SpinParticipant = require('../models/SpinParticipant');
const SpinEvent = require('../models/SpinEvent');
const Room = require('../models/Room');
const RoomMember = require('../models/RoomMember');
const User = require('../models/User');
const logger = require('../utils/logger');
const config = require('../config/config');

class SpinEngine {
  constructor() {
    // In-memory active spin timers mapped by spinId
    this.activeTimers = new Map();
    this.io = null; // Socket.IO server reference injected at startup
  }

  setIo(ioInstance) {
    this.io = ioInstance;
  }

  /**
   * Validate conditions and initiate a new Spin
   * Edge cases handled:
   * - Insufficient players (< 3) -> 400
   * - Max player cap (> 20) -> capped
   * - Duplicate start requests / Active spin already running -> 409
   * - Non-owner / unauthorized start attempt -> 403
   */
  async startSpin({ roomId, initiatedBy, idempotencyKey = null }) {
    // 1. Verify Room existence
    const room = await Room.findOne({ roomId });
    if (!room) {
      const err = new Error(`Room '${roomId}' not found`);
      err.statusCode = 404;
      throw err;
    }

    // 2. Only Room Owner or Admin can start the spin
    if (room.ownerId !== initiatedBy) {
      const err = new Error('Only the room owner can start the spin wheel');
      err.statusCode = 403;
      throw err;
    }

    // 3. Edge Case 1: Duplicate start requests / active spin guard
    if (room.status === 'IN_SPIN' || room.activeSpinId) {
      const activeSpin = await Spin.findOne({ spinId: room.activeSpinId });
      if (activeSpin && (activeSpin.status === 'RUNNING' || activeSpin.status === 'WAITING')) {
        const err = new Error('A spin is already actively running in this room');
        err.statusCode = 409;
        err.activeSpinId = activeSpin.spinId;
        throw err;
      }
    }

    // 4. Retrieve connected, eligible members
    const eligibleMembers = await RoomMember.find({
      roomId,
      connectionStatus: 'CONNECTED',
    });

    // 5. Edge Case 6: Insufficient players (< 3)
    if (eligibleMembers.length < config.spinRules.minParticipants) {
      const err = new Error(
        `Insufficient players to start spin. At least ${config.spinRules.minParticipants} eligible players required, found ${eligibleMembers.length}`
      );
      err.statusCode = 400;
      throw err;
    }

    // Cap at maxParticipants (20)
    const participantsPool = eligibleMembers.slice(0, config.spinRules.maxParticipants);

    const spinId = `spin_${uuidv4().replace(/-/g, '').slice(0, 10)}`;

    // 6. Create Spin Record
    const spin = await Spin.create({
      spinId,
      roomId,
      initiatedBy,
      status: 'WAITING',
      currentRound: 0,
      totalInitialParticipants: participantsPool.length,
      startedAt: new Date(),
      idempotencyKey,
    });

    // Update Room status to IN_SPIN
    room.status = 'IN_SPIN';
    room.activeSpinId = spinId;
    await room.save();

    // 7. Seed Spin Participants
    const participantDocs = participantsPool.map((member) => ({
      spinId,
      roomId,
      userId: member.userId,
      username: member.username,
      status: 'ELIGIBLE',
      eliminationOrder: null,
      eliminatedRound: null,
      eliminatedAt: null,
    }));
    await SpinParticipant.insertMany(participantDocs);

    // Transition: WAITING -> RUNNING
    spin.status = 'RUNNING';
    spin.currentRound = 1;
    await spin.save();

    // 8. Record initial event & broadcast spin_started
    const initialEligibleIds = participantsPool.map((p) => p.userId);
    const startPayload = {
      spinId,
      roomId,
      status: 'RUNNING',
      round: 1,
      activeParticipants: initialEligibleIds,
      eliminatedParticipants: [],
      winnerId: null,
      nextEliminationCountdownSeconds: config.spinRules.eliminationIntervalMs / 1000,
    };

    await this.recordSpinEvent(spinId, roomId, 'spin_started', 1, startPayload);
    this.broadcastToRoom(roomId, 'spin_started', startPayload);

    logger.info({ spinId, roomId, participantsCount: initialEligibleIds.length }, 'Spin started successfully');

    // 9. Start the authoritative 5-second elimination cadence timer
    this.scheduleNextElimination(spinId, roomId);

    return {
      spinId,
      roomId,
      status: 'RUNNING',
      participantsCount: participantsPool.length,
      participants: initialEligibleIds,
    };
  }

  /**
   * Authoritative 5-second elimination loop with monotonic drift-free timer
   */
  scheduleNextElimination(spinId, roomId) {
    if (this.activeTimers.has(spinId)) {
      clearTimeout(this.activeTimers.get(spinId));
    }

    const timer = setTimeout(async () => {
      try {
        await this.processNextEliminationRound(spinId, roomId);
      } catch (err) {
        logger.error({ err, spinId, roomId }, 'Error during elimination round execution');
      }
    }, config.spinRules.eliminationIntervalMs);

    this.activeTimers.set(spinId, timer);
  }

  /**
   * Process an elimination round
   */
  async processNextEliminationRound(spinId, roomId) {
    const spin = await Spin.findOne({ spinId });
    if (!spin || spin.status !== 'RUNNING') {
      this.clearActiveTimer(spinId);
      return;
    }

    // Retrieve remaining eligible participants
    const activeParticipants = await SpinParticipant.find({
      spinId,
      status: 'ELIGIBLE',
    });

    // Edge Case 7: Last players leaving mid-spin (no eligible players left)
    if (activeParticipants.length === 0) {
      await this.abortSpin(spinId, roomId, 'ALL_PARTICIPANTS_LEFT');
      return;
    }

    // Only 1 participant remaining -> WINNER FOUND!
    if (activeParticipants.length === 1) {
      await this.announceWinner(spin, roomId, activeParticipants[0]);
      return;
    }

    // Edge Case 3 Check: Filter out any participants who disconnected mid-spin
    // If a participant disconnected, we can either eliminate them directly this round
    // or select a random survivor to eliminate.
    const disconnectedActive = [];
    for (const p of activeParticipants) {
      const member = await RoomMember.findOne({ roomId, userId: p.userId });
      if (!member || member.connectionStatus === 'DISCONNECTED') {
        disconnectedActive.push(p);
      }
    }

    let victim;
    if (disconnectedActive.length > 0) {
      // Prioritize eliminating disconnected participant to keep active players in game
      victim = disconnectedActive[0];
    } else {
      // Pick random active participant to eliminate
      const randomIndex = Math.floor(Math.random() * activeParticipants.length);
      victim = activeParticipants[randomIndex];
    }

    const round = spin.currentRound;
    const eliminationOrder = spin.totalInitialParticipants - activeParticipants.length + 1;

    // Update participant record
    victim.status = 'ELIMINATED';
    victim.eliminationOrder = eliminationOrder;
    victim.eliminatedRound = round;
    victim.eliminatedAt = new Date();
    await victim.save();

    const remainingEligible = activeParticipants
      .filter((p) => p.userId !== victim.userId)
      .map((p) => p.userId);

    // Next sequence number
    const sequenceNumber = (await SpinEvent.countDocuments({ spinId })) + 1;

    const eliminationPayload = {
      spinId,
      round,
      eliminatedUserId: victim.userId,
      eliminatedUsername: victim.username,
      remainingUsers: remainingEligible,
      remainingCount: remainingEligible.length,
      nextEliminationCountdownSeconds: config.spinRules.eliminationIntervalMs / 1000,
    };

    // Record Event and Broadcast
    await this.recordSpinEvent(spinId, roomId, 'user_eliminated', sequenceNumber, eliminationPayload);
    this.broadcastToRoom(roomId, 'user_eliminated', eliminationPayload);

    logger.info(
      { spinId, round, eliminated: victim.username, remaining: remainingEligible.length },
      'User eliminated in spin round'
    );

    // Check if exactly one participant remains after this elimination
    if (remainingEligible.length === 1) {
      const winnerParticipant = await SpinParticipant.findOne({
        spinId,
        userId: remainingEligible[0],
      });
      await this.announceWinner(spin, roomId, winnerParticipant);
    } else {
      // Increment round and schedule next 5-second interval
      spin.currentRound += 1;
      await spin.save();
      this.scheduleNextElimination(spinId, roomId);
    }
  }

  /**
   * Finalize spin and announce the sole winner
   */
  async announceWinner(spin, roomId, winnerParticipant) {
    this.clearActiveTimer(spin.spinId);

    winnerParticipant.status = 'WINNER';
    await winnerParticipant.save();

    spin.status = 'COMPLETED';
    spin.winnerId = winnerParticipant.userId;
    spin.completedAt = new Date();
    await spin.save();

    // Reset room status
    const room = await Room.findOne({ roomId });
    if (room) {
      room.status = 'IDLE';
      room.activeSpinId = null;
      await room.save();
    }

    // Award 100 virtual points to the winner
    await User.findOneAndUpdate(
      { userId: winnerParticipant.userId },
      { $inc: { virtualPoints: 100 } }
    );

    const sequenceNumber = (await SpinEvent.countDocuments({ spinId: spin.spinId })) + 1;
    const winnerPayload = {
      spinId: spin.spinId,
      roomId,
      winnerId: winnerParticipant.userId,
      winnerUsername: winnerParticipant.username,
      virtualPointsAwarded: 100,
      totalRounds: spin.currentRound,
      completedAt: spin.completedAt,
      spinState: {
        spinId: spin.spinId,
        roomId,
        status: 'COMPLETED',
        winnerId: winnerParticipant.userId,
        activeParticipants: [winnerParticipant.userId],
      },
    };

    await this.recordSpinEvent(spin.spinId, roomId, 'winner_announced', sequenceNumber, winnerPayload);
    this.broadcastToRoom(roomId, 'winner_announced', winnerPayload);

    logger.info({ spinId: spin.spinId, winner: winnerParticipant.username }, 'Spin wheel completed. Winner announced.');
  }

  /**
   * Abort spin in abnormal conditions (e.g. all players leave mid-spin)
   */
  async abortSpin(spinId, roomId, reason) {
    this.clearActiveTimer(spinId);

    const spin = await Spin.findOne({ spinId });
    if (spin) {
      spin.status = 'ABORTED';
      spin.abortedReason = reason;
      spin.completedAt = new Date();
      await spin.save();
    }

    const room = await Room.findOne({ roomId });
    if (room) {
      room.status = 'IDLE';
      room.activeSpinId = null;
      await room.save();
    }

    const sequenceNumber = (await SpinEvent.countDocuments({ spinId })) + 1;
    const abortPayload = {
      spinId,
      roomId,
      status: 'ABORTED',
      reason,
      timestamp: new Date(),
    };

    await this.recordSpinEvent(spinId, roomId, 'spin_aborted', sequenceNumber, abortPayload);
    this.broadcastToRoom(roomId, 'spin_aborted', abortPayload);

    logger.warn({ spinId, roomId, reason }, 'Spin aborted');
  }

  /**
   * Edge Case 4: Reconnect during spin - return current state snapshot
   */
  async getCurrentSpinSnapshot(roomId) {
    const room = await Room.findOne({ roomId });
    if (!room || !room.activeSpinId) return null;

    const spin = await Spin.findOne({ spinId: room.activeSpinId });
    if (!spin) return null;

    const [active, eliminated] = await Promise.all([
      SpinParticipant.find({ spinId: spin.spinId, status: 'ELIGIBLE' }),
      SpinParticipant.find({ spinId: spin.spinId, status: { $ne: 'ELIGIBLE' } }).sort({ eliminationOrder: 1 }),
    ]);

    return {
      spinId: spin.spinId,
      roomId,
      status: spin.status,
      round: spin.currentRound,
      totalInitialParticipants: spin.totalInitialParticipants,
      activeParticipants: active.map((p) => p.userId),
      eliminatedParticipants: eliminated.map((p) => ({
        userId: p.userId,
        username: p.username,
        order: p.eliminationOrder,
        round: p.eliminatedRound,
      })),
      winnerId: spin.winnerId,
      nextEliminationCountdownSeconds: 5,
    };
  }

  /**
   * Record auditable sequential spin events
   */
  async recordSpinEvent(spinId, roomId, eventType, sequenceNumber, payload) {
    return SpinEvent.create({
      eventId: `ev_${uuidv4().replace(/-/g, '').slice(0, 10)}`,
      spinId,
      roomId,
      eventType,
      sequenceNumber,
      payload,
    });
  }

  broadcastToRoom(roomId, eventName, payload) {
    if (this.io) {
      this.io.to(roomId).emit(eventName, payload);
    }
  }

  clearActiveTimer(spinId) {
    if (this.activeTimers.has(spinId)) {
      clearTimeout(this.activeTimers.get(spinId));
      this.activeTimers.delete(spinId);
    }
  }
}

module.exports = new SpinEngine();
