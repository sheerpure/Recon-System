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
 * Configures JWT-based stateless authentication and RBAC for sensitive operations.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * Password encoder bean used for hashing and validating passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Expose AuthenticationManager for AuthController to perform login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Configures the main security filter chain.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 1. Disable CSRF (Stateless JWT architecture)
        http.csrf(csrf -> csrf.disable())
            
            // 2. Set Session Management to Stateless
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 3. Define Authorization Rules
            .authorizeHttpRequests(auth -> auth
                // Allow public access to the landing page, login, and static assets
                .requestMatchers("/", "/login", "/api/auth/**", "/css/**", "/js/**", "/images/**", "/h2-console/**").permitAll()
                
                // Explicitly protect the high-risk transaction API with ADMIN role
                .requestMatchers("/api/transactions/**").hasRole("ADMIN")
                
                // Any other generic request requires simple authentication
                .anyRequest().authenticated()
            )

            // 4. Enable Frame Options for H2-Console (SameOrigin only)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))

            // 5. Intelligent Exception Handling
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    String uri = request.getRequestURI();
                    
                    if (uri.startsWith("/api/")) {
                        // Return 401 status code for AJAX calls to let JS handle the error
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized API Access");
                    } else {
                        // Redirect browser users to the login page for normal navigation
                        response.sendRedirect("/login");
                    }
                })
            );

        // 6. Add our custom JWT Filter before the standard UsernamePassword filter
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}