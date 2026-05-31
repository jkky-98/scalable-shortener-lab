package com.example.shortener.scalable_shortener.domain.service;

public record ShortUrlLookupResult(String originalUrl, CacheLookupStatus cacheStatus) {

    public boolean found() {
        return originalUrl != null;
    }
}
