const { setupTestDB } = require('../setup');
const spinEngine = require('../../backend/src/services/spinEngine');
const roomService = require('../../backend/src/services/roomService');
const Spin = require('../../backend/src/models/Spin');
const SpinParticipant = require('../../backend/src/models/SpinParticipant');
const SpinEvent = require('../../backend/src/models/SpinEvent');

describe('SpinEngine Unit Tests', () => {
  setupTestDB();

  let testRoom;

  beforeEach(async () => {
    // Override interval for fast unit test execution
    spinEngine.scheduleNextElimination = jest.fn();

    testRoom = await roomService.createRoom({
      name: 'Spin Arena Test',
      ownerId: 'player_1',
    });

    await roomService.joinRoom({ roomId: testRoom.id, userId: 'player_2', username: 'P2' });
    await roomService.joinRoom({ roomId: testRoom.id, userId: 'player_3', username: 'P3' });
    await roomService.joinRoom({ roomId: testRoom.id, userId: 'player_4', username: 'P4' });
  });

  afterEach(() => {
    if (testRoom && testRoom.activeSpinId) {
      spinEngine.clearActiveTimer(testRoom.activeSpinId);
    }
  });

  it('should validate and start spin with 4 eligible players', async () => {
    const startResult = await spinEngine.startSpin({
      roomId: testRoom.id,
      initiatedBy: 'player_1',
    });

    expect(startResult).toBeDefined();
    expect(startResult.status).toBe('RUNNING');
    expect(startResult.participantsCount).toBe(4);

    const spin = await Spin.findOne({ spinId: startResult.spinId });
    expect(spin.status).toBe('RUNNING');
    expect(spin.totalInitialParticipants).toBe(4);

    // Initial spin_started event
    const startEvent = await SpinEvent.findOne({ spinId: startResult.spinId, sequenceNumber: 1 });
    expect(startEvent).toBeDefined();
    expect(startEvent.eventType).toBe('spin_started');
  });

  it('should reject non-owner attempting to start spin', async () => {
    await expect(
      spinEngine.startSpin({
        roomId: testRoom.id,
        initiatedBy: 'player_2', // Not owner
      })
    ).rejects.toThrow(/only the room owner/i);
  });

  it('should execute elimination round and record event', async () => {
    const startResult = await spinEngine.startSpin({
      roomId: testRoom.id,
      initiatedBy: 'player_1',
    });

    // Execute one round manually
    await spinEngine.processNextEliminationRound(startResult.spinId, testRoom.id);

    const eliminated = await SpinParticipant.find({
      spinId: startResult.spinId,
      status: 'ELIMINATED',
    });
    expect(eliminated).toHaveLength(1);

    const active = await SpinParticipant.find({
      spinId: startResult.spinId,
      status: 'ELIGIBLE',
    });
    expect(active).toHaveLength(3);

    // Event sequence 2 should be user_eliminated
    const elimEvent = await SpinEvent.findOne({ spinId: startResult.spinId, sequenceNumber: 2 });
    expect(elimEvent.eventType).toBe('user_eliminated');
  });

  it('should progress until exactly one winner is declared', async () => {
    const startResult = await spinEngine.startSpin({
      roomId: testRoom.id,
      initiatedBy: 'player_1',
    });

    // Progress 3 eliminations (4 -> 3 -> 2 -> 1 winner)
    await spinEngine.processNextEliminationRound(startResult.spinId, testRoom.id);
    await spinEngine.processNextEliminationRound(startResult.spinId, testRoom.id);
    await spinEngine.processNextEliminationRound(startResult.spinId, testRoom.id);

    const finalizedSpin = await Spin.findOne({ spinId: startResult.spinId });
    expect(finalizedSpin.status).toBe('COMPLETED');
    expect(finalizedSpin.winnerId).toBeDefined();

    const winnerParticipant = await SpinParticipant.findOne({
      spinId: startResult.spinId,
      status: 'WINNER',
    });
    expect(winnerParticipant).toBeDefined();
    expect(winnerParticipant.userId).toBe(finalizedSpin.winnerId);

    // Final winner event should exist
    const winnerEvent = await SpinEvent.findOne({
      spinId: startResult.spinId,
      eventType: 'winner_announced',
    });
    expect(winnerEvent).toBeDefined();
    expect(winnerEvent.payload.winnerId).toBe(finalizedSpin.winnerId);
  });
});
