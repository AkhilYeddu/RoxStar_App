const { setupTestDB } = require('../setup');
const roomService = require('../../backend/src/services/roomService');
const Room = require('../../backend/src/models/Room');
const RoomMember = require('../../backend/src/models/RoomMember');

describe('RoomService Unit Tests', () => {
  setupTestDB();

  it('should create a room with owner as initial member', async () => {
    const room = await roomService.createRoom({
      name: 'Vocal Studio 1',
      ownerId: 'user_100',
      maxParticipants: 10,
    });

    expect(room).toBeDefined();
    expect(room.name).toBe('Vocal Studio 1');
    expect(room.ownerId).toBe('user_100');
    expect(room.participants).toHaveLength(1);
    expect(room.participants[0].userId).toBe('user_100');
    expect(room.participants[0].role).toBe('OWNER');
  });

  it('should allow multiple members to join a room', async () => {
    const room = await roomService.createRoom({
      name: 'Jam Session',
      ownerId: 'owner_1',
    });

    const joinResult = await roomService.joinRoom({
      roomId: room.id,
      userId: 'user_2',
      username: 'Singer_2',
    });

    expect(joinResult.room.participants).toHaveLength(2);
    expect(joinResult.joinedMember.role).toBe('MEMBER');
  });

  it('should enforce room maximum capacity', async () => {
    const room = await roomService.createRoom({
      name: 'Trio Room',
      ownerId: 'user_1',
      maxParticipants: 3,
    });

    await roomService.joinRoom({ roomId: room.id, userId: 'user_2', username: 'Member 2' });
    await roomService.joinRoom({ roomId: room.id, userId: 'user_3', username: 'Member 3' });

    // 4th member should be rejected
    await expect(
      roomService.joinRoom({ roomId: room.id, userId: 'user_4', username: 'Member 4' })
    ).rejects.toThrow(/maximum capacity/i);
  });

  it('should transfer room ownership when owner leaves room', async () => {
    const room = await roomService.createRoom({
      name: 'Transfer Test',
      ownerId: 'owner_1',
    });

    await roomService.joinRoom({ roomId: room.id, userId: 'user_2', username: 'Member 2' });

    const leaveResult = await roomService.leaveRoom({
      roomId: room.id,
      userId: 'owner_1',
    });

    expect(leaveResult.success).toBe(true);
    expect(leaveResult.newOwnerId).toBe('user_2');

    const updatedRoom = await roomService.getRoomDetails(room.id);
    expect(updatedRoom.ownerId).toBe('user_2');
  });

  it('should share a draft with room', async () => {
    const room = await roomService.createRoom({
      name: 'Draft Room',
      ownerId: 'user_1',
    });

    const shareResult = await roomService.shareDraft({
      roomId: room.id,
      userId: 'user_1',
      draftId: 'draft_99',
      title: 'Echo Harmony',
      durationMs: 3400,
      effectApplied: 'ECHO',
      fileUrl: '/uploads/draft_99.wav',
    });

    expect(shareResult.draft.id).toBe('draft_99');
    expect(shareResult.draft.effectApplied).toBe('ECHO');

    const details = await roomService.getRoomDetails(room.id);
    expect(details.sharedDrafts).toHaveLength(1);
    expect(details.sharedDrafts[0].title).toBe('Echo Harmony');
  });
});
