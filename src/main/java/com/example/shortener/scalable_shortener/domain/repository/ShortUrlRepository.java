package com.example.shortener.scalable_shortener.domain.repository;

import com.example.shortener.scalable_shortener.domain.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {
    Optional<ShortUrl> findByShortKey(String shortKey);
}
