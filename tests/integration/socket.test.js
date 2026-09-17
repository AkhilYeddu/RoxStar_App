const http = require('http');
const { Server } = require('socket.io');
const ioClient = require('socket.io-client');
const { setupTestDB } = require('../setup');
const app = require('../../backend/src/app');
const setupSocketHandlers = require('../../backend/src/sockets/socketHandler');
const roomService = require('../../backend/src/services/roomService');

describe('Socket.IO Real-Time Integration Tests', () => {
  setupTestDB();

  let httpServer;
  let ioServer;
  let serverAddress;

  beforeAll((done) => {
    httpServer = http.createServer(app);
    ioServer = new Server(httpServer, {
      cors: { origin: '*' },
    });
    setupSocketHandlers(ioServer);
    httpServer.listen(0, () => {
      const port = httpServer.address().port;
      serverAddress = `http://localhost:${port}`;
      done();
    });
  });

  afterAll(async () => {
    ioServer.close();
    await new Promise((resolve) => httpServer.close(resolve));
    await new Promise((resolve) => setTimeout(resolve, 300));
  });

  it('should emit room_state and user_joined when client connects', (done) => {
    roomService.createRoom({ name: 'Socket Room', ownerId: 'user_owner' }).then((room) => {
      const client1 = ioClient(serverAddress, {
        query: { roomId: room.id, userId: 'user_owner', username: 'Owner' },
      });

      client1.on('room_state', (state) => {
        expect(state.id).toBe(room.id);
        expect(state.name).toBe('Socket Room');

        // Client 2 connects
        const client2 = ioClient(serverAddress, {
          query: { roomId: room.id, userId: 'user_joiner', username: 'Joiner' },
        });

        client1.on('user_joined', (member) => {
          expect(member.userId).toBe('user_joiner');
          expect(member.username).toBe('Joiner');

          client1.disconnect();
          client2.disconnect();
          done();
        });
      });
    });
  });

  it('should broadcast draft_shared event to room', (done) => {
    roomService.createRoom({ name: 'Draft Broadcast Room', ownerId: 'user_owner' }).then((room) => {
      const client1 = ioClient(serverAddress, {
        query: { roomId: room.id, userId: 'user_owner', username: 'Owner' },
      });

      const client2 = ioClient(serverAddress, {
        query: { roomId: room.id, userId: 'user_listener', username: 'Listener' },
      });

      client2.on('draft_shared', (payload) => {
        expect(payload.draft.title).toBe('Acoustic Intro');
        expect(payload.sharedBy).toBe('Owner');
        client1.disconnect();
        client2.disconnect();
        done();
      });

      // Allow connections to establish
      setTimeout(() => {
        client1.emit('share_draft', {
          roomId: room.id,
          draftId: 'draft_acous_1',
          title: 'Acoustic Intro',
          durationMs: 5000,
          effectApplied: 'ECHO',
        });
      }, 300);
    });
  });
});
