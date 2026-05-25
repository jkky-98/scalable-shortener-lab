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

    public enum Mode {
        SYNC,
        ASYNC
    }

    @Getter
    @Setter
    public static class Async {
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 10000;
    }
}
