package com.example.shortener.scalable_shortener.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "access_logs")
public class AccessLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String shortKey;
	private LocalDateTime accessedAt;
	private String ipAddress;
	private String userAgent;
	private String referer;

	public AccessLog(String shortKey, String ipAddress, String userAgent, String referer) {
		this.shortKey = shortKey;
		this.ipAddress = ipAddress;
		this.userAgent = userAgent;
		this.referer = referer;
		this.accessedAt = LocalDateTime.now();
	}
}