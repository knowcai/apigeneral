package com.apigateway.config;

import com.apigateway.service.distributed.CircuitBreakerStorePort;
import com.apigateway.service.distributed.InFlightCounterPort;
import com.apigateway.service.distributed.RateLimiterPort;
import com.apigateway.service.distributed.RedisCircuitBreakerStore;
import com.apigateway.service.distributed.RedisInFlightCounter;
import com.apigateway.service.distributed.RedisRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(name = "gateway.redis.enabled", havingValue = "true")
public class RedisDistributedConfig {

    @Bean
    LettuceConnectionFactory redisConnectionFactory(GatewayRedisProperties props) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(props.getHost(), props.getPort());
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            config.setPassword(props.getPassword());
        }
        return new LettuceConnectionFactory(config);
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    RateLimiterPort redisRateLimiter(StringRedisTemplate redis) {
        return new RedisRateLimiter(redis);
    }

    @Bean
    InFlightCounterPort redisInFlightCounter(StringRedisTemplate redis) {
        return new RedisInFlightCounter(redis);
    }

    @Bean
    CircuitBreakerStorePort redisCircuitBreakerStore(StringRedisTemplate redis) {
        return new RedisCircuitBreakerStore(redis);
    }
}
