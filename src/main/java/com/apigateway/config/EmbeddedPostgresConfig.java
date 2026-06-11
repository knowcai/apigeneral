package com.apigateway.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;

@Configuration
@Profile("dev")
public class EmbeddedPostgresConfig {

    private EmbeddedPostgres embeddedPostgres;

    @Bean
    @Primary
    public DataSource dataSource() throws IOException {
        // 避免 DriverManager 加载 ClickHouse/MySQL 等驱动导致嵌入式 PG 启动失败
        System.setProperty("jdbc.drivers", "org.postgresql.Driver");
        embeddedPostgres = EmbeddedPostgres.builder()
                .setPort(15432)
                .start();
        return embeddedPostgres.getPostgresDatabase();
    }

    @PreDestroy
    public void stop() throws IOException {
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
    }
}
