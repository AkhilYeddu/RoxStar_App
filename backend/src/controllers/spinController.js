const spinEngine = require('../services/spinEngine');
const Spin = require('../models/Spin');
const SpinEvent = require('../models/SpinEvent');
const SpinParticipant = require('../models/SpinParticipant');

class SpinController {
  async startSpin(req, res, next) {
    try {
      const { roomId } = req.params;
      const { userId } = req.body;
      const idempotencyKey = req.headers['idempotency-key'] || null;

      const result = await spinEngine.startSpin({
        roomId,
        initiatedBy: userId,
        idempotencyKey,
      });

      res.status(200).json({
        success: true,
        data: result,
        message: 'Spin started successfully',
      });
    } catch (error) {
      next(error);
    }
  }

  async getSpinState(req, res, next) {
    try {
      const { spinId } = req.params;
      const spin = await Spin.findOne({ spinId });
      if (!spin) {
        return res.status(404).json({
          success: false,
          error: 'NotFoundError',
          message: `Spin '${spinId}' not found`,
        });
      }

      const [activeParticipants, eliminatedParticipants] = await Promise.all([
        SpinParticipant.find({ spinId, status: 'ELIGIBLE' }),
        SpinParticipant.find({ spinId, status: { $ne: 'ELIGIBLE' } }).sort({ eliminationOrder: 1 }),
      ]);

      res.status(200).json({
        success: true,
        data: {
          spinId: spin.spinId,
          roomId: spin.roomId,
          status: spin.status,
          currentRound: spin.currentRound,
          totalInitialParticipants: spin.totalInitialParticipants,
          activeParticipants: activeParticipants.map((p) => ({
            userId: p.userId,
            username: p.username,
          })),
          eliminatedParticipants: eliminatedParticipants.map((p) => ({
            userId: p.userId,
            username: p.username,
            order: p.eliminationOrder,
            round: p.eliminatedRound,
            status: p.status,
          })),
          winnerId: spin.winnerId,
          startedAt: spin.startedAt,
          completedAt: spin.completedAt,
        },
      });
    } catch (error) {
      next(error);
    }
  }

  async getSpinEvents(req, res, next) {
    try {
      const { spinId } = req.params;
      const events = await SpinEvent.find({ spinId }).sort({ sequenceNumber: 1 });
      res.status(200).json({
        success: true,
        data: events,
        count: events.length,
      });
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new SpinController();
