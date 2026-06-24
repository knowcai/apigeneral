package com.apigateway.security;

import com.apigateway.entity.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ApiConsumerContext {

    public static final String REQUEST_ATTR = "apiConsumer";

    private final Long id;
    private final String name;

    public static ApiConsumerContext from(Consumer consumer) {
        return new ApiConsumerContext(consumer.getId(), consumer.getName());
    }
}
