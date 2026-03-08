package com.coindcx.tpe.config;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AeronConfig {
    private static final Logger logger = LoggerFactory.getLogger(AeronConfig.class);
    
    private static volatile AeronConfig instance;
    private final MediaDriver mediaDriver;
    private final Aeron aeron;

    private AeronConfig() {
        String aeronDir = System.getProperty("aeron.dir", "/tmp/aeron");
        
        MediaDriver.Context mediaDriverContext = new MediaDriver.Context()
                .dirDeleteOnStart(false)
                .dirDeleteOnShutdown(false)
                .aeronDirectoryName(aeronDir)
                .threadingMode(ThreadingMode.SHARED);

        this.mediaDriver = MediaDriver.launchEmbedded(mediaDriverContext);
        logger.info("Embedded MediaDriver launched at {}", mediaDriver.aeronDirectoryName());

        Aeron.Context aeronContext = new Aeron.Context()
                .aeronDirectoryName(mediaDriver.aeronDirectoryName());

        this.aeron = Aeron.connect(aeronContext);
        logger.info("Aeron client connected");
    }

    public static AeronConfig getInstance() {
        if (instance == null) {
            synchronized (AeronConfig.class) {
                if (instance == null) {
                    instance = new AeronConfig();
                }
            }
        }
        return instance;
    }

    public Aeron getAeron() {
        return aeron;
    }

    public void close() {
        logger.info("Closing Aeron resources");
        if (aeron != null) {
            aeron.close();
        }
        if (mediaDriver != null) {
            mediaDriver.close();
        }
    }
}
