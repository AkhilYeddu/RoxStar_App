const http = require('http');
const { setupTestDB } = require('../setup');
const app = require('../../backend/src/app');

describe('REST API Integration Tests', () => {
  setupTestDB();

  let server;
  let baseUrl;

  beforeAll((done) => {
    server = http.createServer(app);
    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      baseUrl = `http://127.0.0.1:${port}`;
      done();
    });
  });

  afterAll((done) => {
    server.close(done);
  });

  it('GET /api/health - should return 200 UP', async () => {
    const res = await fetch(`${baseUrl}/api/health`);
    const body = await res.json();
    expect(res.status).toBe(200);
    expect(body.status).toBe('UP');
    expect(body.database.status).toBe('CONNECTED');
  });

  it('GET /api/ready - should return 200 Ready', async () => {
    const res = await fetch(`${baseUrl}/api/ready`);
    const body = await res.json();
    expect(res.status).toBe(200);
    expect(body.ready).toBe(true);
  });

  it('POST /api/rooms - should create a room', async () => {
    const res = await fetch(`${baseUrl}/api/rooms`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: 'Vocal Masterclass',
        ownerId: 'user_dev_1',
        maxParticipants: 15,
      }),
    });
    const body = await res.json();

    expect(res.status).toBe(201);
    expect(body.success).toBe(true);
    expect(body.data.name).toBe('Vocal Masterclass');
    expect(body.data.id).toBeDefined();
  });

  it('POST /api/rooms - should reject empty name with 400 Bad Request', async () => {
    const res = await fetch(`${baseUrl}/api/rooms`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: '',
        ownerId: 'user_dev_1',
      }),
    });
    const body = await res.json();

    expect(res.status).toBe(400);
    expect(body.success).toBe(false);
    expect(body.error).toBe('ValidationError');
  });

  it('Full Room Lifecycle: Create -> Join -> Share Draft -> Leave', async () => {
    // 1. Create Room
    const createRes = await fetch(`${baseUrl}/api/rooms`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: 'Live Jam Hub',
        ownerId: 'user_owner',
      }),
    });
    const createBody = await createRes.json();
    const roomId = createBody.data.id;

    // 2. Join Room
    const joinRes = await fetch(`${baseUrl}/api/rooms/${roomId}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: 'user_guest',
        username: 'Guest_Singer',
      }),
    });
    const joinBody = await joinRes.json();
    expect(joinRes.status).toBe(200);
    expect(joinBody.data.participants).toHaveLength(2);

    // 3. Share Draft
    const shareRes = await fetch(`${baseUrl}/api/rooms/${roomId}/drafts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: 'user_owner',
        draftId: 'draft_voc_1',
        title: 'Vocal Run in D Minor',
        durationMs: 4500,
        effectApplied: 'REVERB',
      }),
    });
    const shareBody = await shareRes.json();
    expect(shareRes.status).toBe(200);
    expect(shareBody.data.draft.id).toBe('draft_voc_1');

    // 4. Get Room State
    const getRes = await fetch(`${baseUrl}/api/rooms/${roomId}`);
    const getBody = await getRes.json();
    expect(getRes.status).toBe(200);
    expect(getBody.data.sharedDrafts).toHaveLength(1);

    // 5. Leave Room
    const leaveRes = await fetch(`${baseUrl}/api/rooms/${roomId}/leave`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: 'user_guest',
      }),
    });
    expect(leaveRes.status).toBe(200);
  });

  it('Idempotency Key - duplicate requests return identical cached response', async () => {
    const key = 'idem_unique_test_12345';

    const res1 = await fetch(`${baseUrl}/api/rooms`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': key,
      },
      body: JSON.stringify({
        name: 'Idempotent Room',
        ownerId: 'user_idem',
      }),
    });
    const body1 = await res1.json();
    expect(res1.status).toBe(201);
    const roomId = body1.data.id;

    // Duplicate request with identical key
    const res2 = await fetch(`${baseUrl}/api/rooms`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': key,
      },
      body: JSON.stringify({
        name: 'Idempotent Room',
        ownerId: 'user_idem',
      }),
    });
    const body2 = await res2.json();
    expect(res2.status).toBe(201);
    expect(body2.data.id).toBe(roomId);
  });
});
