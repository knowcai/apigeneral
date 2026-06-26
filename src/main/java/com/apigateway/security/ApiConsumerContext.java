package com.apigateway.security;

import com.apigateway.entity.Consumer;
import lombok.Getter;

@Getter
public class ApiConsumerContext {

    public static final String REQUEST_ATTR = "apiConsumer";

    private final Long id;
    private final String name;
    private final String authMode;

    public ApiConsumerContext(Long id, String name, String authMode) {
        this.id = id;
        this.name = name;
        this.authMode = authMode;
    }

    public static ApiConsumerContext from(Consumer consumer) {
        String mode = consumer.getThemeId() != null ? "THEME_KEY" : "LEGACY";
        return new ApiConsumerContext(consumer.getId(), consumer.getName(), mode);
    }
}
