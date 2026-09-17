const http = require('http');
const { Server } = require('socket.io');
const app = require('./app');
const config = require('./config/config');
const { connectDB, disconnectDB } = require('./config/db');
const logger = require('./utils/logger');
const setupSocketHandlers = require('./sockets/socketHandler');

const server = http.createServer(app);

// Socket.IO configuration
const io = new Server(server, {
  cors: {
    origin: config.corsOrigin,
    methods: ['GET', 'POST'],
  },
  pingInterval: 10000,
  pingTimeout: 5000,
});

setupSocketHandlers(io);

const startServer = async () => {
  try {
    // Connect to MongoDB
    await connectDB();

    server.listen(config.port, () => {
      logger.info(`RoxStar Real-Time Service running on port ${config.port} [${config.nodeEnv}]`);
      logger.info(`Health check available at: http://localhost:${config.port}/api/health`);
    });
  } catch (error) {
    logger.error({ error: error.message }, 'Failed to start server');
    process.exit(1);
  }
};

// Graceful Shutdown
const shutdown = async (signal) => {
  logger.info(`Received ${signal}. Starting graceful shutdown...`);
  server.close(async () => {
    logger.info('HTTP & Socket.IO server closed');
    await disconnectDB();
    logger.info('Graceful shutdown complete. Exiting.');
    process.exit(0);
  });

  // Force close after 10s if hanging
  setTimeout(() => {
    logger.error('Could not close connections in time, forcefully shutting down');
    process.exit(1);
  }, 10000);
};

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

if (require.main === module) {
  startServer();
}

module.exports = { app, server, io };
