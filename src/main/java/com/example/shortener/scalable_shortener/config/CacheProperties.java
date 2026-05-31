package com.example.shortener.scalable_shortener.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "shortener.cache")
public class CacheProperties {

    private boolean enabled = false;
    private long ttlSeconds = 3600;
    private String keyPrefix = "shortener:url:";
}
