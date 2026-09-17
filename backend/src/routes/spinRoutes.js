const express = require('express');
const router = express.Router();
const { z } = require('zod');
const spinController = require('../controllers/spinController');
const validate = require('../middlewares/validate');
const idempotency = require('../middlewares/idempotency');

const startSpinSchema = z.object({
  params: z.object({
    roomId: z.string().min(1),
  }),
  body: z.object({
    userId: z.string().min(1, 'userId is required'),
  }),
});

router.post('/:roomId/spin/start', idempotency, validate(startSpinSchema), spinController.startSpin);
router.get('/spins/:spinId', spinController.getSpinState);
router.get('/spins/:spinId/events', spinController.getSpinEvents);

module.exports = router;
