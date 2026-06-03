package com.example.shortener.scalable_shortener.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "shortener.datasource.routing", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DataSourceRoutingProperties.class)
public class ReadWriteDataSourceConfig {

    @Bean
    @Primary
    public DataSource routingDataSource(DataSourceRoutingProperties properties) {
        ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource();
        DataSource primaryDataSource = hikariDataSource("primary", properties.getPrimary());
        DataSource replicaDataSource = hikariDataSource("replica", properties.getReplica());

        routingDataSource.setTargetDataSources(Map.of(
                ReadWriteRoutingDataSource.PRIMARY, primaryDataSource,
                ReadWriteRoutingDataSource.REPLICA, replicaDataSource
        ));
        routingDataSource.setDefaultTargetDataSource(primaryDataSource);
        routingDataSource.afterPropertiesSet();

        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    private HikariDataSource hikariDataSource(String poolName, DataSourceRoutingProperties.Endpoint endpoint) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("shortener-" + poolName);
        dataSource.setJdbcUrl(endpoint.getUrl());
        dataSource.setUsername(endpoint.getUsername());
        dataSource.setPassword(endpoint.getPassword());
        dataSource.setDriverClassName(endpoint.getDriverClassName());
        dataSource.setMaximumPoolSize(endpoint.getMaximumPoolSize());
        dataSource.setMinimumIdle(endpoint.getMinimumIdle());
        dataSource.setConnectionTimeout(endpoint.getConnectionTimeout());
        return dataSource;
    }
}
