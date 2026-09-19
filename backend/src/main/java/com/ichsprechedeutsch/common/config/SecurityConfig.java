package com.ichsprechedeutsch.common.config;

import com.ichsprechedeutsch.common.security.JsonAuthErrorHandlers;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API tamamen stateless'tir: oturum tutulmaz, her istek Supabase'in
 * verdigi JWT ile dogrulanir. Imza dogrulamasi JWKS ucundan yapilir
 * (application.yml -> spring.security.oauth2.resourceserver.jwt.jwk-set-uri).
 *
 * Kullanici kimligi HER ZAMAN token'dan okunur, istek govdesinden degil.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final List<String> allowedOrigins;
    private final JsonAuthErrorHandlers authErrorHandlers;

    public SecurityConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
                          JsonAuthErrorHandlers authErrorHandlers) {
        this.allowedOrigins = allowedOrigins;
        this.authErrorHandlers = authErrorHandlers;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Tarayicida cerez kullanmiyoruz; CSRF yuzeyi yok.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/health", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> {})
                        .authenticationEntryPoint(authErrorHandlers))
                // Zincirin geri kalani icin de ayni JSON bicimi.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authErrorHandlers)
                        .accessDeniedHandler(authErrorHandlers))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
