CREATE TABLE gateway_policy (
    id                      BIGINT PRIMARY KEY DEFAULT 1,
    global_qps_enabled      BOOLEAN   NOT NULL DEFAULT TRUE,
    global_qps              INTEGER   NOT NULL DEFAULT 1000,
    ip_qps_enabled          BOOLEAN   NOT NULL DEFAULT TRUE,
    ip_qps                  INTEGER   NOT NULL DEFAULT 100,
    api_qps_enabled         BOOLEAN   NOT NULL DEFAULT TRUE,
    api_qps                 INTEGER   NOT NULL DEFAULT 50,
    circuit_enabled         BOOLEAN   NOT NULL DEFAULT TRUE,
    circuit_failure_rate    INTEGER   NOT NULL DEFAULT 50,
    circuit_min_calls       INTEGER   NOT NULL DEFAULT 20,
    circuit_wait_sec        INTEGER   NOT NULL DEFAULT 30,
    circuit_fallback        TEXT      NOT NULL DEFAULT '{"code":503,"message":"服务熔断中，请稍后重试","data":null}',
    retry_enabled           BOOLEAN   NOT NULL DEFAULT TRUE,
    retry_max_attempts      INTEGER   NOT NULL DEFAULT 2,
    retry_interval_ms       INTEGER   NOT NULL DEFAULT 500,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT gateway_policy_singleton CHECK (id = 1)
);

INSERT INTO gateway_policy (id) VALUES (1);
