const idempotentCache = new Map();

// Idempotency middleware caching responses for non-GET endpoints with Idempotency-Key
const idempotency = (req, res, next) => {
  const key = req.headers['idempotency-key'];
  if (!key) {
    return next();
  }

  if (idempotentCache.has(key)) {
    const cached = idempotentCache.get(key);
    return res.status(cached.status).json(cached.body);
  }

  // Intercept response send to cache result
  const originalJson = res.json.bind(res);
  res.json = (body) => {
    if (res.statusCode >= 200 && res.statusCode < 300) {
      idempotentCache.set(key, { status: res.statusCode, body });
      // Clean cache entry after 10 minutes
      setTimeout(() => idempotentCache.delete(key), 10 * 60 * 1000);
    }
    return originalJson(body);
  };

  next();
};

module.exports = idempotency;
