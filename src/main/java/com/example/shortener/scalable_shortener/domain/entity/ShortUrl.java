package com.example.shortener.scalable_shortener.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "short_urls", indexes = @Index(name = "idx_short_key", columnList = "shortKey"))
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String originalUrl;

    @Column(unique = true, length = 10)
    private String shortKey;

    public ShortUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public void assignShortKey(String shortKey) {
        this.shortKey = shortKey;
    }
}