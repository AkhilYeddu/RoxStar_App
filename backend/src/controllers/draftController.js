const Draft = require('../models/Draft');
const { v4: uuidv4 } = require('uuid');

class DraftController {
  async createDraft(req, res, next) {
    try {
      const { userId, title, durationMs, effectApplied, fileUrl } = req.body;
      const draftId = `draft_${uuidv4().replace(/-/g, '').slice(0, 10)}`;

      const draft = await Draft.create({
        draftId,
        userId,
        title: title || 'Voice Draft',
        durationMs: durationMs || 0,
        effectApplied: effectApplied || 'NONE',
        fileUrl: fileUrl || '',
      });

      res.status(201).json({
        success: true,
        data: draft,
        message: 'Draft saved successfully',
      });
    } catch (error) {
      next(error);
    }
  }

  async getUserDrafts(req, res, next) {
    try {
      const { userId } = req.params;
      const drafts = await Draft.find({ userId }).sort({ createdAt: -1 });
      res.status(200).json({
        success: true,
        data: drafts,
        count: drafts.length,
      });
    } catch (error) {
      next(error);
    }
  }

  async renameDraft(req, res, next) {
    try {
      const { draftId } = req.params;
      const { title } = req.body;
      if (!title || !title.trim()) {
        return res.status(400).json({ success: false, message: 'Title is required' });
      }
      const draft = await Draft.findOneAndUpdate(
        { draftId },
        { title: title.trim() },
        { new: true }
      );
      if (!draft) {
        return res.status(404).json({ success: false, message: `Draft '${draftId}' not found` });
      }
      res.status(200).json({ success: true, data: draft, message: 'Draft renamed successfully' });
    } catch (error) {
      next(error);
    }
  }

  async deleteDraft(req, res, next) {
    try {
      const { draftId } = req.params;
      const result = await Draft.findOneAndDelete({ draftId });
      if (!result) {
        return res.status(404).json({
          success: false,
          error: 'NotFoundError',
          message: `Draft '${draftId}' not found`,
        });
      }
      res.status(200).json({
        success: true,
        message: 'Draft deleted successfully',
      });
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new DraftController();
