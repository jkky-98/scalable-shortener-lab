package com.example.shortener.scalable_shortener.domain.service;

import com.example.shortener.scalable_shortener.config.AccessLogProperties;
import com.example.shortener.scalable_shortener.domain.entity.AccessLog;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogProperties accessLogProperties;
    private final List<AccessLogWriter> writers;
    private final Map<AccessLogProperties.Mode, AccessLogWriter> writersByMode =
            new EnumMap<>(AccessLogProperties.Mode.class);
    private AccessLogWriter selectedWriter;

    @PostConstruct
    void initialize() {
        for (AccessLogWriter writer : writers) {
            writersByMode.put(writer.mode(), writer);
        }

        selectedWriter = writersByMode.get(accessLogProperties.getMode());
        if (selectedWriter == null) {
            throw new IllegalStateException("No AccessLogWriter for mode: " + accessLogProperties.getMode());
        }
    }

    public void save(AccessLog accessLog) {
        selectedWriter.write(accessLog);
    }
}
