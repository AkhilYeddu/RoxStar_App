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

        // 4. Return authoritative room_state to connecting/reconnecting client
        const roomState = await roomService.getRoomDetails(roomId);
        socket.emit('room_state', roomState);

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
        });

        io.to(targetRoomId).emit('draft_shared', result);
      } catch (err) {
        socket.emit('error_event', { message: err.message });
      }
    });

    // Handle Client Disconnect
    socket.on('disconnect', async (reason) => {
      logger.info({ socketId: socket.id, userId: socket.userId, roomId: socket.roomId, reason }, 'Client disconnected');

      if (socket.roomId && socket.userId) {
        try {
          // Update DB presence to DISCONNECTED
          await roomService.setConnectionStatus(socket.id, 'DISCONNECTED');

          // Broadcast mandatory event: user_left
          io.to(socket.roomId).emit('user_left', {
            userId: socket.userId,
            reason: reason || 'disconnect',
          });

          // Check if any members remain connected in room
          const remainingConnected = await RoomMember.countDocuments({
            roomId: socket.roomId,
            connectionStatus: 'CONNECTED',
          });

          const room = await Room.findOne({ roomId: socket.roomId });

          // Edge Case 7: If all participants leave while spin is RUNNING -> abort spin
          if (remainingConnected === 0 && room && room.status === 'IN_SPIN') {
            await spinEngine.abortSpin(room.activeSpinId, socket.roomId, 'ALL_PARTICIPANTS_LEFT');
          }
        } catch (err) {
          logger.error({ err: err.message }, 'Error handling socket disconnect');
        }
      }
    });
  });
};

module.exports = setupSocketHandlers;
