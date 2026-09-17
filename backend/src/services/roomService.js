const { v4: uuidv4 } = require('uuid');
const Room = require('../models/Room');
const RoomMember = require('../models/RoomMember');
const Draft = require('../models/Draft');
const User = require('../models/User');

class RoomService {
  /**
   * Create a new room with owner
   */
  async createRoom({ name, ownerId, maxParticipants = 20 }) {
    if (!name || !ownerId) {
      const err = new Error('Room name and ownerId are required');
      err.statusCode = 400;
      throw err;
    }

    // Ensure owner user exists in User collection
    await User.findOneAndUpdate(
      { userId: ownerId },
      { $setOnInsert: { userId: ownerId, username: `User_${ownerId.slice(0, 6)}` } },
      { upsert: true, new: true }
    );

    const roomId = `room_${uuidv4().replace(/-/g, '').slice(0, 10)}`;

    const room = await Room.create({
      roomId,
      name: name.trim(),
      ownerId,
      maxParticipants: Math.min(Math.max(maxParticipants, 3), 50),
      status: 'IDLE',
    });

    // Automatically add owner as first member
    await RoomMember.create({
      roomId,
      userId: ownerId,
      username: `User_${ownerId.slice(0, 6)}`,
      role: 'OWNER',
      connectionStatus: 'CONNECTED',
    });

    return this.getRoomDetails(roomId);
  }

  /**
   * List active rooms (status != 'CLOSED')
   */
  async listRooms() {
    // Immediate purge of DISCONNECTED members on room refresh
    await RoomMember.deleteMany({ connectionStatus: 'DISCONNECTED' });

    const rooms = await Room.find({ status: { $ne: 'CLOSED' } })
      .sort({ createdAt: -1 })
      .limit(50);

    const roomSummaries = await Promise.all(
      rooms.map(async (room) => {
        const participants = await RoomMember.find({ roomId: room.roomId }).select('userId username role connectionStatus');
        const connectedParticipants = participants.filter((p) => p.connectionStatus === 'CONNECTED');
        
        // If room has no connected members, purge the room doc
        if (connectedParticipants.length === 0) {
          await Room.deleteOne({ roomId: room.roomId });
          return null;
        }

        return {
          id: room.roomId,
          roomId: room.roomId,
          name: room.name,
          ownerId: room.ownerId,
          status: room.status,
          maxParticipants: room.maxParticipants,
          participants: connectedParticipants.map((p) => ({
            userId: p.userId,
            username: p.username,
            role: p.role,
            connectionStatus: p.connectionStatus,
          })),
          participantCount: connectedParticipants.length,
          activeSpinId: room.activeSpinId,
          createdAt: room.createdAt,
          updatedAt: room.updatedAt,
        };
      })
    );

    // Only return active rooms that have connected participants
    return roomSummaries.filter((r) => r !== null && r.participantCount > 0);
  }

  /**
   * Join an existing room
   */
  async joinRoom({ roomId, userId, username, socketId = null }) {
    const room = await Room.findOne({ roomId });
    if (!room) {
      const err = new Error(`Room '${roomId}' does not exist`);
      err.statusCode = 404;
      throw err;
    }

    if (room.status === 'CLOSED') {
      const err = new Error('Room is closed');
      err.statusCode = 400;
      throw err;
    }

    // Check capacity
    const currentMemberCount = await RoomMember.countDocuments({ roomId });
    const existingMember = await RoomMember.findOne({ roomId, userId });

    if (!existingMember && currentMemberCount >= room.maxParticipants) {
      const err = new Error(`Room has reached maximum capacity of ${room.maxParticipants}`);
      err.statusCode = 400;
      throw err;
    }

    // Ensure user record exists
    const displayName = username && username.trim() ? username.trim() : `User_${userId.slice(0, 6)}`;
    await User.findOneAndUpdate(
      { userId },
      { $set: { username: displayName } },
      { upsert: true, new: true }
    );

    const role = room.ownerId === userId ? 'OWNER' : 'MEMBER';

    const member = await RoomMember.findOneAndUpdate(
      { roomId, userId },
      {
        $set: {
          username: displayName,
          role,
          connectionStatus: 'CONNECTED',
          socketId,
          lastSeenAt: new Date(),
        },
      },
      { upsert: true, new: true }
    );

    return {
      room: await this.getRoomDetails(roomId),
      joinedMember: member,
    };
  }

  /**
   * Leave a room
   */
  async leaveRoom({ roomId, userId }) {
    const room = await Room.findOne({ roomId });
    if (!room) {
      const err = new Error(`Room '${roomId}' not found`);
      err.statusCode = 404;
      throw err;
    }

    const member = await RoomMember.findOne({ roomId, userId });
    if (!member) {
      const err = new Error(`User '${userId}' is not in room '${roomId}'`);
      err.statusCode = 404;
      throw err;
    }

    await RoomMember.deleteOne({ roomId, userId });

    // If owner leaves, transfer ownership to oldest remaining member or mark idle
    const remainingMembers = await RoomMember.find({ roomId }).sort({ createdAt: 1 });
    if (remainingMembers.length > 0 && room.ownerId === userId) {
      const newOwner = remainingMembers[0];
      room.ownerId = newOwner.userId;
      newOwner.role = 'OWNER';
      await Promise.all([room.save(), newOwner.save()]);
    } else if (remainingMembers.length === 0) {
      room.status = 'IDLE';
      room.activeSpinId = null;
      await room.save();
    }

    return {
      success: true,
      remainingCount: remainingMembers.length,
      newOwnerId: room.ownerId,
    };
  }

  /**
   * Get full authoritative room state, participants, and shared drafts
   */
  async getRoomDetails(roomId) {
    const room = await Room.findOne({ roomId });
    if (!room) {
      const err = new Error(`Room '${roomId}' not found`);
      err.statusCode = 404;
      throw err;
    }

    const [participants, sharedDrafts] = await Promise.all([
      RoomMember.find({ roomId }).sort({ role: 1, createdAt: 1 }),
      Draft.find({ sharedInRooms: roomId }).sort({ createdAt: -1 }),
    ]);

    return {
      id: room.roomId,
      name: room.name,
      ownerId: room.ownerId,
      status: room.status,
      maxParticipants: room.maxParticipants,
      activeSpinId: room.activeSpinId,
      participants: participants.map((p) => ({
        userId: p.userId,
        username: p.username,
        role: p.role,
        connectionStatus: p.connectionStatus,
        lastSeenAt: p.lastSeenAt,
      })),
      sharedDrafts: sharedDrafts.map((d) => ({
        id: d.draftId,
        userId: d.userId,
        title: d.title,
        durationMs: d.durationMs,
        fileUrl: d.fileUrl,
        effectApplied: d.effectApplied,
        createdAt: d.createdAt,
      })),
      createdAt: room.createdAt,
      updatedAt: room.updatedAt,
    };
  }

  /**
   * Share a draft with room members
   */
  async shareDraft({ roomId, userId, draftId, title, durationMs, effectApplied, fileUrl, audioBase64 }) {
    const room = await Room.findOne({ roomId });
    if (!room) {
      const err = new Error(`Room '${roomId}' not found`);
      err.statusCode = 404;
      throw err;
    }

    const member = await RoomMember.findOne({ roomId, userId });
    if (!member) {
      const err = new Error(`User '${userId}' is not a member of room '${roomId}'`);
      err.statusCode = 403;
      throw err;
    }

    let draft = await Draft.findOne({ draftId });
    const computedFileUrl = fileUrl || (audioBase64 ? `/api/drafts/${draftId}/audio` : '');
    if (!draft) {
      draft = new Draft({
        draftId,
        userId,
        title: title || 'Voice Draft',
        durationMs: durationMs || 0,
        effectApplied: effectApplied || 'NONE',
        fileUrl: computedFileUrl,
        audioBase64: audioBase64 || '',
        sharedInRooms: [roomId],
      });
      await draft.save();
    } else {
      if (effectApplied) draft.effectApplied = effectApplied;
      if (audioBase64) draft.audioBase64 = audioBase64;
      if (fileUrl || audioBase64) draft.fileUrl = computedFileUrl;
      if (!draft.sharedInRooms.includes(roomId)) {
        draft.sharedInRooms.push(roomId);
      }
      await draft.save();
    }

    return {
      draft: {
        id: draft.draftId,
        userId: draft.userId,
        title: draft.title,
        durationMs: draft.durationMs,
        effectApplied: draft.effectApplied,
        fileUrl: draft.fileUrl || `/api/drafts/${draft.draftId}/audio`,
        createdAt: draft.createdAt,
      },
      sharedBy: member.username,
    };
  }

  /**
   * Update participant connection state (for presence and disconnect tracking)
   */
  async setConnectionStatus(socketId, status) {
    const member = await RoomMember.findOne({ socketId });
    if (member) {
      member.connectionStatus = status;
      member.lastSeenAt = new Date();
      if (status === 'DISCONNECTED') {
        member.socketId = null;
      }
      await member.save();
      return member;
    }
    return null;
  }
}

module.exports = new RoomService();
