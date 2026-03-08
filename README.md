# TPE Position Tracker

Real-time position tracking system that consumes position events from Aeron IPC and Kafka, tracks active/inactive positions with leverage per account-instrument pair, and publishes state updates.

## Architecture
```
Binance Futures Testnet (WebSocket)
            │
            ▼
  BinanceWebSocketAdapter
  (normalizes to PositionEvent)
            │
     ┌──────┴──────┐
     ▼             ▼
 Aeron IPC      Kafka
 (streamId=10)  (position-events)
     │             │
     └──────┬───────┘
            ▼
   PositionStateEngine
   (OPEN → ACTIVE, CLOSE → INACTIVE)
   (tracks leverage per account:instrument)
            │
     ┌──────┴──────┐
     ▼             ▼
 Aeron IPC      Kafka
 (streamId=20)  (position-state-updates)

```

## System Overview
This is the TPE Position Tracker — it consumes real-time position events from Binance Futures Testnet and routes them through Aeron IPC and Kafka.
The entire stack runs containerized — Zookeeper, Kafka, the tracker service, and Kafka UI — all spun up with a single Docker Compose command.
When I place a futures order on Binance Testnet, the WebSocket adapter normalizes it into a PositionEvent and you can see it flow through the logs in real time.
The state engine tracks every position by account-instrument pair — automatically marking it ACTIVE on open, updating leverage on changes, and INACTIVE on close.
Every state update is published to both Aeron IPC for low-latency internal consumers, and Kafka for downstream systems — visible here live in the Kafka UI.

## Tech Stack
1. Java
2. Aeron (low latency stream)
3. Kafka (reliable stream)
4. docker