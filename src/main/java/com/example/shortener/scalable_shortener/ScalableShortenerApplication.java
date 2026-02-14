package com.example.shortener.scalable_shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class ScalableShortenerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScalableShortenerApplication.class, args);
	}

}
