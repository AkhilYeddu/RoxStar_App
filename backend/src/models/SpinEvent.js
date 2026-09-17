const mongoose = require('mongoose');

const spinEventSchema = new mongoose.Schema(
  {
    eventId: {
      type: String,
      required: true,
      unique: true,
      index: true,
    },
    spinId: {
      type: String,
      required: true,
      index: true,
    },
    roomId: {
      type: String,
      required: true,
      index: true,
    },
    eventType: {
      type: String,
      enum: [
        'spin_started',
        'user_eliminated',
        'winner_announced',
        'spin_aborted',
        'user_disconnected_during_spin',
      ],
      required: true,
    },
    sequenceNumber: {
      type: Number,
      required: true,
    },
    payload: {
      type: mongoose.Schema.Types.Mixed,
      required: true,
    },
    timestamp: {
      type: Date,
      default: Date.now,
    },
  },
  {
    timestamps: true,
  }
);

spinEventSchema.index({ spinId: 1, sequenceNumber: 1 }, { unique: true });

module.exports = mongoose.model('SpinEvent', spinEventSchema);
