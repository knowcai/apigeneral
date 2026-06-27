package com.apigateway.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "theme_api_key_pickup")
public class ThemeApiKeyPickup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "theme_id", nullable = false)
    private Long themeId;

    @Column(name = "consumer_id", nullable = false, unique = true)
    private Long consumerId;

    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "encrypted_key", nullable = false, length = 512)
    private String encryptedKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
