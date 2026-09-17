const express = require('express');
const router = express.Router();
const { z } = require('zod');
const roomController = require('../controllers/roomController');
const validate = require('../middlewares/validate');
const idempotency = require('../middlewares/idempotency');

const createRoomSchema = z.object({
  body: z.object({
    name: z.string().min(1, 'Room name is required').max(100),
    ownerId: z.string().min(1, 'ownerId is required'),
    maxParticipants: z.number().int().min(3).max(50).optional(),
  }),
});

const joinRoomSchema = z.object({
  params: z.object({
    roomId: z.string().min(1),
  }),
  body: z.object({
    userId: z.string().min(1, 'userId is required'),
    username: z.string().optional(),
  }),
});

const leaveRoomSchema = z.object({
  params: z.object({
    roomId: z.string().min(1),
  }),
  body: z.object({
    userId: z.string().min(1, 'userId is required'),
  }),
});

const shareDraftSchema = z.object({
  params: z.object({
    roomId: z.string().min(1),
  }),
  body: z.object({
    userId: z.string().min(1),
    draftId: z.string().min(1),
    title: z.string().optional(),
    durationMs: z.number().nonnegative().optional(),
    effectApplied: z.enum(['NONE', 'ECHO', 'REVERB', 'PITCH_SHIFT']).optional(),
    fileUrl: z.string().optional(),
  }),
});

router.get('/', roomController.listRooms);
router.post('/', idempotency, validate(createRoomSchema), roomController.createRoom);
router.post('/:roomId/join', validate(joinRoomSchema), roomController.joinRoom);
router.post('/:roomId/leave', validate(leaveRoomSchema), roomController.leaveRoom);
router.get('/:roomId', roomController.getRoomState);
router.post('/:roomId/drafts', validate(shareDraftSchema), roomController.shareDraft);

module.exports = router;
