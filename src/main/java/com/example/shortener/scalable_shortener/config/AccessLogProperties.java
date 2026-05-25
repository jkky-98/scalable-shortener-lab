package com.example.shortener.scalable_shortener.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "shortener.access-log")
public class AccessLogProperties {

    private Mode mode = Mode.SYNC;
    private Async async = new Async();
    private Batch batch = new Batch();
    private SmartBatch smartBatch = new SmartBatch();

    public enum Mode {
        SYNC,
        ASYNC,
        BATCH,
        SMART_BATCH
    }

    @Getter
    @Setter
    public static class Async {
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 10000;
    }

    @Getter
    @Setter
    public static class Batch {
        private int queueCapacity = 20000;
        private int size = 500;
        private long flushIntervalMs = 100;
    }

    @Getter
    @Setter
    public static class SmartBatch {
        private int queueCapacity = 20000;
        private int size = 200;
        private long flushIntervalMs = 50;
    }
}
