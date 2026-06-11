package com.apigateway.repository;

import com.apigateway.entity.Datasource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasourceRepository extends JpaRepository<Datasource, Long> {
}
