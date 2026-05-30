package com.erp.erp_cloud.security;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    /**
     * Public endpoint to validate user credentials and issue secure short-lived multi-tenant JWT access tokens.
     * * TODO (SECURITY-RATE-LIMITING): Implement a defensive rate limiting mechanism before production deployment.
     * Target metrics: Max 5 attempts per IP per minute, Max 10 attempts per account identity string per hour (e.g., using Bucket4j).
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        // Protects user privacy by leveraging hash identifiers instead of exposing raw PII string metrics in standard logs
        log.info("Processing authentication request for tenant/companyId: {} | identityHash: {}",
                loginRequest.companyId(), loginRequest.email().hashCode());

        // Combines username and tenant context into a unified principal string to support stateless multi-tenancy
        String unifiedPrincipal = loginRequest.email() + "|" + loginRequest.companyId();

        // Standard Spring Security authentication mechanism invocation token wrapper
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                unifiedPrincipal,
                loginRequest.password()
        );

        try {
            // Delegates verification to the configured AuthenticationProvider chain
            Authentication authentication = authenticationManager.authenticate(authenticationToken);

            // Generates the signed cryptographic token utilizing our verified provider engine
            String jwt = tokenProvider.generateToken(authentication);

            // Reclaims the rich authenticated context details from the security thread principal
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

            // Safe mapping extraction of high-level functional groups (Roles) for frontend rendering constraints
            List<String> roles = userPrincipal.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .map(auth -> auth.substring(5)) // Strips out the 'ROLE_' internal framework marker prefix
                    .collect(Collectors.toList());

            // Builds the rich payload user summary data holder structure
            JwtResponse.UserSummary userSummary = new JwtResponse.UserSummary(
                    userPrincipal.getId(),
                    userPrincipal.getUsername(),
                    // TODO (ERP-PROFILE): Load fullName dynamic mapping from UserProfile extensions once the domain entity is implemented
                    null,
                    userPrincipal.getCompanyId(), // Extracted directly from validated context, not the raw request
                    roles
            );

            // Calculates exact token validation lifetime window margins converting milliseconds to seconds via provider state
            long expiresInSeconds = tokenProvider.getJwtExpirationInMs() / 1000;

            log.info("Identity context successfully authenticated under tenant context: {}. JWT token successfully generated.",
                    userSummary.companyId());

            return ResponseEntity.ok(new JwtResponse(jwt, expiresInSeconds, userSummary));

        } catch (BadCredentialsException ex) {
            // Explicit defense mechanism against internal framework disclosure leaks or unhandled raw stack exceptions
            log.warn("Failed authentication attempt recorded for tenant/companyId: {} | Reason: Bad credentials structure matrix.",
                    loginRequest.companyId());

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}