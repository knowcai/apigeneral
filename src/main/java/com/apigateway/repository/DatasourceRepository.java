package com.apigateway.repository;

import com.apigateway.entity.Datasource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatasourceRepository extends JpaRepository<Datasource, Long> {

    List<Datasource> findByThemeIdIn(List<Long> themeIds);
}
