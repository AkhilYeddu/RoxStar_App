const logger = require('../utils/logger');

// Centralized application error handling middleware
const errorHandler = (err, req, res, next) => {
  const status = err.statusCode || err.status || 500;
  const message = err.message || 'Internal Server Error';

  logger.error({
    err: {
      message: err.message,
      stack: err.stack,
      status,
      path: req.originalUrl,
      method: req.method,
    },
  }, 'Request error caught by global handler');

  res.status(status).json({
    success: false,
    error: err.name || 'ApplicationError',
    message,
    statusCode: status,
    timestamp: new Date().toISOString(),
    path: req.originalUrl,
  });
};

module.exports = errorHandler;
