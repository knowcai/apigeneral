package com.apigateway.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Entity
@Table(name = "theme_membership")
@IdClass(ThemeMembership.Pk.class)
public class ThemeMembership {

    @Id
    @Column(name = "theme_id")
    private Long themeId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ThemeMembershipRole role;

    @Getter
    @Setter
    public static class Pk implements Serializable {
        private Long themeId;
        private Long userId;
    }
}
