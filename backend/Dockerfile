# ==========================================
# Stage 1: Build & Dependencies
# ==========================================
FROM node:24-alpine AS dependencies

WORKDIR /app

# Install build dependencies if needed
COPY package*.json ./
RUN npm ci --only=production && npm cache clean --force

# ==========================================
# Stage 2: Production Runner
# ==========================================
FROM node:24-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=4000

# Create non-root system user for security
RUN addgroup -S roxstar && adduser -S roxstar -G roxstar

# Copy production node_modules from builder
COPY --from=dependencies /app/node_modules ./node_modules
COPY package*.json ./
COPY src/ ./src/
COPY database/ ./database/

# Assign ownership to non-root user
RUN chown -R roxstar:roxstar /app

USER roxstar

EXPOSE 4000

# Health check configuration using the /api/health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:4000/api/health || exit 1

CMD ["node", "src/server.js"]
