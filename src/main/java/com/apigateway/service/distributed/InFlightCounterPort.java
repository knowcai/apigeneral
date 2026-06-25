package com.apigateway.service.distributed;

public interface InFlightCounterPort {

    void begin(String apiCode);

    void end(String apiCode);

    boolean hasInFlight(String apiCode);

    int count(String apiCode);
}
