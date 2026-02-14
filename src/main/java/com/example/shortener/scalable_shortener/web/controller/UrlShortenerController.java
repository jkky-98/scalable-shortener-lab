package com.example.shortener.scalable_shortener.web.controller;

import com.example.shortener.scalable_shortener.domain.entity.AccessLog;
import com.example.shortener.scalable_shortener.domain.entity.ShortUrl;
import com.example.shortener.scalable_shortener.domain.repository.AccessLogRepository;
import com.example.shortener.scalable_shortener.domain.repository.ShortUrlRepository;
import com.example.shortener.scalable_shortener.utils.Base62;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UrlShortenerController {

    private final ShortUrlRepository shortUrlRepository;
    private final AccessLogRepository accessLogRepository;
    private final Base62 base62;

    // [Mission 1] 헬스 체크 & IP 확인
    @GetMapping("/api/hello")
    public ResponseEntity<String> hello() {
        String ip = "Unknown";
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return ResponseEntity.ok("Alive! (Server IP: " + ip + ")");
    }

	// [Mission 2] URL 단축 (Write)
    @PostMapping("/api/shorten")
    @Transactional
    public ResponseEntity<Map<String, String>> shortenUrl(@RequestBody Map<String, String> request) {
        String originalUrl = request.get("url");

        // 1. 먼저 ID만 생성해서 DB에 넣습니다. (short_key는 일단 null이나 빈값)
        ShortUrl entity = new ShortUrl(originalUrl);
        shortUrlRepository.saveAndFlush(entity); // 즉시 DB에 반영해서 ID 확정

        // 2. 확정된 ID로 Key를 만듭니다.
        String shortKey = base62.encode(entity.getId());

        // 3. Key를 업데이트합니다.
        entity.assignShortKey(shortKey);

        // 4. 명시적으로 한 번 더 저장 (flush는 트랜잭션 종료 시 자동 수행)
        log.info("Saved ID: {}, Generated Key: {}", entity.getId(), shortKey);

        return ResponseEntity.ok(Map.of("key", shortKey));
    }

    // [Mission 2] 리다이렉트 (Read + AccessLog Write)
    @GetMapping("/api/{key}")
    public ResponseEntity<?> redirect(@PathVariable String key, HttpServletRequest request) {
        ShortUrl shortUrl = shortUrlRepository.findByShortKey(key).orElse(null);

        if (shortUrl == null) {
            return ResponseEntity.notFound().build();
        }

        // 통계 저장 (동기 처리 - 성능 저하 원인)
        AccessLog log = new AccessLog(
                key,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("Referer")
        );
        accessLogRepository.save(log);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(shortUrl.getOriginalUrl()));
        return new ResponseEntity<>(headers, HttpStatus.FOUND); // 302 Found
    }
}
