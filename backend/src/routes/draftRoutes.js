const express = require('express');
const router = express.Router();
const { z } = require('zod');
const draftController = require('../controllers/draftController');
const validate = require('../middlewares/validate');

const createDraftSchema = z.object({
  body: z.object({
    userId: z.string().min(1, 'userId is required'),
    title: z.string().min(1, 'title is required'),
    durationMs: z.number().nonnegative(),
    effectApplied: z.enum(['NONE', 'ECHO', 'REVERB', 'PITCH_SHIFT']).optional(),
    fileUrl: z.string().optional(),
  }),
});

router.post('/', validate(createDraftSchema), draftController.createDraft);
router.get('/user/:userId', draftController.getUserDrafts);
router.patch('/:draftId/rename', draftController.renameDraft);
router.delete('/:draftId', draftController.deleteDraft);

module.exports = router;
