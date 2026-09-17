const mongoose = require('mongoose');

const roomSchema = new mongoose.Schema(
  {
    roomId: {
      type: String,
      required: true,
      unique: true,
      index: true,
    },
    name: {
      type: String,
      required: true,
      trim: true,
      maxlength: 100,
    },
    ownerId: {
      type: String,
      required: true,
      index: true,
    },
    status: {
      type: String,
      enum: ['IDLE', 'IN_SPIN', 'CLOSED'],
      default: 'IDLE',
      index: true,
    },
    maxParticipants: {
      type: Number,
      default: 20,
      min: 3,
      max: 50,
    },
    activeSpinId: {
      type: String,
      default: null,
    },
  },
  {
    timestamps: true,
  }
);

roomSchema.index({ status: 1, createdAt: -1 });

module.exports = mongoose.model('Room', roomSchema);
