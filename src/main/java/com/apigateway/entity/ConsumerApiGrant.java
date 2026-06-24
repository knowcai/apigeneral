package com.apigateway.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Entity
@Table(name = "consumer_api_grant")
@IdClass(ConsumerApiGrant.Pk.class)
public class ConsumerApiGrant {

    @Id
    @Column(name = "consumer_id")
    private Long consumerId;

    @Id
    @Column(name = "api_id")
    private Long apiId;

    @Getter
    @Setter
    public static class Pk implements Serializable {
        private Long consumerId;
        private Long apiId;
    }
}
