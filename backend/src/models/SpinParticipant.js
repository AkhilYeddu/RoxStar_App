const mongoose = require('mongoose');

const spinParticipantSchema = new mongoose.Schema(
  {
    spinId: {
      type: String,
      required: true,
      index: true,
    },
    roomId: {
      type: String,
      required: true,
    },
    userId: {
      type: String,
      required: true,
      index: true,
    },
    username: {
      type: String,
      required: true,
    },
    status: {
      type: String,
      enum: ['ELIGIBLE', 'ELIMINATED', 'WINNER', 'SPECTATOR', 'DISCONNECTED_ELIMINATED'],
      default: 'ELIGIBLE',
      index: true,
    },
    eliminationOrder: {
      type: Number,
      default: null,
    },
    eliminatedRound: {
      type: Number,
      default: null,
    },
    eliminatedAt: {
      type: Date,
      default: null,
    },
  },
  {
    timestamps: true,
  }
);

spinParticipantSchema.index({ spinId: 1, userId: 1 }, { unique: true });
spinParticipantSchema.index({ spinId: 1, status: 1 });

module.exports = mongoose.model('SpinParticipant', spinParticipantSchema);
