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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartBatchAccessLogWriter implements AccessLogWriter {

    private static final String INSERT_SQL = """
            insert into access_logs (short_key, accessed_at, ip_address, user_agent, referer)
            values (?, ?, ?, ?, ?)
            """;

    private final AccessLogProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    private final Queue<AccessLog> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger();
    private final AtomicBoolean flushRequested = new AtomicBoolean();

    private Semaphore capacityLimiter;
    private TransactionTemplate transactionTemplate;
    private Thread workerThread;
    private volatile boolean running = true;

    @PostConstruct
    void start() {
        AccessLogProperties.SmartBatch smartBatch = properties.getSmartBatch();
        capacityLimiter = new Semaphore(smartBatch.getQueueCapacity());
        transactionTemplate = new TransactionTemplate(transactionManager);
        workerThread = new Thread(this::runLoop, "access-log-smart-batch-writer");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        LockSupport.unpark(workerThread);

        try {
            workerThread.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        flushUntilEmpty();
    }

    @Override
    public AccessLogProperties.Mode mode() {
        return AccessLogProperties.Mode.SMART_BATCH;
    }

    @Override
    public void write(AccessLog accessLog) {
        try {
            capacityLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            insertBatch(List.of(accessLog));
            return;
        }

        queue.offer(accessLog);
        int currentSize = queueSize.incrementAndGet();

        if (currentSize >= properties.getSmartBatch().getSize()) {
            requestFlush();
        }
    }

    private void runLoop() {
        while (running) {
            AccessLogProperties.SmartBatch smartBatch = properties.getSmartBatch();
            long flushIntervalNanos = TimeUnit.MILLISECONDS.toNanos(smartBatch.getFlushIntervalMs());

            if (shouldWaitForMore(smartBatch)) {
                LockSupport.parkNanos(flushIntervalNanos);
            }

            flushRequested.set(false);
            flushSafely();
        }

        flushUntilEmpty();
    }

    private boolean shouldWaitForMore(AccessLogProperties.SmartBatch smartBatch) {
        int currentSize = queueSize.get();
        return currentSize == 0 || (currentSize < smartBatch.getSize() && !flushRequested.get());
    }

    private void requestFlush() {
        flushRequested.set(true);
        LockSupport.unpark(workerThread);
    }

    private void flushSafely() {
        try {
            flushOnce();
        } catch (Exception e) {
            log.error("Failed to flush smart access log batch", e);
        }
    }

    private void flushUntilEmpty() {
        while (queueSize.get() > 0) {
            flushSafely();
        }
    }

    private void flushOnce() {
        AccessLogProperties.SmartBatch smartBatch = properties.getSmartBatch();
        List<AccessLog> batch = new ArrayList<>(smartBatch.getSize());

        for (int i = 0; i < smartBatch.getSize(); i++) {
            AccessLog accessLog = queue.poll();
            if (accessLog == null) {
                break;
            }

            queueSize.decrementAndGet();
            capacityLimiter.release();
            batch.add(accessLog);
        }

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
}
