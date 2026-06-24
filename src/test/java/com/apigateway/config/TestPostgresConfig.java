package com.apigateway.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;

@Configuration
@Profile("test")
public class TestPostgresConfig {

    private static final Object LOCK = new Object();
    private static EmbeddedPostgres sharedPostgres;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (LOCK) {
                if (sharedPostgres != null) {
                    try {
                        sharedPostgres.close();
                    } catch (IOException ignored) {
                    }
                    sharedPostgres = null;
                }
            }
        }));
    }

    @Bean
    @Primary
    public DataSource dataSource() throws IOException {
        synchronized (LOCK) {
            if (sharedPostgres == null) {
                System.setProperty("jdbc.drivers", "org.postgresql.Driver");
                sharedPostgres = EmbeddedPostgres.builder()
                        .setLocaleConfig("locale", "C")
                        .setLocaleConfig("lc-collate", "C")
                        .setLocaleConfig("lc-ctype", "C")
                        .setLocaleConfig("lc-messages", "en_US")
                        .start();
            }
            return sharedPostgres.getPostgresDatabase();
        }
    }
}
