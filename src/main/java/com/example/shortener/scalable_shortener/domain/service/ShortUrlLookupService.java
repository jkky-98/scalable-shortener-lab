package com.example.shortener.scalable_shortener.domain.service;

import com.example.shortener.scalable_shortener.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortUrlLookupService {

    private final ShortUrlReadService shortUrlReadService;
    private final StringRedisTemplate redisTemplate;
    private final CacheProperties cacheProperties;
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();
    private final LongAdder cacheBypasses = new LongAdder();
    private final LongAdder cacheErrors = new LongAdder();

    public ShortUrlLookupResult findOriginalUrl(String shortKey) {
        if (!cacheProperties.isEnabled()) {
            cacheBypasses.increment();
            return findFromDatabase(shortKey, CacheLookupStatus.BYPASS);
        }

        String cacheKey = cacheKey(shortKey);
        try {
            String cachedOriginalUrl = redisTemplate.opsForValue().get(cacheKey);
            if (cachedOriginalUrl != null) {
                cacheHits.increment();
                return new ShortUrlLookupResult(cachedOriginalUrl, CacheLookupStatus.HIT);
            }

            cacheMisses.increment();
            return findFromDatabaseAndCache(shortKey, cacheKey);
        } catch (DataAccessException ex) {
            cacheErrors.increment();
            log.warn("Redis lookup failed. Falling back to MySQL. key={}", shortKey, ex);
            return findFromDatabase(shortKey, CacheLookupStatus.ERROR);
        }
    }

    public Map<String, Object> stats() {
        long hits = cacheHits.sum();
        long misses = cacheMisses.sum();
        long cacheRequests = hits + misses;
        double hitRatio = cacheRequests == 0 ? 0.0 : (double) hits / cacheRequests;

        return Map.of(
                "enabled", cacheProperties.isEnabled(),
                "ttlSeconds", cacheProperties.getTtlSeconds(),
                "hits", hits,
                "misses", misses,
                "bypasses", cacheBypasses.sum(),
                "errors", cacheErrors.sum(),
                "hitRatio", hitRatio
        );
    }

    public void resetStats() {
        cacheHits.reset();
        cacheMisses.reset();
        cacheBypasses.reset();
        cacheErrors.reset();
    }

    private ShortUrlLookupResult findFromDatabaseAndCache(String shortKey, String cacheKey) {
        ShortUrlLookupResult result = findFromDatabase(shortKey, CacheLookupStatus.MISS);
        if (!result.found()) {
            return result;
        }

        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    result.originalUrl(),
                    Duration.ofSeconds(cacheProperties.getTtlSeconds())
            );
        } catch (DataAccessException ex) {
            cacheErrors.increment();
            log.warn("Redis cache store failed. key={}", shortKey, ex);
        }

        return result;
    }

    private ShortUrlLookupResult findFromDatabase(String shortKey, CacheLookupStatus status) {
        return shortUrlReadService.findOriginalUrl(shortKey)
                .map(originalUrl -> new ShortUrlLookupResult(originalUrl, status))
                .orElseGet(() -> new ShortUrlLookupResult(null, status));
    }

    private String cacheKey(String shortKey) {
        return cacheProperties.getKeyPrefix() + shortKey;
    }
}
