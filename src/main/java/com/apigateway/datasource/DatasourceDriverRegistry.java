package com.apigateway.datasource;

import com.apigateway.entity.DatasourceType;
import com.apigateway.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DatasourceDriverRegistry {

    private final Map<DatasourceType, DatasourceDriver> drivers;

    public DatasourceDriverRegistry(List<DatasourceDriver> driverList) {
        Map<DatasourceType, DatasourceDriver> map = new EnumMap<>(DatasourceType.class);
        for (DatasourceDriver driver : driverList) {
            if (map.put(driver.type(), driver) != null) {
                throw new IllegalStateException("重复的数据源驱动: " + driver.type());
            }
        }
        this.drivers = Map.copyOf(map);
    }

    public DatasourceDriver require(DatasourceType type) {
        DatasourceDriver driver = drivers.get(type);
        if (driver == null) {
            throw new BusinessException("不支持的数据源类型: " + type);
        }
        return driver;
    }

    public DatasourceDriver require(String typeName) {
        try {
            return require(DatasourceType.valueOf(typeName.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不支持的数据源类型: " + typeName);
        }
    }

    public List<Map<String, String>> listSupported() {
        return drivers.values().stream()
                .sorted(Comparator.comparing(d -> d.type().name()))
                .map(d -> Map.of(
                        "type", d.type().name(),
                        "displayName", d.displayName()))
                .toList();
    }
}
