package com.fintech.recon_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Log to verify this exact method is being called
        System.out.println("✅ SecurityConfig: Applying Dev-Friendly Rules...");

        http
            // 1. Disable CSRF (Critical for POST requests from Postman)
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. Disable CORS (Allows Postman/Browser to call from any origin)
            .cors(AbstractHttpConfigurer::disable)

            // 3. Permit All Requests
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )

            // 4. Handle H2 Console Frames
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
            );

        return http.build();
    }
}