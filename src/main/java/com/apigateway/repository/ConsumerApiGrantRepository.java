package com.apigateway.repository;

import com.apigateway.entity.ConsumerApiGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConsumerApiGrantRepository extends JpaRepository<ConsumerApiGrant, ConsumerApiGrant.Pk> {

    List<ConsumerApiGrant> findByConsumerId(Long consumerId);

    void deleteByConsumerId(Long consumerId);

    @Query("select g.apiId from ConsumerApiGrant g where g.consumerId = :consumerId")
    List<Long> findApiIdsByConsumerId(Long consumerId);

    boolean existsByConsumerIdAndApiId(Long consumerId, Long apiId);
}
