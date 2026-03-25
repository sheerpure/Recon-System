package com.fintech.recon_system.security.config;

import com.fintech.recon_system.security.jwt.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig: The central security nerve center for the Recon-System.
 * This class defines how users are authenticated and which resources are protected.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * PasswordEncoder bean using BCrypt hashing algorithm.
     * Ensures all passwords stored in the database are salted and hashed for security.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager bean: Coordinates the authentication process.
     * Required by AuthController to verify user credentials during login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * SecurityFilterChain bean: Defines the security filter stack.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 1. Disable CSRF (Cross-Site Request Forgery) protection as we use Stateless JWTs
        http.csrf(csrf -> csrf.disable())
            
            // 2. Enforce Stateless Session Policy (No server-side sessions/JSESSIONID)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 3. Define Endpoint Access Control Rules
            .authorizeHttpRequests(auth -> auth
                /**
                 * ALLOW LIST:
                 * - "/" and "/login": Essential for the browser to load the entry pages.
                 * - "/api/auth/**": Used for the login/token generation endpoint.
                 * - "/css/**", "/js/**", "/images/**": Static assets needed for UI rendering.
                 */
                .requestMatchers("/", "/login", "/api/auth/**", "/css/**", "/js/**", "/images/**").permitAll()
                
                // RBAC (Role-Based Access Control): Only ADMINs can upload or perform batch audits
                .requestMatchers("/api/transactions/upload/**").hasRole("ADMIN")
                .requestMatchers("/api/transactions/batch-status").hasRole("ADMIN")
                
                // PROTECTED DATA: All other API calls require a valid JWT token
                .anyRequest().authenticated()
            )

            // 4. Custom Global Exception Handling
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    /**
                     * Intelligent Redirection:
                     * - If a Browser User hits a protected page without a token, redirect to /login.
                     * - If an API Client (like Thunder Client) fails auth, return 401 Unauthorized.
                     */
                    if (!request.getRequestURI().startsWith("/api/")) {
                        response.sendRedirect("/login");
                    } else {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                    }
                })
            );

        // 5. JWT Filter Injection: Apply our custom filter before the standard authentication filter
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}