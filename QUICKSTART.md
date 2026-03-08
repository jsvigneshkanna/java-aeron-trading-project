# Quick Start Guide

## 1. Get Binance Futures Testnet Credentials

1. Visit [https://testnet.binancefuture.com](https://testnet.binancefuture.com)
2. Sign up or log in
3. Go to API Management
4. Create a new API key (save both key and secret)
5. Note your account ID from the account page

## 2. Configure Environment

```bash
cp .env.example .env
```

Edit `.env` and add your credentials:

```bash
BINANCE_API_KEY=your_actual_api_key_here
BINANCE_API_SECRET=your_actual_api_secret_here
BINANCE_ACCOUNT_ID=your_actual_account_id_here
```

## 3. Start the System

```bash
docker-compose -f docker-compose.demo.yml up --build
```

Wait for the banner:

```
╔════════════════════════════════════════════════════════════╗
║                                                            ║
║         TPE Position Tracker — STARTED                     ║
║                                                            ║
║   Real-time Position Tracking with Aeron IPC + Kafka      ║
║                                                            ║
╚════════════════════════════════════════════════════════════╝

Connected to Binance Futures Testnet WebSocket
```

## 4. Test It Out

### Option A: Place Orders on Binance Testnet

1. Go to [https://testnet.binancefuture.com](https://testnet.binancefuture.com)
2. Select a trading pair (e.g., BTCUSDT)
3. Place a market order
4. Watch the logs for real-time updates:

```
Received [OPEN] ACC123:BTCUSDT qty=1.5000 leverage=10.0x side=LONG
State updated: ACC123:BTCUSDT → ACTIVE qty=1.5000 leverage=10.0x
Published to Aeron streamId=20
Published to Kafka topic=position-state-updates partition=0 offset=42
```

### Option B: Send Test Events via Kafka

1. Open Kafka UI at [http://localhost:8080](http://localhost:8080)
2. Navigate to Topics → `position-events`
3. Click "Produce Message"
4. Send a test event:

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

1. Check `position-state-updates` topic for the resulting state

## 5. Monitor

- **Logs**: Watch Docker Compose logs for real-time events
- **Kafka UI**: [http://localhost:8080](http://localhost:8080) to view topics and messages
- **Metrics**: Check container logs for throughput stats

## 6. Stop the System

```bash
docker-compose -f docker-compose.demo.yml down
```

## Troubleshooting

### "WebSocket connection failed"

- Verify your API key/secret are correct
- Check Binance Testnet is accessible
- Ensure API key has the right permissions

### "Kafka connection refused"

- Wait 30 seconds for Kafka to fully start
- Check `docker-compose ps` shows all services running

### "No position updates"

- Ensure you're using Binance **Futures** Testnet (not spot)
- Verify `BINANCE_ACCOUNT_ID` matches your testnet account
- Check WebSocket connection status in logs

## Next Steps

- Review `README.md` for detailed architecture
- Explore source code in `src/main/java/com/coindcx/tpe/`
- Run tests: `docker run --rm -v $(pwd):/app -w /app maven:3.9-eclipse-temurin-17 mvn test`

