const mongoose = require('mongoose');

const spinSchema = new mongoose.Schema(
  {
    spinId: {
      type: String,
      required: true,
      unique: true,
      index: true,
    },
    roomId: {
      type: String,
      required: true,
      index: true,
    },
    initiatedBy: {
      type: String,
      required: true,
    },
    status: {
      type: String,
      enum: ['WAITING', 'RUNNING', 'COMPLETED', 'ABORTED'],
      default: 'WAITING',
      index: true,
    },
    currentRound: {
      type: Number,
      default: 0,
    },
    totalInitialParticipants: {
      type: Number,
      required: true,
    },
    winnerId: {
      type: String,
      default: null,
      index: true,
    },
    startedAt: {
      type: Date,
      default: null,
    },
    completedAt: {
      type: Date,
      default: null,
    },
    abortedReason: {
      type: String,
      default: null,
    },
    idempotencyKey: {
      type: String,
      sparse: true,
      index: true,
    },
  },
  {
    timestamps: true,
  }
);

spinSchema.index({ roomId: 1, status: 1 });

module.exports = mongoose.model('Spin', spinSchema);
