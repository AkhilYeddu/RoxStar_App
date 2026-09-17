const mongoose = require('mongoose');

class HealthController {
  async getHealth(req, res) {
    const dbState = mongoose.connection.readyState;
    const isDbConnected = dbState === 1;

    res.status(200).json({
      status: 'UP',
      timestamp: new Date().toISOString(),
      uptime: process.uptime(),
      database: {
        status: isDbConnected ? 'CONNECTED' : 'DISCONNECTED',
        readyState: dbState,
      },
      system: {
        memoryUsage: process.memoryUsage(),
        nodeVersion: process.version,
      },
    });
  }

  async getReadiness(req, res) {
    const dbState = mongoose.connection.readyState;
    if (dbState === 1) {
      return res.status(200).json({
        ready: true,
        message: 'Service is ready to accept traffic',
      });
    }

    return res.status(503).json({
      ready: false,
      message: 'Service is not ready (database disconnected)',
    });
  }
}

module.exports = new HealthController();
