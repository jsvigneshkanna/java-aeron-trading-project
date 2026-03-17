# TPE Position Tracker

A real-time position tracking system for cryptocurrency futures trading. It ingests live order and account updates from **Binance Futures Testnet** via WebSocket, normalizes them into position events, tracks state per account-instrument pair, and publishes updates through both **Aeron IPC** (for ultra-low-latency internal consumers) and **Apache Kafka** (for reliable downstream processing).

## Architecture

```
Binance Futures Testnet (WebSocket)
            │
            ▼
  BinanceWebSocketAdapter
  (normalizes to PositionEvent)
            │
     ┌──────┴──────┐
     ▼              ▼
 Aeron IPC       Kafka
 (streamId=10)   (position-events)
     │              │
     └──────┬───────┘
            ▼
   PositionStateEngine
   (OPEN → ACTIVE, CLOSE → INACTIVE)
   (tracks leverage per account:instrument)
            │
     ┌──────┴──────┐
     ▼              ▼
 Aeron IPC       Kafka
 (streamId=20)   (position-state-updates)
```

**Dual-channel design** — Aeron IPC provides nanosecond-scale latency for co-located processes, while Kafka ensures durable, replayable delivery for distributed consumers.

## How It Works

1. **Ingestion** — The `BinanceWebSocketAdapter` connects to Binance Futures Testnet using a signed listen key. It receives `ORDER_TRADE_UPDATE` and `ACCOUNT_UPDATE` events over WebSocket, normalizes them into `PositionEvent` objects, and publishes each event to Aeron IPC (stream 10) and Kafka (`position-events` topic).

2. **State Tracking** — The `PositionStateEngine` consumes events from both the `AeronSubscriber` (stream 10) and the `KafkaEventConsumer` (`position-events` topic). For each event it updates an in-memory `PositionStateStore` keyed by `(accountId, instrumentId)`:
   - `OPEN` → marks the position **ACTIVE** with quantity, leverage, and side
   - `UPDATE` → updates quantity and leverage on the existing position
   - `CLOSE` → marks the position **INACTIVE**

3. **Publishing** — Every state transition is published to Aeron IPC (stream 20) and Kafka (`position-state-updates` topic) so downstream systems can react in real time.

## Project Structure

```
src/main/java/com/coindcx/tpe/
├── Main.java                          # Entry point — wires all components and manages lifecycle
├── config/
│   ├── AeronConfig.java               # Singleton embedded MediaDriver + Aeron client
│   └── KafkaConfig.java               # Kafka producer/consumer properties from env vars
├── codec/
│   ├── EventEncoder.java              # Encodes PositionEvent/PositionState → pipe-delimited text
│   └── EventDecoder.java              # Decodes pipe-delimited text → PositionEvent
├── engine/
│   ├── PositionStateEngine.java       # Core state machine — processes events, emits state updates
│   └── PositionStateStore.java        # Thread-safe in-memory store (ConcurrentHashMap)
├── ingestion/
│   ├── BinanceWebSocketAdapter.java   # Binance Futures WebSocket client with auto-reconnect
│   ├── BinanceAuthUtil.java           # HMAC-SHA256 signing for Binance API authentication
│   ├── AeronSubscriber.java           # Subscribes to Aeron IPC stream, feeds engine
│   └── KafkaEventConsumer.java        # Consumes from Kafka topic, feeds engine
├── model/
│   ├── PositionEvent.java             # Immutable event (accountId, instrumentId, type, qty, leverage, side)
│   ├── PositionState.java             # Immutable state (key, qty, leverage, side, status)
│   ├── AccountInstrumentKey.java      # Composite key with equals/hashCode
│   ├── EventType.java                 # Enum: OPEN, UPDATE, CLOSE
│   └── PositionStatus.java            # Enum: ACTIVE, INACTIVE
└── publisher/
    ├── AeronPublisher.java            # Publishes to Aeron IPC with back-pressure retry
    └── KafkaEventProducer.java        # Publishes to Kafka as JSON
```

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 17 |
| Build | Maven | 3.9+ |
| Low-latency messaging | Aeron IPC | 1.44.0 |
| High-performance buffers | Agrona | 1.21.0 |
| Distributed streaming | Apache Kafka | 3.7.0 |
| JSON serialization | Jackson | 2.17.0 |
| Logging | SLF4J + Logback | 2.0.12 / 1.5.3 |
| Testing | JUnit 5 + Mockito + Testcontainers | 5.10.2 / 5.11.0 / 1.19.7 |
| Containerization | Docker + Docker Compose | - |

## Prerequisites

- **Java 17** (or Docker for containerized builds)
- **Maven 3.9+** (or Docker for containerized builds)
- **Docker** and **Docker Compose**
- A **Binance Futures Testnet** account with API credentials

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/<your-username>/java-aeron-trading-project.git
cd java-aeron-trading-project
```

### 2. Get Binance Futures Testnet credentials

1. Visit [https://testnet.binancefuture.com](https://testnet.binancefuture.com)
2. Sign up or log in
3. Go to **API Management** and create a new API key
4. Save both the API key and secret
5. Note your account ID from the account page

### 3. Configure environment

```bash
cp .env.example .env
```

Edit `.env` with your credentials:

```bash
BINANCE_API_KEY=your_actual_api_key
BINANCE_API_SECRET=your_actual_api_secret
BINANCE_ACCOUNT_ID=your_actual_account_id
```

### 4. Run with Docker Compose (recommended)

Start the full stack — Zookeeper, Kafka, the tracker, and Kafka UI:

```bash
docker-compose -f docker-compose.demo.yml up --build
```

Wait for the startup banner:

```
╔════════════════════════════════════════════════════════════╗
║                                                            ║
║         TPE Position Tracker — STARTED                     ║
║                                                            ║
║   Real-time Position Tracking with Aeron IPC + Kafka       ║
║                                                            ║
╚════════════════════════════════════════════════════════════╝
```

The Kafka UI is available at [http://localhost:8080](http://localhost:8080).

### 5. Run locally (without Docker for the app)

Start only the infrastructure:

```bash
docker-compose up -d zookeeper kafka
```

Build and run the application:

```bash
mvn clean package -Dmaven.test.skip=true
java -jar target/app.jar
```

## Configuration

All configuration is driven by environment variables (loaded from `.env` by Docker Compose):

| Variable | Default | Description |
|----------|---------|-------------|
| `BINANCE_API_KEY` | *(required)* | Binance Futures Testnet API key |
| `BINANCE_API_SECRET` | *(required)* | Binance Futures Testnet API secret |
| `BINANCE_ACCOUNT_ID` | *(required)* | Your Binance Testnet account ID |
| `BINANCE_REST_BASE` | `https://testnet.binancefuture.com` | Binance REST API base URL |
| `BINANCE_WS_BASE` | `wss://stream.binancefuture.com` | Binance WebSocket base URL |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker addresses |
| `KAFKA_POSITION_EVENTS_TOPIC` | `position-events` | Topic for inbound position events |
| `KAFKA_STATE_UPDATES_TOPIC` | `position-state-updates` | Topic for outbound state updates |
| `KAFKA_CONSUMER_GROUP` | `tpe-position-tracker` | Kafka consumer group ID |
| `AERON_SUB_CHANNEL` | `aeron:ipc` | Aeron subscription channel |
| `AERON_SUB_STREAM_ID` | `10` | Aeron stream ID for raw events |
| `AERON_PUB_CHANNEL` | `aeron:ipc` | Aeron publication channel |
| `AERON_PUB_STREAM_ID` | `20` | Aeron stream ID for state updates |

## Testing

### Unit tests

```bash
mvn test
```

Uses JUnit 5 and Mockito for isolated unit testing of the state engine and codecs.

### Integration tests

Integration tests use [Testcontainers](https://www.testcontainers.org/) to spin up a real Kafka broker in Docker:

```bash
mvn verify
```

### Run tests in Docker (no local Java needed)

```bash
docker run --rm -v "$(pwd)":/app -w /app maven:3.9-eclipse-temurin-17 mvn test
```

## Testing the Live System

### Option A: Place orders on Binance Testnet

1. Go to [https://testnet.binancefuture.com](https://testnet.binancefuture.com)
2. Select a futures trading pair (e.g., BTCUSDT)
3. Place a market order
4. Watch the Docker Compose logs for real-time updates:

```
Received [OPEN] ACC123:BTCUSDT qty=1.5000 leverage=10.0x side=LONG
State updated: ACC123:BTCUSDT → ACTIVE qty=1.5000 leverage=10.0x
Published to Aeron streamId=20
Published to Kafka topic=position-state-updates partition=0 offset=42
```

### Option B: Send test events via Kafka UI

1. Open [http://localhost:8080](http://localhost:8080)
2. Navigate to **Topics** > `position-events` > **Produce Message**
3. Send a test event:

```json
{
  "accountId": "TEST_ACC",
  "instrumentId": "BTCUSDT",
  "eventType": "OPEN",
  "quantity": 1.5,
  "leverage": 10.0,
  "side": "LONG",
  "timestamp": 1709876543210
}
```

4. Check the `position-state-updates` topic for the resulting state update

## Message Formats

### Aeron IPC (pipe-delimited text)

**Event:**
```
EVT|{eventType}|{accountId}|{instrumentId}|{quantity}|{leverage}|{side}|{timestamp}
```

**State:**
```
STATE|{accountId}|{instrumentId}|{quantity}|{leverage}|{side}|{status}|{lastUpdatedAt}
```

### Kafka (JSON)

**Event (`position-events` topic):**
```json
{
  "accountId": "ACC123",
  "instrumentId": "BTCUSDT",
  "eventType": "OPEN",
  "quantity": 1.5,
  "leverage": 10.0,
  "side": "LONG",
  "timestamp": 1709876543210
}
```

**State (`position-state-updates` topic):**
```json
{
  "accountId": "ACC123",
  "instrumentId": "BTCUSDT",
  "quantity": 1.5,
  "leverage": 10.0,
  "side": "LONG",
  "status": "ACTIVE",
  "lastUpdatedAt": 1709876543210
}
```

## Docker Services

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `zookeeper` | confluentinc/cp-zookeeper:7.6.0 | 2181 | Kafka coordination |
| `kafka` | confluentinc/cp-kafka:7.6.0 | 9092 | Message broker |
| `tpe-tracker` | Built from `Dockerfile` | 20121, 20122 | Position tracker application |
| `kafka-ui` | provectuslabs/kafka-ui:latest | 8080 | Web UI for Kafka (demo compose only) |

The Dockerfile uses a multi-stage build: Maven + Eclipse Temurin 17 for building, and Eclipse Temurin 17 JRE for the runtime image. The JVM is configured with G1GC, 256m-512m heap, and the necessary `--add-opens` flags for Aeron.

## Troubleshooting

| Problem | Possible Cause | Solution |
|---------|---------------|----------|
| WebSocket connection failed | Invalid API credentials | Verify `BINANCE_API_KEY` and `BINANCE_API_SECRET` in `.env` |
| WebSocket connection failed | Testnet unreachable | Check that `https://testnet.binancefuture.com` is accessible |
| Kafka connection refused | Kafka not ready yet | Wait 30 seconds after `docker-compose up` for Kafka to initialize |
| Kafka connection refused | Services not running | Run `docker-compose ps` to verify all services are healthy |
| No position updates | Wrong testnet type | Ensure you are using Binance **Futures** Testnet, not Spot Testnet |
| No position updates | Account ID mismatch | Verify `BINANCE_ACCOUNT_ID` matches your testnet account |
| Aeron back-pressure warnings | Subscriber too slow | Check consumer thread health; increase buffer sizes if needed |

## License

This project is licensed under the [Apache License 2.0](LICENSE).
