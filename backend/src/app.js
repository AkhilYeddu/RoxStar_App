const express = require('express');
const cors = require('cors');
const errorHandler = require('./middlewares/errorHandler');
const roomRoutes = require('./routes/roomRoutes');
const spinRoutes = require('./routes/spinRoutes');
const draftRoutes = require('./routes/draftRoutes');
const healthRoutes = require('./routes/healthRoutes');

const app = express();

// Global Middlewares
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Request trace logger
app.use((req, res, next) => {
  req.requestId = req.headers['x-request-id'] || `req_${Date.now()}`;
  res.setHeader('X-Request-Id', req.requestId);
  next();
});

// API Routes
app.use('/api', healthRoutes);
app.use('/api/rooms', roomRoutes);
app.use('/api/rooms', spinRoutes);
app.use('/api', spinRoutes); // Direct access for /api/spins/:spinId
app.use('/api/drafts', draftRoutes);

// Root Welcome endpoint
app.get('/', (req, res) => {
  res.status(200).json({
    name: 'RoxStar Unified Real-Time Service',
    status: 'ACTIVE',
    version: '1.0.0',
    docs: '/api-docs',
    endpoints: {
      health: '/api/health',
      ready: '/api/ready',
      rooms: '/api/rooms',
      drafts: '/api/drafts',
    },
  });
});

// 404 Route Handler
app.use((req, res, next) => {
  res.status(404).json({
    success: false,
    error: 'NotFound',
    message: `Endpoint ${req.method} ${req.originalUrl} not found`,
  });
});

// Centralized Error Handler
app.use(errorHandler);

module.exports = app;
