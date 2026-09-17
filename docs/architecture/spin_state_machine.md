# Spin Wheel Lifecycle & State Machine Architecture

## 1. State Machine Specification
As defined in **Section C1** of the RoxStar Technical Assessment:

```mermaid
stateDiagram-v2
    [*] --> WAITING: Room Owner triggers POST /api/rooms/:roomId/spin/start
    
    state WAITING {
        [*] --> CheckEligibility
        CheckEligibility --> ValidateMinMax: Check min 3 & max 20 connected players
        ValidateMinMax --> RejectInsufficient: < 3 Players (400 Bad Request)
        ValidateMinMax --> SeedParticipants: >= 3 Players
        SeedParticipants --> [*]
    }

    WAITING --> RUNNING: Spin record created & initial 'spin_started' emitted
    RejectInsufficient --> [*]: Abort start without state change

    state RUNNING {
        [*] --> RoundTimer: Authoritative 5000ms server interval
        RoundTimer --> ProcessElimination: Pick random active player (or disconnected player)
        ProcessElimination --> EmitEliminated: Broadcast 'user_eliminated' event
        EmitEliminated --> SurvivorCheck: Count remaining ELIGIBLE players
        
        SurvivorCheck --> NextRound: > 1 survivors remain
        NextRound --> RoundTimer: Increment round & schedule next 5s timer
        
        SurvivorCheck --> SingleWinner: Exactly 1 survivor remains
        SingleWinner --> AwardVirtualPoints: Credit +100 virtual points to User in DB
    }

    RUNNING --> COMPLETED: Sole survivor confirmed, emit 'winner_announced'
    RUNNING --> ABORTED: Abnormal conditions (e.g. ALL active players disconnect/leave)

    COMPLETED --> [*]: Room status reset to IDLE, activeSpinId cleared
    ABORTED --> [*]: Room status reset to IDLE, emit 'spin_aborted'
```

---

## 2. Invariants & Rules
1. **Single Active Spin Invariant**: A room can have at most ONE spin with status `RUNNING` or `WAITING` at any moment. Any concurrent start request is rejected with `409 Conflict`.
2. **Deterministic Cadence**: Eliminations occur on an authoritative 5-second interval powered by the Node.js event loop with elapsed time checks.
3. **Survivor Monotonicity**: Active survivor count strictly decreases by 1 in each 5s round until exactly 1 winner is left.
4. **Idempotency & Reconnection**: Every elimination event is recorded in the `SpinEvent` collection with an atomic sequential index (`sequenceNumber`), guaranteeing reconnecting clients can rebuild the complete audit trail.

---

## 3. Handled Edge Cases (Section C4)

| # | Edge Case | Handled Behavior | Resulting State |
|---|---|---|---|
| **1** | Insufficient Players (< 3) | Rejects start request with HTTP 400 and descriptive error message. | `IDLE` (no spin created) |
| **2** | Duplicate Start Requests | Idempotency guard / active spin check rejects with HTTP 409 Conflict. | Ongoing spin uninterrupted |
| **3** | Mid-Spin Late Joins | Late joiner is admitted to room with `SPECTATOR` status and excluded from elimination/winner pool. | Running spin unaffected |
| **4** | Mid-Spin User Disconnect | Disconnected participant is prioritized for elimination or flagged as inactive; game continues for remaining connected players. | `RUNNING` |
| **5** | Mid-Spin Reconnection | Connecting client immediately receives full `spin_state` snapshot containing current round, survivors, and countdown timer. | Synchronized client state |
| **6** | Admin / Owner Disconnect | Spin continues running autonomously on server interval; ownership can transfer without interrupting spin. | `RUNNING` -> `COMPLETED` |
| **7** | All Active Players Leave | If active survivors count drops to 0 mid-spin, spin immediately terminates with reason `ALL_PARTICIPANTS_LEFT`. | `ABORTED` |
| **8** | Duplicate Network Packets | Idempotency-Key header returns identical cached JSON response without duplicate execution. | Idempotent response |
