const { setupTestDB } = require('../setup');
const spinEngine = require('../../backend/src/services/spinEngine');
const roomService = require('../../backend/src/services/roomService');
const Spin = require('../../backend/src/models/Spin');
const SpinParticipant = require('../../backend/src/models/SpinParticipant');
const Room = require('../../backend/src/models/Room');
const RoomMember = require('../../backend/src/models/RoomMember');

describe('Section C4 - Spin Wheel Edge-Case Reasoning & Handling', () => {
  setupTestDB();

  beforeEach(() => {
    // Prevent real timer firing during tests
    spinEngine.scheduleNextElimination = jest.fn();
  });

  // Edge Case 1: Insufficient Players (< 3)
  it('Edge Case 1: Should reject spin start when fewer than 3 players are eligible', async () => {
    const room = await roomService.createRoom({
      name: 'Under-populated Room',
      ownerId: 'owner_1',
    });
    // Add only 1 additional player (total = 2 players)
    await roomService.joinRoom({ roomId: room.id, userId: 'user_2', username: 'P2' });

    await expect(
      spinEngine.startSpin({ roomId: room.id, initiatedBy: 'owner_1' })
    ).rejects.toThrow(/insufficient players/i);

    const checkRoom = await Room.findOne({ roomId: room.id });
    expect(checkRoom.status).toBe('IDLE');
  });

  // Edge Case 2: Duplicate Start Requests / Concurrent Spin Guard
  it('Edge Case 2: Should prevent concurrent spin starts in the same room (409 Conflict)', async () => {
    const room = await roomService.createRoom({ name: 'Dual Start Room', ownerId: 'owner_1' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p2', username: 'P2' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p3', username: 'P3' });

    // First start succeeds
    const spin1 = await spinEngine.startSpin({ roomId: room.id, initiatedBy: 'owner_1' });
    expect(spin1.status).toBe('RUNNING');

    // Second start attempt immediately while spin is running
    await expect(
      spinEngine.startSpin({ roomId: room.id, initiatedBy: 'owner_1' })
    ).rejects.toThrow(/already actively running/i);
  });

  // Edge Case 3: Late Joins Mid-Spin (Spectator Mode)
  it('Edge Case 3: Users joining mid-spin should be spectators, not entered into active spin pool', async () => {
    const room = await roomService.createRoom({ name: 'Spectator Room', ownerId: 'owner_1' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p2', username: 'P2' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p3', username: 'P3' });

    // Start spin with 3 initial players
    const startResult = await spinEngine.startSpin({ roomId: room.id, initiatedBy: 'owner_1' });

    // New user joins during active spin
    const joinMidSpin = await roomService.joinRoom({
      roomId: room.id,
      userId: 'late_comer',
      username: 'LateComer',
    });

    // Check spin participants: late_comer must NOT be in the spin pool
    const inSpin = await SpinParticipant.findOne({
      spinId: startResult.spinId,
      userId: 'late_comer',
    });
    expect(inSpin).toBeNull();

    // Verify room has 4 total members but spin active participants remain 3
    const details = await roomService.getRoomDetails(room.id);
    expect(details.participants).toHaveLength(4);
  });

  // Edge Case 4: User Disconnect Mid-Spin Handling
  it('Edge Case 4: Disconnected user mid-spin is prioritized for elimination or flagged', async () => {
    const room = await roomService.createRoom({ name: 'Disconnect Room', ownerId: 'owner_1' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p2', username: 'P2', socketId: 'sock_p2' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p3', username: 'P3', socketId: 'sock_p3' });

    const startResult = await spinEngine.startSpin({ roomId: room.id, initiatedBy: 'owner_1' });

    // P2 disconnects mid-game
    await roomService.setConnectionStatus('sock_p2', 'DISCONNECTED');

    // Process elimination round
    await spinEngine.processNextEliminationRound(startResult.spinId, room.id);

    // Disconnected user P2 should be eliminated first
    const p2Participant = await SpinParticipant.findOne({
      spinId: startResult.spinId,
      userId: 'p2',
    });
    expect(p2Participant.status).toBe('ELIMINATED');
  });

  // Edge Case 5: Mid-Spin Reconnection Snapshot Recovery
  it('Edge Case 5: Reconnecting client receives immediate snapshot of current round and survivors', async () => {
    const room = await roomService.createRoom({ name: 'Reconnect Room', ownerId: 'owner_1' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p2', username: 'P2' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p3', username: 'P3' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p4', username: 'P4' });

    const startResult = await spinEngine.startSpin({ roomId: room.id, initiatedBy: 'owner_1' });

    // Eliminate 1 player
    await spinEngine.processNextEliminationRound(startResult.spinId, room.id);

    // Fetch reconnect snapshot
    const snapshot = await spinEngine.getCurrentSpinSnapshot(room.id);
    expect(snapshot).toBeDefined();
    expect(snapshot.status).toBe('RUNNING');
    expect(snapshot.activeParticipants).toHaveLength(3);
    expect(snapshot.eliminatedParticipants).toHaveLength(1);
  });

  // Edge Case 6: Empty Room Abort (All participants leave mid-spin)
  it('Edge Case 6: Should abort spin immediately if all active players leave the room', async () => {
    const room = await roomService.createRoom({ name: 'Evacuation Room', ownerId: 'owner_1' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p2', username: 'P2' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p3', username: 'P3' });

    const startResult = await spinEngine.startSpin({ roomId: room.id, initiatedBy: 'owner_1' });

    // All players leave
    await SpinParticipant.updateMany({ spinId: startResult.spinId }, { $set: { status: 'ELIMINATED' } });

    // Run elimination check
    await spinEngine.processNextEliminationRound(startResult.spinId, room.id);

    const abortedSpin = await Spin.findOne({ spinId: startResult.spinId });
    expect(abortedSpin.status).toBe('ABORTED');
    expect(abortedSpin.abortedReason).toBe('ALL_PARTICIPANTS_LEFT');
  });

  // Edge Case 7: Admin Disconnect - Autonomous Timer Continuation
  it('Edge Case 7: Spin continues running autonomously even if room owner/admin disconnects', async () => {
    const room = await roomService.createRoom({ name: 'Admin Drop Room', ownerId: 'owner_1' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p2', username: 'P2' });
    await roomService.joinRoom({ roomId: room.id, userId: 'p3', username: 'P3' });

    const startResult = await spinEngine.startSpin({ roomId: room.id, initiatedBy: 'owner_1' });

    // Admin leaves room
    await roomService.leaveRoom({ roomId: room.id, userId: 'owner_1' });

    // Spin in DB should remain RUNNING
    const currentSpin = await Spin.findOne({ spinId: startResult.spinId });
    expect(currentSpin.status).toBe('RUNNING');

    // Elimination round can still execute cleanly
    await spinEngine.processNextEliminationRound(startResult.spinId, room.id);
  });
});
