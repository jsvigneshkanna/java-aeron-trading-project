package com.coindcx.tpe.ingestion;

import com.coindcx.tpe.engine.PositionStateEngine;
import com.coindcx.tpe.model.EventType;
import com.coindcx.tpe.model.PositionEvent;
import com.coindcx.tpe.publisher.AeronPublisher;
import com.coindcx.tpe.publisher.KafkaEventProducer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BinanceWebSocketAdapter {
    private static final Logger logger = LoggerFactory.getLogger(BinanceWebSocketAdapter.class);

    private static final String REST_BASE = System.getenv()
            .getOrDefault("BINANCE_REST_BASE", "https://testnet.binancefuture.com");
    private static final String WS_BASE = System.getenv()
            .getOrDefault("BINANCE_WS_BASE", "wss://stream.binancefuture.com");
    private static final String API_KEY = System.getenv("BINANCE_API_KEY");
    private static final String API_SECRET = System.getenv("BINANCE_API_SECRET");
    private static final String ACCOUNT_ID = System.getenv("BINANCE_ACCOUNT_ID");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PositionStateEngine engine;
    private final AeronPublisher aeronPublisher;
    private final KafkaEventProducer kafkaProducer;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;

    private WebSocket webSocket;
    private String listenKey;
    private final StringBuilder messageBuffer;

    public BinanceWebSocketAdapter(PositionStateEngine engine, AeronPublisher aeronPublisher,
                                   KafkaEventProducer kafkaProducer) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.engine = engine;
        this.aeronPublisher = aeronPublisher;
        this.kafkaProducer = kafkaProducer;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.running = new AtomicBoolean(false);
        this.messageBuffer = new StringBuilder();
    }

    public void start() {
        if (API_KEY == null || API_SECRET == null || ACCOUNT_ID == null) {
            logger.error("Missing Binance credentials. Set BINANCE_API_KEY, BINANCE_API_SECRET, BINANCE_ACCOUNT_ID");
            return;
        }

        running.set(true);
        
        try {
            listenKey = createListenKey();
            connectWebSocket();
            startKeepaliveTask();
            logger.info("✅ Connected to Binance Futures Testnet WebSocket");
        } catch (Exception e) {
            logger.error("Failed to start Binance WebSocket adapter", e);
            running.set(false);
        }
    }

    private String createListenKey() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(REST_BASE + "/fapi/v1/listenKey"))
                .header("X-MBX-APIKEY", API_KEY)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to create listenKey: " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        String key = json.get("listenKey").asText();
        logger.info("Created Binance listenKey");
        return key;
    }

    private void connectWebSocket() {
        String wsUrl = WS_BASE + "/ws/" + listenKey;
        
        webSocket = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        logger.info("WebSocket opened");
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        messageBuffer.append(data);
                        
                        if (last) {
                            String message = messageBuffer.toString();
                            messageBuffer.setLength(0);
                            handleMessage(message);
                        }
                        
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        logger.warn("WebSocket closed: {} - {}", statusCode, reason);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        logger.error("WebSocket error", error);
                    }
                }).join();
    }

    private void handleMessage(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String eventType = json.has("e") ? json.get("e").asText() : "";

            if ("ORDER_TRADE_UPDATE".equals(eventType)) {
                handleOrderTradeUpdate(json);
            } else if ("ACCOUNT_UPDATE".equals(eventType)) {
                handleAccountUpdate(json);
            }
        } catch (Exception e) {
            logger.error("Failed to handle WebSocket message: {}", message, e);
        }
    }

    private void handleOrderTradeUpdate(JsonNode json) {
        try {
            JsonNode order = json.get("o");
            String symbol = order.get("s").asText();
            String binanceSide = order.get("S").asText();
            String side = "BUY".equals(binanceSide) ? "LONG" : "SHORT";
            double quantity = order.get("q").asDouble();
            long timestamp = order.get("T").asLong();
            String orderStatus = order.get("X").asText();

            EventType eventTypeEnum;
            switch (orderStatus) {
                case "NEW":
                    eventTypeEnum = EventType.OPEN;
                    break;
                case "PARTIALLY_FILLED":
                    eventTypeEnum = EventType.UPDATE;
                    break;
                case "FILLED":
                case "CANCELED":
                case "EXPIRED":
                    eventTypeEnum = EventType.CLOSE;
                    break;
                default:
                    logger.debug("Ignoring order status: {}", orderStatus);
                    return;
            }

            double leverage = fetchLeverage(symbol);
            
            PositionEvent event = new PositionEvent(ACCOUNT_ID, symbol, eventTypeEnum,
                    quantity, leverage, side, timestamp);
            
            aeronPublisher.publishEvent(event);
            kafkaProducer.publishEvent(event);
            engine.process(event);
        } catch (Exception e) {
            logger.error("Failed to handle ORDER_TRADE_UPDATE", e);
        }
    }

    private void handleAccountUpdate(JsonNode json) {
        try {
            long timestamp = json.get("T").asLong();
            JsonNode updateData = json.get("a");
            
            if (updateData != null && updateData.has("P")) {
                JsonNode positions = updateData.get("P");
                
                for (JsonNode position : positions) {
                    String symbol = position.get("s").asText();
                    String binanceSide = position.get("ps").asText();
                    String side = "LONG".equals(binanceSide) ? "LONG" : "SHORT";
                    double positionAmount = position.get("pa").asDouble();
                    double quantity = Math.abs(positionAmount);

                    EventType eventTypeEnum = quantity > 0 ? EventType.UPDATE : EventType.CLOSE;
                    double leverage = fetchLeverage(symbol);

                    PositionEvent event = new PositionEvent(ACCOUNT_ID, symbol, eventTypeEnum,
                            quantity, leverage, side, timestamp);
                    
                    aeronPublisher.publishEvent(event);
                    kafkaProducer.publishEvent(event);
                    engine.process(event);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to handle ACCOUNT_UPDATE", e);
        }
    }

    private double fetchLeverage(String symbol) {
        try {
            long timestamp = System.currentTimeMillis();
            String queryString = "symbol=" + symbol + "&timestamp=" + timestamp;
            String signature = BinanceAuthUtil.sign(queryString, API_SECRET);
            
            String url = REST_BASE + "/fapi/v2/positionRisk?" + queryString + "&signature=" + signature;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-MBX-APIKEY", API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode positions = objectMapper.readTree(response.body());
                if (positions.isArray() && positions.size() > 0) {
                    return positions.get(0).get("leverage").asDouble();
                }
            }
            
            logger.debug("Could not fetch leverage for {}, defaulting to 1.0", symbol);
            return 1.0;
        } catch (Exception e) {
            logger.debug("Error fetching leverage for {}, defaulting to 1.0", symbol, e);
            return 1.0;
        }
    }

    private void startKeepaliveTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                keepaliveListenKey();
            } catch (Exception e) {
                logger.error("Failed to send keepalive", e);
            }
        }, 30, 30, TimeUnit.MINUTES);
    }

    private void keepaliveListenKey() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(REST_BASE + "/fapi/v1/listenKey"))
                .header("X-MBX-APIKEY", API_KEY)
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            logger.debug("🔄 Binance listenKey keepalive sent");
        } else {
            logger.warn("Keepalive failed with status {}: {}", response.statusCode(), response.body());
        }
    }

    public void stop() {
        running.set(false);
        
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Shutting down");
            logger.info("WebSocket closed");
        }
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Binance WebSocket adapter stopped");
    }
}
