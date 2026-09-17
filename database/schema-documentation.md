# RoxStar Database Design & Justification

## Overview
The RoxStar database is implemented in **MongoDB** using **Mongoose ODM**. It handles users, room presence, voice draft metadata, authoritative spin wheel state machines, and real-time auditable event sequences.

---

## 1. Schema & Entities

### 1.1 `User`
- **Purpose**: Stores participant identity, profile metadata, and earned virtual points.
- **Fields**:
  - `userId` (`String`, Unique, Index): Unique external identifier for the user.
  - `username` (`String`): Display name.
  - `avatarUrl` (`String`): Profile photo or avatar image URI.
  - `virtualPoints` (`Number`): Balance of virtual points awarded upon winning spins.
  - `createdAt`, `updatedAt` (`Date`): Audit timestamps.

### 1.2 `Room`
- **Purpose**: Represents collaborative room lifecycle, ownership, status, and linked active spin.
- **Fields**:
  - `roomId` (`String`, Unique, Index): Human-readable room identifier (e.g. `room_7b9f3e1a`).
  - `name` (`String`): Descriptive room title.
  - `ownerId` (`String`, Index): `userId` of the administrator/owner who controls spin wheel triggers.
  - `status` (`String`, Index): Enum (`'IDLE' | 'IN_SPIN' | 'CLOSED'`).
  - `maxParticipants` (`Number`): Maximum allowed room members (default: 20, max: 50).
  - `activeSpinId` (`String`): Foreign pointer to in-flight `Spin`.

### 1.3 `RoomMember`
- **Purpose**: Tracks participant membership, role, and real-time presence connection status.
- **Fields**:
  - `roomId` (`String`, Index): Target room.
  - `userId` (`String`, Index): Target user.
  - `username` (`String`): Display name cached for high-speed room queries.
  - `role` (`String`): Enum (`'OWNER' | 'MEMBER'`).
  - `connectionStatus` (`String`, Index): Enum (`'CONNECTED' | 'DISCONNECTED'`).
  - `socketId` (`String`): Active WebSocket socket id.
  - `lastSeenAt` (`Date`): Heartbeat / last ping timestamp.
- **Indexes**:
  - `{ roomId: 1, userId: 1 }` (**Compound Unique**): Prevents duplicate membership records for the same user in a room.
  - `{ roomId: 1, connectionStatus: 1 }`: Optimized for real-time presence counting and eligible player queries.

### 1.4 `Draft`
- **Purpose**: Stores metadata and local/cloud references to voice recordings.
- **Fields**:
  - `draftId` (`String`, Unique, Index): Unique draft identifier.
  - `userId` (`String`, Index): Creator of the draft.
  - `title` (`String`): Title given to recording.
  - `durationMs` (`Number`): Duration in milliseconds.
  - `fileUrl` (`String`): Stored audio file location.
  - `effectApplied` (`String`): Audio effect applied (`'NONE' | 'ECHO' | 'REVERB' | 'PITCH_SHIFT'`).
  - `sharedInRooms` (`[String]`, Multikey Index): List of room IDs where this draft has been shared.
- **Indexes**:
  - `{ userId: 1, createdAt: -1 }`: Fast retrieval of a user's recent voice drafts.
  - `{ sharedInRooms: 1 }`: Fast retrieval of shared audio in a specific room.

### 1.5 `Spin`
- **Purpose**: Authoritative state machine tracking a single spin lifecycle in a room.
- **Fields**:
  - `spinId` (`String`, Unique, Index): Unique identifier.
  - `roomId` (`String`, Index): Associated room.
  - `initiatedBy` (`String`): Admin/Owner user who started the spin.
  - `status` (`String`, Index): State machine (`'WAITING' -> 'RUNNING' -> 'COMPLETED' | 'ABORTED'`).
  - `currentRound` (`Number`): Round counter incremented every 5 seconds.
  - `totalInitialParticipants` (`Number`): Starting participant count.
  - `winnerId` (`String`, Index): `userId` of the ultimate survivor.
  - `startedAt` (`Date`): Timestamp of spin kickoff.
  - `completedAt` (`Date`): Timestamp when winner was finalized.
  - `abortedReason` (`String`): Reason recorded if aborted.
  - `idempotencyKey` (`String`, Sparse Index): Guarantees at-most-once start execution.

### 1.6 `SpinParticipant`
- **Purpose**: Tracks player eligibility, status, and elimination order for a spin.
- **Fields**:
  - `spinId` (`String`, Index): Associated spin.
  - `roomId` (`String`): Associated room.
  - `userId` (`String`, Index): Participant ID.
  - `username` (`String`): Username.
  - `status` (`String`, Index): Enum (`'ELIGIBLE' | 'ELIMINATED' | 'WINNER' | 'SPECTATOR' | 'DISCONNECTED_ELIMINATED'`).
  - `eliminationOrder` (`Number`): Order in which player was knocked out (1st out, 2nd out...).
  - `eliminatedRound` (`Number`): Round number when knocked out.
  - `eliminatedAt` (`Date`): Timestamp of elimination.
- **Indexes**:
  - `{ spinId: 1, userId: 1 }` (**Compound Unique**): Prevents duplicate player registration in a single spin.
  - `{ spinId: 1, status: 1 }`: High-performance index for querying remaining survivors (`status: 'ELIGIBLE'`).

### 1.7 `SpinEvent`
- **Purpose**: Immutable append-only auditable log of every event emitted during the spin.
- **Fields**:
  - `eventId` (`String`, Unique, Index): Unique event ID.
  - `spinId` (`String`, Index): Associated spin.
  - `roomId` (`String`, Index): Associated room.
  - `eventType` (`String`): Mandatory event type (`'spin_started' | 'user_eliminated' | 'winner_announced' | 'spin_aborted'`).
  - `sequenceNumber` (`Number`): Monotonically increasing sequence index (1, 2, 3...).
  - `payload` (`Mixed`): Full event snapshot data.
  - `timestamp` (`Date`): Precise timestamp.
- **Indexes**:
  - `{ spinId: 1, sequenceNumber: 1 }` (**Compound Unique**): Guarantees strict ordering and idempotency of the published event stream.

---

## 2. Key Index Justifications

| Index Target | Type | Justification |
|---|---|---|
| `RoomMember { roomId: 1, userId: 1 }` | Compound Unique | Enforces integrity that a user cannot join the same room more than once concurrently. |
| `RoomMember { roomId: 1, connectionStatus: 1 }` | Compound | Crucial for real-time presence checks; filtering online members for starting a spin executes in sub-millisecond time. |
| `SpinParticipant { spinId: 1, status: 1 }` | Compound | Queried every 5 seconds by `spinEngine` to fetch the remaining `ELIGIBLE` pool without table scanning. |
| `SpinEvent { spinId: 1, sequenceNumber: 1 }` | Compound Unique | Ensures auditable, gapless replay of events for reconnecting clients. |
| `Spin { idempotencyKey: 1 }` | Sparse Unique | Prevents race-condition duplicate start requests from double-triggering wheel timers. |
