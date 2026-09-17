const roomService = require('../services/roomService');
const spinEngine = require('../services/spinEngine');
const logger = require('../utils/logger');
const RoomMember = require('../models/RoomMember');
const Room = require('../models/Room');

const setupSocketHandlers = (io) => {
  spinEngine.setIo(io);

  io.on('connection', async (socket) => {
    const { roomId, userId, username } = socket.handshake.query;
    logger.info({ socketId: socket.id, roomId, userId, username }, 'Client connected to WebSocket');

    if (roomId && userId) {
      try {
        // 1. Join Socket.IO room channel
        socket.join(roomId);
        socket.roomId = roomId;
        socket.userId = userId;

        // 2. Register/update room membership in DB
        const joinResult = await roomService.joinRoom({
          roomId,
          userId,
          username: username || `User_${userId.slice(0, 6)}`,
          socketId: socket.id,
        });

        // 3. Broadcast mandatory event: user_joined
        const memberUsername = username || (joinResult.joinedMember && joinResult.joinedMember.username) || `User_${userId.slice(0, 6)}`;
        const memberRole = (joinResult.joinedMember && joinResult.joinedMember.role) || 'MEMBER';
        socket.to(roomId).emit('user_joined', {
          userId,
          username: memberUsername,
          role: memberRole,
          connectionStatus: 'CONNECTED',
        });

        // 4. Return authoritative room_state to all room members
        const roomState = await roomService.getRoomDetails(roomId);
        io.to(roomId).emit('room_state', roomState);

        // 5. If a spin is actively running in this room, synchronize spin state immediately (Edge Case 4)
        const activeSpinSnapshot = await spinEngine.getCurrentSpinSnapshot(roomId);
        if (activeSpinSnapshot && activeSpinSnapshot.status === 'RUNNING') {
          socket.emit('spin_started', activeSpinSnapshot);
        }
      } catch (err) {
        logger.error({ err: err.message, roomId, userId }, 'Error processing initial socket connection');
        socket.emit('error_event', { message: err.message });
      }
    }

    // Client requests manual room state refresh
    socket.on('get_room_state', async (data) => {
      try {
        const targetRoomId = data.roomId || socket.roomId;
        if (targetRoomId) {
          const roomState = await roomService.getRoomDetails(targetRoomId);
          socket.emit('room_state', roomState);
        }
      } catch (err) {
        socket.emit('error_event', { message: err.message });
      }
    });

    // Client explicitly shares draft via socket
    socket.on('share_draft', async (draftData) => {
      try {
        const targetRoomId = draftData.roomId || socket.roomId;
        const result = await roomService.shareDraft({
          roomId: targetRoomId,
          userId: socket.userId,
          draftId: draftData.draftId,
          title: draftData.title,
          durationMs: draftData.durationMs,
          effectApplied: draftData.effectApplied,
          fileUrl: draftData.fileUrl,
          audioBase64: draftData.audioBase64,
        });

        io.to(targetRoomId).emit('draft_shared', result);
      } catch (err) {
        socket.emit('error_event', { message: err.message });
      }
    });

    // Client joins a room dynamically
    socket.on('join_room', async ({ roomId, userId, username }) => {
      try {
        if (!roomId || !userId) {
          return socket.emit('error_event', { message: 'roomId and userId are required to join room' });
        }
        if (socket.roomId && socket.roomId !== roomId) {
          socket.leave(socket.roomId);
        }
        socket.join(roomId);
        socket.roomId = roomId;
        socket.userId = userId;

        const joinResult = await roomService.joinRoom({
          roomId,
          userId,
          username: username || `User_${userId.slice(0, 6)}`,
          socketId: socket.id,
        });

        const memberUsername = username || (joinResult.joinedMember && joinResult.joinedMember.username) || `User_${userId.slice(0, 6)}`;
        const memberRole = (joinResult.joinedMember && joinResult.joinedMember.role) || 'MEMBER';

        socket.to(roomId).emit('user_joined', {
          userId,
          username: memberUsername,
          role: memberRole,
          connectionStatus: 'CONNECTED',
        });

        const roomState = await roomService.getRoomDetails(roomId);
        io.to(roomId).emit('room_state', roomState);

        const activeSpinSnapshot = await spinEngine.getCurrentSpinSnapshot(roomId);
        if (activeSpinSnapshot && activeSpinSnapshot.status === 'RUNNING') {
          socket.emit('spin_started', activeSpinSnapshot);
        }
      } catch (err) {
        logger.error({ err: err.message, roomId, userId }, 'Error joining room dynamically');
        socket.emit('error_event', { message: err.message });
      }
    });

    // Client leaves a room explicitly
    socket.on('leave_room', async (data = {}) => {
      try {
        const targetRoomId = data.roomId || socket.roomId;
        const targetUserId = data.userId || socket.userId;
        if (targetRoomId && targetUserId) {
          await roomService.leaveRoom({ roomId: targetRoomId, userId: targetUserId });
          socket.leave(targetRoomId);
          io.to(targetRoomId).emit('user_left', {
            userId: targetUserId,
            reason: 'leave',
          });
          const remainingState = await roomService.getRoomDetails(targetRoomId).catch(() => null);
          if (remainingState) {
            io.to(targetRoomId).emit('room_state', remainingState);
          }
          if (socket.roomId === targetRoomId) {
            socket.roomId = null;
          }
        }
      } catch (err) {
        socket.emit('error_event', { message: err.message });
      }
    });

    // Handle Client Disconnect
    socket.on('disconnect', async (reason) => {
      logger.info({ socketId: socket.id, userId: socket.userId, roomId: socket.roomId, reason }, 'Client disconnected');

      if (socket.roomId && socket.userId) {
        try {
          // Fully remove the member from the room on disconnect (cleans up empty rooms)
          const targetRoomId = socket.roomId;
          const targetUserId = socket.userId;

          // Try a proper leave (handles ownership transfer, etc.)
          await roomService.leaveRoom({ roomId: targetRoomId, userId: targetUserId }).catch(async () => {
            // Fallback: just delete the member record directly
            await RoomMember.deleteOne({ roomId: targetRoomId, userId: targetUserId });
          });

          // Broadcast user_left
          io.to(targetRoomId).emit('user_left', {
            userId: targetUserId,
            username: socket.username,
            reason: reason || 'disconnect',
          });

          const updatedState = await roomService.getRoomDetails(targetRoomId).catch(() => null);
          if (updatedState) {
            io.to(targetRoomId).emit('room_state', updatedState);
          }

          // Check if any members remain in room
          const remainingCount = await RoomMember.countDocuments({ roomId: targetRoomId });
          const room = await Room.findOne({ roomId: targetRoomId });

          // Edge Case 7: If all participants leave while spin is RUNNING -> abort spin
          if (remainingCount === 0 && room && room.status === 'IN_SPIN') {
            await spinEngine.abortSpin(room.activeSpinId, targetRoomId, 'ALL_PARTICIPANTS_LEFT');
          }
        } catch (err) {
          logger.error({ err: err.message }, 'Error handling socket disconnect');
        }
      }
    });
  });
};

module.exports = setupSocketHandlers;
