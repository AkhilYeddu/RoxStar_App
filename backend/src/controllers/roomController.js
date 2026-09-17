const roomService = require('../services/roomService');
const spinEngine = require('../services/spinEngine');

class RoomController {
  async createRoom(req, res, next) {
    try {
      const { name, ownerId, maxParticipants } = req.body;
      const room = await roomService.createRoom({ name, ownerId, maxParticipants });
      res.status(201).json({
        success: true,
        data: room,
        message: 'Room created successfully',
      });
    } catch (error) {
      next(error);
    }
  }

  async joinRoom(req, res, next) {
    try {
      const { roomId } = req.params;
      const { userId, username } = req.body;
      const result = await roomService.joinRoom({ roomId, userId, username });

      // If spin is running, notify caller of spectator status
      const activeSpinSnapshot = await spinEngine.getCurrentSpinSnapshot(roomId);

      res.status(200).json({
        success: true,
        data: result.room,
        activeSpin: activeSpinSnapshot,
        message: activeSpinSnapshot && activeSpinSnapshot.status === 'RUNNING'
          ? 'Joined room as spectator (spin currently running)'
          : 'Joined room successfully',
      });
    } catch (error) {
      next(error);
    }
  }

  async leaveRoom(req, res, next) {
    try {
      const { roomId } = req.params;
      const { userId } = req.body;
      const result = await roomService.leaveRoom({ roomId, userId });
      res.status(200).json({
        success: true,
        data: result,
        message: 'Left room successfully',
      });
    } catch (error) {
      next(error);
    }
  }

  async getRoomState(req, res, next) {
    try {
      const { roomId } = req.params;
      const room = await roomService.getRoomDetails(roomId);
      const activeSpin = await spinEngine.getCurrentSpinSnapshot(roomId);
      res.status(200).json({
        success: true,
        data: {
          ...room,
          activeSpin,
        },
      });
    } catch (error) {
      next(error);
    }
  }

  async shareDraft(req, res, next) {
    try {
      const { roomId } = req.params;
      const { userId, draftId, title, durationMs, effectApplied, fileUrl } = req.body;
      const result = await roomService.shareDraft({
        roomId,
        userId,
        draftId,
        title,
        durationMs,
        effectApplied,
        fileUrl,
      });

      // Also trigger socket broadcast
      spinEngine.broadcastToRoom(roomId, 'draft_shared', result);

      res.status(200).json({
        success: true,
        data: result,
        message: 'Draft shared with room members',
      });
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new RoomController();
