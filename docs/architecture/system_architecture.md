# RoxStar System Architecture

## Architecture Overview

The RoxStar Platform is a real-time multiplayer audio studio and spin-wheel arena consisting of:
1. **Android Application**: Native Android app (Kotlin MVVM + Clean Architecture) with an ultra-low latency C++ audio engine built with Google Oboe.
2. **Real-Time Backend Service**: Node.js + Express + Socket.IO managing authoritative room state, membership presence, draft metadata, and deterministic 5-second cadence spin eliminations.
3. **Database Tier**: MongoDB persistent storage holding users, room records, membership states, draft audio references, and auditable event logs.
4. **Cloud & Infrastructure**: Containerized via Docker, orchestrated with Docker Compose, tested via GitHub Actions CI/CD, and deployed to Cloud (AWS ECS / GCP Cloud Run / Azure Container Apps).

```mermaid
graph TB
    subgraph Client ["Android Mobile Device"]
        UI["Android UI (Activities / ViewModels)"]
        Repo["Draft & Room Repositories"]
        JNI["JNI Bridge (native-lib.cpp)"]
        subgraph NativeAudio ["Native Audio Engine (C++20)"]
            OboeEngine["AudioEngine (Oboe Stream Lifecycle)"]
            DSP["DSP Voice Effects (Echo / Reverb)"]
            WavWriter["WavWriter (RIFF/WAV Header & PCM)"]
        end
        SocketClient["Socket.IO Client (ws://)"]
        HttpClient["Retrofit REST Client (http://)"]
    end

    subgraph NetworkLayer ["Load Balancer / Ingress"]
        ALB["Application Load Balancer (Sticky Sessions / WSS)"]
    end

    subgraph BackendCluster ["Node.js Real-Time Service (Docker)"]
        Express["Express REST API (/api/*)"]
        SocketServer["Socket.IO Engine (Room Broadcasts)"]
        RoomService["Room & Presence Service"]
        SpinEngine["Authoritative Spin Wheel Engine (5s Timer)"]
        Middlewares["Zod Validation & Idempotency"]
    end

    subgraph DatabaseTier ["Persistent Storage"]
        MongoDB[("MongoDB 7.0 (Users, Rooms, Drafts, Spins, Events)")]
    end

    UI --> Repo
    UI --> JNI
    JNI --> OboeEngine
    OboeEngine --> DSP
    DSP --> WavWriter
    Repo --> HttpClient
    Repo --> SocketClient

    HttpClient --> ALB
    SocketClient --> ALB
    ALB --> Express
    ALB --> SocketServer

    Express --> Middlewares
    Middlewares --> RoomService
    Middlewares --> SpinEngine
    SocketServer --> RoomService
    SocketServer --> SpinEngine

    RoomService --> MongoDB
    SpinEngine --> MongoDB
```
