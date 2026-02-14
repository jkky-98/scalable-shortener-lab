package com.example.shortener.scalable_shortener.utils;

import org.springframework.stereotype.Component;

@Component
public class Base62 {
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public String encode(long value) {
        // 0일 경우 처리
        if (value == 0) return String.valueOf(BASE62.charAt(0));

        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(BASE62.charAt((int) (value % 62)));
            value /= 62;
        }
        return sb.reverse().toString();
    }
}
