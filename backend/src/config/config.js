require('dotenv').config();

module.exports = {
  port: parseInt(process.env.PORT, 10) || 4000,
  nodeEnv: process.env.NODE_ENV || 'development',
  mongoUri: process.env.MONGO_URI || 'mongodb://localhost:27017/roxstar_db',
  corsOrigin: process.env.CORS_ORIGIN || '*',
  spinRules: {
    minParticipants: 3,
    maxParticipants: 20,
    eliminationIntervalMs: parseInt(process.env.SPIN_INTERVAL_MS, 10) || 5000, // 5 seconds per assessment rules
  }
};
