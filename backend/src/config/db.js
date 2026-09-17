const mongoose = require('mongoose');
const logger = require('../utils/logger');
const config = require('./config');

let isConnected = false;

const connectDB = async (uri = config.mongoUri) => {
  if (isConnected) {
    logger.info('Using existing database connection');
    return mongoose.connection;
  }

  try {
    const conn = await mongoose.connect(uri, {
      serverSelectionTimeoutMS: 3000,
    });
    isConnected = true;
    logger.info(`MongoDB connected successfully: ${conn.connection.host}/${conn.connection.name}`);

    mongoose.connection.on('error', (err) => {
      logger.error({ err }, 'MongoDB connection error');
    });

    mongoose.connection.on('disconnected', () => {
      logger.warn('MongoDB disconnected');
      isConnected = false;
    });

    return conn;
  } catch (error) {
    logger.warn(`Could not connect to external MongoDB at ${uri}. Falling back to embedded MongoMemoryServer for development...`);
    try {
      const { MongoMemoryServer } = require('mongodb-memory-server');
      const mongod = await MongoMemoryServer.create();
      const memUri = mongod.getUri();
      const conn = await mongoose.connect(memUri);
      isConnected = true;
      logger.info(`Embedded MongoDB running at ${memUri}`);
      return conn;
    } catch (memError) {
      logger.error({ error: memError.message }, 'Failed to start embedded MongoDB');
      throw memError;
    }
  }
};

const disconnectDB = async () => {
  if (isConnected) {
    await mongoose.disconnect();
    isConnected = false;
    logger.info('MongoDB disconnected cleanly');
  }
};

module.exports = { connectDB, disconnectDB };
