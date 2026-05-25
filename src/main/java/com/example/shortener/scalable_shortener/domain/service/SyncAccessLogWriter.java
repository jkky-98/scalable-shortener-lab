package com.example.shortener.scalable_shortener.domain.service;

import com.example.shortener.scalable_shortener.config.AccessLogProperties;
import com.example.shortener.scalable_shortener.domain.entity.AccessLog;
import com.example.shortener.scalable_shortener.domain.repository.AccessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SyncAccessLogWriter implements AccessLogWriter {

    private final AccessLogRepository accessLogRepository;

    @Override
    public AccessLogProperties.Mode mode() {
        return AccessLogProperties.Mode.SYNC;
    }

    @Override
    public void write(AccessLog accessLog) {
        accessLogRepository.save(accessLog);
    }
}
