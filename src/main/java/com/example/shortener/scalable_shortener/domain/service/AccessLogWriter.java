package com.example.shortener.scalable_shortener.domain.service;

import com.example.shortener.scalable_shortener.config.AccessLogProperties;
import com.example.shortener.scalable_shortener.domain.entity.AccessLog;

public interface AccessLogWriter {

    AccessLogProperties.Mode mode();

    void write(AccessLog accessLog);
}
