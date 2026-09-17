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

    // Auto-seed default demo room and data if not present
    try {
      const Room = require('./models/Room');
      const User = require('./models/User');
      const RoomMember = require('./models/RoomMember');
      const Draft = require('./models/Draft');

      const existingRoom = await Room.findOne({ roomId: 'room_studio_alpha' });
      if (!existingRoom) {
        logger.info('Auto-seeding default demo room and demo data...');
        await User.create([
          { userId: 'user_alex', username: 'Alex_Vocalist', virtualPoints: 350 },
          { userId: 'user_sarah', username: 'Sarah_SoundEng', virtualPoints: 500 },
          { userId: 'user_mike', username: 'Mike_Producer', virtualPoints: 200 },
          { userId: 'user_elena', username: 'Elena_Singer', virtualPoints: 150 },
        ]);

        await Room.create({
          roomId: 'room_studio_alpha',
          name: 'RoxStar Audio Jam & Spin Arena',
          ownerId: 'user_alex',
          status: 'IDLE',
          maxParticipants: 20,
        });

        await RoomMember.create([
          { roomId: 'room_studio_alpha', userId: 'user_alex', username: 'Alex_Vocalist', role: 'OWNER', connectionStatus: 'CONNECTED' },
          { roomId: 'room_studio_alpha', userId: 'user_sarah', username: 'Sarah_SoundEng', role: 'MEMBER', connectionStatus: 'CONNECTED' },
          { roomId: 'room_studio_alpha', userId: 'user_mike', username: 'Mike_Producer', role: 'MEMBER', connectionStatus: 'CONNECTED' },
          { roomId: 'room_studio_alpha', userId: 'user_elena', username: 'Elena_Singer', role: 'MEMBER', connectionStatus: 'CONNECTED' },
        ]);

        await Draft.create([
          {
            draftId: 'draft_demo_01',
            userId: 'user_alex',
            title: 'Lead Vocal Harmony Hook',
            durationMs: 4200,
            effectApplied: 'ECHO',
            sharedInRooms: ['room_studio_alpha'],
          },
          {
            draftId: 'draft_demo_02',
            userId: 'user_sarah',
            title: 'Acoustic Ambience Reverb Clip',
            durationMs: 7800,
            effectApplied: 'REVERB',
            sharedInRooms: ['room_studio_alpha'],
          },
          {
            draftId: 'draft_demo_03',
            userId: 'user_mike',
            title: 'Raw Beatbox Draft',
            durationMs: 3500,
            effectApplied: 'NONE',
            sharedInRooms: ['room_studio_alpha'],
          }
        ]);
        logger.info('Default demo room, members, and drafts successfully seeded.');
      }
    } catch (seedErr) {
      logger.warn({ err: seedErr.message }, 'Skipped auto-seeding default data');
    }

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
