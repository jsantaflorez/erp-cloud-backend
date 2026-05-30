package com.erp.erp_cloud.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_COMPANY_ID = "companyId";
    private static final String CLAIM_AUTHORITIES = "authorities";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final int MIN_SECRET_KEY_BYTES = 32;

    private final SecretKey key;
    private final long jwtExpirationInMs;

    /**
     * Constructor designed with Fail-Fast approach.
     * No default secret is provided to prevent unsafe silent deployments in production environments.
     */
    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration-ms:86400000}") long jwtExpirationInMs) {

        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);

        // Explicitly validates the key length at startup to avoid runtime WeakKeyExceptions
        if (keyBytes.length < MIN_SECRET_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "JWT secret key must be at least 32 bytes (256 bits) long. Current size: " + keyBytes.length + " bytes.");
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtExpirationInMs = jwtExpirationInMs;
    }

    /**
     * Generates an access token including explicit token mapping types to prevent token substitution attacks.
     */
    public String generateToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        List<String> authorities = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Using JJWT 0.12.x builder API implementation pattern
        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS) // Prepares infrastructure for future Refresh Tokens
                .claim(CLAIM_USER_ID, userPrincipal.getId())
                .claim(CLAIM_COMPANY_ID, userPrincipal.getCompanyId())
                .claim(CLAIM_AUTHORITIES, authorities)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS256) // Updated to modern non-deprecated signature enumeration
                .compact();
    }

    /**
     * Centralized token parsing method to execute cryptographic validation only once per execution thread.
     * Throws JwtException subclasses downstream to be handled by security filters.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates the token and extracts its payload in a single operation.
     * Exceptions are thrown downstream to be specifically handled by the calling security filters.
     */
    @SuppressWarnings("unchecked")
    public UserPrincipalClaims extractValidClaims(String token) throws JwtException {
        Claims claims = parseClaims(token);

        return new UserPrincipalClaims(
                claims.getSubject(),
                claims.get(CLAIM_USER_ID, Long.class),
                claims.get(CLAIM_COMPANY_ID, Long.class),
                claims.get(CLAIM_AUTHORITIES, List.class)
        );
    }

    /**
     * Validates the integrity, signature, and expiration status of the token with enhanced context logs.
     * Note: Keep this if required by legacy entry points, otherwise extractValidClaims is preferred.
     */
    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(authToken);
            return true;
        } catch (SignatureException ex) {
            log.error("Invalid JWT signature structure.");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token structure format: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token has expired. Subject: {}, Expired at: {}",
                    ex.getClaims().getSubject(),
                    ex.getClaims().getExpiration());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token format.");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty or null.");
        }
        return false;
    }

    /**
     * Exposes the internal cryptographic expiration lifespan configuration setting.
     */
    public long getJwtExpirationInMs() {
        return this.jwtExpirationInMs;
    }
}