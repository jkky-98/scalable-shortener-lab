package com.example.shortener.scalable_shortener.domain.repository;

import com.example.shortener.scalable_shortener.domain.entity.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {
}
