package com.apigateway.service.distributed;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayDeque;
import java.util.Deque;

@Getter
@Setter
public class CircuitBreakerSnapshot {

    public enum Status { CLOSED, OPEN, HALF_OPEN }

    private Status status = Status.CLOSED;
    private long openUntilMs;
    private Deque<CallRecord> recentCalls = new ArrayDeque<>();

    public record CallRecord(long timestampMs, boolean success) {}
}
