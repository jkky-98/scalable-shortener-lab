package com.example.shortener.scalable_shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.example.shortener.scalable_shortener.domain.repository")
@EntityScan(basePackages = "com.example.shortener.scalable_shortener.domain.entity")
public class ScalableShortenerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScalableShortenerApplication.class, args);
	}

}
