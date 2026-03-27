package com.fintech.recon_system.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for JWT generation and validation.
 * Optimized for HS512 algorithm with sufficient key length and proper character handling.
 */
@Component
public class JwtUtils {

    /**
     * HS512 requires a key of at least 512 bits (64 bytes). 
     * This string is manually extended to ensure it meets that security threshold.
     */
    private final String jwtSecret = "FintechReconSystem_Secure_Secret_Key_2026_Must_Exceed_64_Characters_For_HS512_Algorithm_Validation_!";
    private final int jwtExpirationMs = 86400000; // 24 hours

    /**
     * Converts the raw string secret into a secure Key object.
     * Uses StandardCharsets.UTF_8 to avoid character encoding issues and bypass Base64 decoding bugs.
     */
    private Key getSigningKey() {
        byte[] keyBytes = this.jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a secure JWT token for an authenticated user.
     */
    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512) // Uses secure Key object
                .compact();
    }

    /**
     * Parses the JWT and extracts the username.
     */
    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     * Validates the token's signature, integrity, and expiration.
     */
    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(authToken);
            return true;
        } catch (SignatureException e) {
            System.err.println("Invalid JWT signature: " + e.getMessage());
        } catch (MalformedJwtException e) {
            System.err.println("Invalid JWT token: " + e.getMessage());
        } catch (ExpiredJwtException e) {
            System.err.println("JWT token is expired: " + e.getMessage());
        } catch (UnsupportedJwtException e) {
            System.err.println("JWT token is unsupported: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("JWT claims string is empty: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("JWT error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        return false;
    }
}