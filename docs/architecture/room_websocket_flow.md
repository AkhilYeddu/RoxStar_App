# Real-Time Room & WebSocket Event Flow

## Event Specifications (Section B2)
| Event Name | Direction | Payload Description | Trigger Condition |
|---|---|---|---|
| `user_joined` | Server -> Room | `{ userId, username, role, connectionStatus }` | Member connects/joins room |
| `user_left` | Server -> Room | `{ userId, reason }` | Member disconnects or leaves |
| `draft_shared` | Server -> Room | `{ draft: { id, title, durationMs, effectApplied, fileUrl }, sharedBy }` | Member shares a voice draft |
| `spin_started` | Server -> Room | `{ spinId, roomId, status, round, activeParticipants, nextEliminationCountdownSeconds }` | Admin/Owner starts spin |
| `user_eliminated` | Server -> Room | `{ spinId, round, eliminatedUserId, remainingUsers, nextEliminationCountdownSeconds }` | Authoritative 5s interval knocks out player |
| `winner_announced` | Server -> Room | `{ spinId, winnerId, winnerUsername, virtualPointsAwarded, spinState }` | Last player standing wins |
| `room_state` | Server -> Client | Full snapshot of room metadata, participants, active drafts, and active spin | On connect, reconnect, or sync request |

```mermaid
sequenceDiagram
    autonumber
    actor ClientA as Admin / Owner (Android)
    actor ClientB as Member B (Android)
    actor ClientC as Member C (Android)
    participant Server as Node.js Real-Time Engine
    participant DB as MongoDB Atlas

    Note over ClientA,Server: 1. Room Creation & Connections
    ClientA->>Server: POST /api/rooms { name: "Vocal Hub", ownerId: "UserA" }
    Server->>DB: Create Room & Member records
    Server-->>ClientA: 201 Created (roomId: "room_123")

    ClientA->>Server: WS Connect (?roomId=room_123&userId=UserA)
    Server-->>ClientA: emit("room_state", fullState)

    ClientB->>Server: WS Connect (?roomId=room_123&userId=UserB)
    Server-->>ClientB: emit("room_state", fullState)
    Server--)ClientA: broadcast("user_joined", { userId: "UserB" })

    ClientC->>Server: WS Connect (?roomId=room_123&userId=UserC)
    Server-->>ClientC: emit("room_state", fullState)
    Server--)ClientA: broadcast("user_joined", { userId: "UserC" })
    Server--)ClientB: broadcast("user_joined", { userId: "UserC" })

    Note over ClientB,Server: 2. Voice Draft Sharing
    ClientB->>Server: WS emit("share_draft", { draftId: "draft_01", effect: "ECHO" })
    Server->>DB: Upsert Draft & push to sharedInRooms
    Server--)ClientA: broadcast("draft_shared", { draft, sharedBy: "UserB" })
    Server--)ClientB: broadcast("draft_shared", { draft, sharedBy: "UserB" })
    Server--)ClientC: broadcast("draft_shared", { draft, sharedBy: "UserB" })

    Note over ClientA,Server: 3. Spin Kickoff & 5s Elimination Cadence
    ClientA->>Server: POST /api/rooms/room_123/spin/start
    Server->>DB: Validate >= 3 players, WAITING -> RUNNING
    Server--)ClientA: broadcast("spin_started", { spinId, active: [A, B, C] })
    Server--)ClientB: broadcast("spin_started", { spinId, active: [A, B, C] })
    Server--)ClientC: broadcast("spin_started", { spinId, active: [A, B, C] })

    Note over Server: Authoritative 5000ms Server Timer
    Server->>DB: Eliminate UserC (Round 1)
    Server--)ClientA: broadcast("user_eliminated", { eliminated: "UserC", remaining: [A, B] })
    Server--)ClientB: broadcast("user_eliminated", { eliminated: "UserC", remaining: [A, B] })
    Server--)ClientC: broadcast("user_eliminated", { eliminated: "UserC", remaining: [A, B] })

    Note over Server: Authoritative 5000ms Server Timer
    Server->>DB: Eliminate UserB (Round 2)
    Server--)ClientA: broadcast("user_eliminated", { eliminated: "UserB", remaining: [A] })
    Server--)ClientB: broadcast("user_eliminated", { eliminated: "UserB", remaining: [A] })
    Server--)ClientC: broadcast("user_eliminated", { eliminated: "UserB", remaining: [A] })

    Note over Server: Sole Survivor Detected -> Declare Winner
    Server->>DB: Award +100 virtual points, status -> COMPLETED
    Server--)ClientA: broadcast("winner_announced", { winnerId: "UserA" })
    Server--)ClientB: broadcast("winner_announced", { winnerId: "UserA" })
    Server--)ClientC: broadcast("winner_announced", { winnerId: "UserA" })
```
