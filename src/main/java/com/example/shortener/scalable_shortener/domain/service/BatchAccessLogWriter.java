package com.example.shortener.scalable_shortener.domain.service;

import com.example.shortener.scalable_shortener.config.AccessLogProperties;
import com.example.shortener.scalable_shortener.domain.entity.AccessLog;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchAccessLogWriter implements AccessLogWriter {

    private static final String INSERT_SQL = """
            insert into access_logs (short_key, accessed_at, ip_address, user_agent, referer)
            values (?, ?, ?, ?, ?)
            """;

    private final AccessLogProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    private ArrayBlockingQueue<AccessLog> queue;
    private ScheduledExecutorService scheduler;
    private TransactionTemplate transactionTemplate;

    @PostConstruct
    void start() {
        AccessLogProperties.Batch batch = properties.getBatch();
        queue = new ArrayBlockingQueue<>(batch.getQueueCapacity());
        transactionTemplate = new TransactionTemplate(transactionManager);
        scheduler = Executors.newSingleThreadScheduledExecutor(new AccessLogThreadFactory());
        scheduler.scheduleWithFixedDelay(
                this::flushSafely,
                batch.getFlushIntervalMs(),
                batch.getFlushIntervalMs(),
                TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        flushUntilEmpty();
    }

    @Override
    public AccessLogProperties.Mode mode() {
        return AccessLogProperties.Mode.BATCH;
    }

    @Override
    public void write(AccessLog accessLog) {
        try {
            queue.put(accessLog);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            insertBatch(List.of(accessLog));
        }
    }

    private void flushSafely() {
        try {
            flushOnce();
        } catch (Exception e) {
            log.error("Failed to flush access log batch", e);
        }
    }

    private void flushUntilEmpty() {
        while (queue != null && !queue.isEmpty()) {
            flushSafely();
        }
    }

    private void flushOnce() {
        List<AccessLog> batch = new ArrayList<>(properties.getBatch().getSize());
        queue.drainTo(batch, properties.getBatch().getSize());

        if (batch.isEmpty()) {
            return;
        }

        insertBatch(batch);
    }

    private void insertBatch(List<AccessLog> batch) {
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.batchUpdate(INSERT_SQL, batch, batch.size(), (ps, accessLog) -> {
                    ps.setString(1, accessLog.getShortKey());
                    ps.setTimestamp(2, Timestamp.valueOf(accessLog.getAccessedAt()));
                    ps.setString(3, accessLog.getIpAddress());
                    ps.setString(4, accessLog.getUserAgent());
                    ps.setString(5, accessLog.getReferer());
                })
        );
    }

    private static class AccessLogThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("access-log-batch-writer");
            thread.setDaemon(true);
            return thread;
        }
    }
}
