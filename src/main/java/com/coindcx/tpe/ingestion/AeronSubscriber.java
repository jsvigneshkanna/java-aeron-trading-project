package com.coindcx.tpe.ingestion;

import com.coindcx.tpe.codec.EventDecoder;
import com.coindcx.tpe.engine.PositionStateEngine;
import com.coindcx.tpe.model.PositionEvent;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AeronSubscriber implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(AeronSubscriber.class);
    private static final int FRAGMENT_LIMIT = 10;

    private final Subscription subscription;
    private final PositionStateEngine engine;
    private final EventDecoder decoder;
    private final AtomicBoolean running;
    private final IdleStrategy idleStrategy;

    public AeronSubscriber(Aeron aeron, String channel, int streamId, PositionStateEngine engine) {
        this.subscription = aeron.addSubscription(channel, streamId);
        this.engine = engine;
        this.decoder = new EventDecoder();
        this.running = new AtomicBoolean(false);
        this.idleStrategy = new SleepingIdleStrategy(TimeUnit.MILLISECONDS.toNanos(1));
        logger.info("Aeron subscription created on channel={} streamId={}", channel, streamId);
    }

    @Override
    public void run() {
        running.set(true);
        logger.info("Aeron subscriber started");

        FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
            Optional<PositionEvent> eventOpt = decoder.decode(buffer, offset, length);
            eventOpt.ifPresent(event -> {
                logger.debug("Received event from Aeron: {}", event);
                engine.process(event);
            });
        };

        while (running.get()) {
            int fragmentsRead = subscription.poll(fragmentHandler, FRAGMENT_LIMIT);
            idleStrategy.idle(fragmentsRead);
        }

        logger.info("Aeron subscriber stopped");
    }

    public void stop() {
        running.set(false);
    }

    public void close() {
        stop();
        if (subscription != null) {
            subscription.close();
            logger.info("Aeron subscription closed");
        }
    }
}
