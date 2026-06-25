package com.apigateway.service;

import com.apigateway.service.distributed.InFlightCounterPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 跟踪每个 apiCode 正在处理的动态 API 请求数，用于发布前校验。 */
@Component
@RequiredArgsConstructor
public class InFlightRequestTracker {

    private final InFlightCounterPort counter;

    public void begin(String apiCode) {
        counter.begin(apiCode);
    }

    public void end(String apiCode) {
        counter.end(apiCode);
    }

    public boolean hasInFlight(String apiCode) {
        return counter.hasInFlight(apiCode);
    }

    public int count(String apiCode) {
        return counter.count(apiCode);
    }
}
