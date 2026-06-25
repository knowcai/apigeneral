package com.apigateway.datasource;

import com.apigateway.entity.DatasourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DatasourceDriverRegistryTest {

    @Autowired
    private DatasourceDriverRegistry registry;

    @Test
    void registersBuiltInDrivers() {
        assertNotNull(registry.require(DatasourceType.DORIS));
        assertNotNull(registry.require(DatasourceType.CLICKHOUSE));
        assertNotNull(registry.require(DatasourceType.POSTGRES));
        assertNotNull(registry.require(DatasourceType.TRINO));
        assertNotNull(registry.require(DatasourceType.STARROCKS));
        assertTrue(registry.listSupported().size() >= 5);
    }

    @Test
    void dorisAndStarRocksUseMysqlStylePaging() {
        String sql = "SELECT 1";
        assertEquals("SELECT 1 LIMIT 10 OFFSET 5",
                registry.require(DatasourceType.DORIS).wrapPagedSql(sql, 5, 10));
        assertEquals("SELECT 1 LIMIT 5, 10",
                registry.require(DatasourceType.CLICKHOUSE).wrapPagedSql(sql, 5, 10));
        assertEquals("SELECT 1 OFFSET 5 LIMIT 10",
                registry.require(DatasourceType.TRINO).wrapPagedSql(sql, 5, 10));
    }
}
