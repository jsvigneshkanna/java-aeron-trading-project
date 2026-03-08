package com.coindcx.tpe;

import com.coindcx.tpe.config.AeronConfig;
import com.coindcx.tpe.engine.PositionStateEngine;
import com.coindcx.tpe.engine.PositionStateStore;
import com.coindcx.tpe.ingestion.AeronSubscriber;
import com.coindcx.tpe.ingestion.BinanceWebSocketAdapter;
import com.coindcx.tpe.ingestion.KafkaEventConsumer;
import com.coindcx.tpe.publisher.AeronPublisher;
import com.coindcx.tpe.publisher.KafkaEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final String AERON_SUB_CHANNEL = System.getenv()
            .getOrDefault("AERON_SUB_CHANNEL", "aeron:ipc");
    private static final int AERON_SUB_STREAM_ID = Integer.parseInt(
            System.getenv().getOrDefault("AERON_SUB_STREAM_ID", "10"));
    private static final String AERON_PUB_CHANNEL = System.getenv()
            .getOrDefault("AERON_PUB_CHANNEL", "aeron:ipc");
    private static final int AERON_PUB_STREAM_ID = Integer.parseInt(
            System.getenv().getOrDefault("AERON_PUB_STREAM_ID", "20"));

    public static void main(String[] args) {
        printBanner();

        AeronConfig aeronConfig = AeronConfig.getInstance();
        KafkaEventProducer kafkaProducer = new KafkaEventProducer();
        AeronPublisher aeronPublisher = new AeronPublisher(
                aeronConfig.getAeron(), AERON_PUB_CHANNEL, AERON_PUB_STREAM_ID);
        
        PositionStateStore store = new PositionStateStore();
        PositionStateEngine engine = new PositionStateEngine(store, aeronPublisher, kafkaProducer);
        
        KafkaEventConsumer kafkaConsumer = new KafkaEventConsumer(engine);
        Thread kafkaConsumerThread = new Thread(kafkaConsumer, "kafka-consumer");
        
        AeronSubscriber aeronSubscriber = new AeronSubscriber(
                aeronConfig.getAeron(), AERON_SUB_CHANNEL, AERON_SUB_STREAM_ID, engine);
        Thread aeronSubscriberThread = new Thread(aeronSubscriber, "aeron-subscriber");
        
        BinanceWebSocketAdapter binanceAdapter = new BinanceWebSocketAdapter(
                engine, aeronPublisher, kafkaProducer);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, stopping components...");
            
            binanceAdapter.stop();
            kafkaConsumer.stop();
            aeronSubscriber.stop();
            
            try {
                kafkaConsumerThread.join(5000);
                aeronSubscriberThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for threads to stop");
            }
            
            kafkaConsumer.close();
            aeronSubscriber.close();
            kafkaProducer.close();
            aeronPublisher.close();
            aeronConfig.close();
            
            logger.info("Shutdown complete");
        }, "shutdown-hook"));

        kafkaConsumerThread.start();
        aeronSubscriberThread.start();
        binanceAdapter.start();

        logger.info("TPE Position Tracker — STARTED");
        logger.info("Active threads: kafka-consumer, aeron-subscriber, binance-adapter");
        logger.info("Press Ctrl+C to stop");

        try {
            kafkaConsumerThread.join();
            aeronSubscriberThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Main thread interrupted");
        }
    }

    private static void printBanner() {
        String banner = """
                ╔════════════════════════════════════════════════════════════╗
                ║                                                            ║
                ║         TPE Position Tracker — STARTED                     ║
                ║                                                            ║
                ║   Real-time Position Tracking with Aeron IPC + Kafka       ║
                ║                                                            ║
                ╚════════════════════════════════════════════════════════════╝
                """;
        System.out.println(banner);
    }
}
