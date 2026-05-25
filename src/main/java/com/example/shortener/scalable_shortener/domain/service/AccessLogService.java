package com.example.shortener.scalable_shortener.domain.service;

import com.example.shortener.scalable_shortener.config.AccessLogProperties;
import com.example.shortener.scalable_shortener.domain.entity.AccessLog;
import com.example.shortener.scalable_shortener.domain.repository.AccessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;
    private final AsyncAccessLogWriter asyncAccessLogWriter;
    private final AccessLogProperties accessLogProperties;

    public void save(AccessLog accessLog) {
        if (accessLogProperties.getMode() == AccessLogProperties.Mode.ASYNC) {
            asyncAccessLogWriter.save(accessLog);
            return;
        }

        accessLogRepository.save(accessLog);
    }
}
