package com.example.shortener.scalable_shortener.domain.service;

import com.example.shortener.scalable_shortener.domain.entity.AccessLog;
import com.example.shortener.scalable_shortener.domain.repository.AccessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AsyncAccessLogWriter {

    private final AccessLogRepository accessLogRepository;

    @Async("accessLogExecutor")
    @Transactional
    public void save(AccessLog accessLog) {
        accessLogRepository.save(accessLog);
    }
}
