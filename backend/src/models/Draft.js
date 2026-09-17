const mongoose = require('mongoose');

const draftSchema = new mongoose.Schema(
  {
    draftId: {
      type: String,
      required: true,
      unique: true,
      index: true,
    },
    userId: {
      type: String,
      required: true,
      index: true,
    },
    title: {
      type: String,
      required: true,
      trim: true,
    },
    durationMs: {
      type: Number,
      required: true,
      min: 0,
    },
    fileUrl: {
      type: String,
      default: '',
    },
    effectApplied: {
      type: String,
      enum: ['NONE', 'ECHO', 'REVERB', 'PITCH_SHIFT'],
      default: 'NONE',
    },
    sharedInRooms: [
      {
        type: String,
        index: true,
      },
    ],
  },
  {
    timestamps: true,
  }
);

draftSchema.index({ userId: 1, createdAt: -1 });

module.exports = mongoose.model('Draft', draftSchema);
