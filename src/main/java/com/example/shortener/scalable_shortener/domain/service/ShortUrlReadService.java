package com.example.shortener.scalable_shortener.domain.service;

import com.example.shortener.scalable_shortener.domain.entity.ShortUrl;
import com.example.shortener.scalable_shortener.domain.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShortUrlReadService {

    private final ShortUrlRepository shortUrlRepository;

    @Transactional(readOnly = true)
    public Optional<String> findOriginalUrl(String shortKey) {
        return shortUrlRepository.findByShortKey(shortKey)
                .map(ShortUrl::getOriginalUrl);
    }
}
