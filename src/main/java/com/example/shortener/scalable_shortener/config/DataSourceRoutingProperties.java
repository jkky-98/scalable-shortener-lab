package com.example.shortener.scalable_shortener.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "shortener.datasource.routing")
public class DataSourceRoutingProperties {

    private boolean enabled = false;
    private Endpoint primary = new Endpoint();
    private Endpoint replica = new Endpoint();

    @Getter
    @Setter
    public static class Endpoint {
        private String url;
        private String username = "root";
        private String password = "root";
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeout = 30000;
    }
}
