package com.apigateway.config;

import com.apigateway.security.ApiKeyAuthFilter;
import com.apigateway.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final @Lazy JwtAuthFilter jwtAuthFilter;
    private final @Lazy ApiKeyAuthFilter apiKeyAuthFilter;
    private final GatewaySecurityProperties securityProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未登录\",\"data\":null}");
                }))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/admin/auth/login").permitAll();
                    auth.requestMatchers("/api/data/**").permitAll();
                    if (securityProperties.isActuatorPublic()) {
                        auth.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                                .permitAll();
                    } else {
                        auth.requestMatchers("/actuator/**").authenticated();
                    }
                    if (securityProperties.isSwaggerPublic()) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .permitAll();
                    } else {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .authenticated();
                    }
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers("/admin/**").authenticated();
                    auth.anyRequest().permitAll();
                })
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
