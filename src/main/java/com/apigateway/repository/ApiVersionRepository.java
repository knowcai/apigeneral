package com.apigateway.repository;

import com.apigateway.entity.ApiVersion;
import com.apigateway.entity.PublishStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiVersionRepository extends JpaRepository<ApiVersion, Long> {

    List<ApiVersion> findByApiIdOrderByVersionNoDesc(Long apiId);

    List<ApiVersion> findByApiIdAndStatus(Long apiId, PublishStatus status);

    Optional<ApiVersion> findByApiIdAndVersionNo(Long apiId, Integer versionNo);

    Optional<ApiVersion> findFirstByApiIdAndStatusOrderByVersionNoDesc(Long apiId, PublishStatus status);
}
