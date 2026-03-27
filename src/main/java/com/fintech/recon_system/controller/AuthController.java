package com.fintech.recon_system.controller;

import com.fintech.recon_system.security.jwt.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public AuthController(AuthenticationManager authenticationManager, JwtUtils jwtUtils) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateToken(authentication);

        // [DEBUG] Print to Docker logs to confirm login success
        System.out.println("✅ User authenticated: " + loginRequest.getUsername());

        // Create Cookie with maximum compatibility for HTTP/IP access
        Cookie cookie = new Cookie("jwt_token", jwt);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // MUST be false for HTTP
        cookie.setPath("/");
        cookie.setMaxAge(86400); // 1 day
        
        // Manual header for dual-layer assurance
        response.addCookie(cookie);
        response.setHeader("Set-Cookie", "jwt_token=" + jwt + "; Path=/; Max-Age=86400; HttpOnly");

        return ResponseEntity.ok(new JwtResponse(jwt));
    }

    // --- Data Transfer Objects (DTOs) ---
    
    static class LoginRequest { 
        private String username; 
        private String password; 
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    static class JwtResponse { 
        private final String token; 
        private final String type = "Bearer"; 
        public JwtResponse(String token) { this.token = token; }
        public String getToken() { return token; }
        public String getType() { return type; }
    }
}