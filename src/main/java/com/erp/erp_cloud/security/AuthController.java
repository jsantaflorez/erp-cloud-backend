package com.erp.erp_cloud.security;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.auth.AuthResponse;
import com.erp.erp_cloud.dto.auth.CompanyOptionDTO;
import com.erp.erp_cloud.dto.auth.EmailLookupRequest;
import com.erp.erp_cloud.dto.auth.LoginRequest;
import com.erp.erp_cloud.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    /**
     * Public endpoint to validate user credentials and issue secure short-lived
     * multi-tenant JWT access tokens.
     *
     * TODO (SECURITY-RATE-LIMITING): Implement a defensive rate limiting mechanism
     * before production deployment. Target: max 5 attempts per IP/min, max 10
     * attempts per account/hour (e.g., Bucket4j).
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest) {

        log.info("AUTH_ATTEMPT | tenant: {} | identityHash: {}",
                loginRequest.companyId(),
                loginRequest.email().hashCode());

        // Combines email and companyId into a unified principal string --
        // CustomUserDetailsService splits this to enforce tenant isolation
        String unifiedPrincipal = loginRequest.email() + "|" + loginRequest.companyId();

        // Delegates full credential verification to the configured AuthenticationProvider chain.
        // On failure, BadCredentialsException propagates to GlobalExceptionHandler -> 401 JSON response.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(unifiedPrincipal, loginRequest.password())
        );

        // Reclaims the rich authenticated principal from the security thread context
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        // Generates the signed JWT token from the verified authentication context
        String jwt = tokenProvider.generateToken(authentication);

        // Extracts only high-level role names for frontend rendering constraints --
        // strips the internal ROLE_ framework prefix before sending to the client
        Set<String> roles = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .collect(Collectors.toSet());

        // Converts expiration from milliseconds to seconds for the frontend token scheduler
        long expiresInSeconds = tokenProvider.getJwtExpirationInMs() / 1000;

        // Builds the unified authentication response payload
        AuthResponse authResponse = new AuthResponse(
                jwt,
                "Bearer",
                userPrincipal.getUsername(),
                userPrincipal.getFullName(),   // derived from firstName + lastName in User entity
                userPrincipal.getCompanyId(),
                roles,
                expiresInSeconds
        );

        log.info("AUTH_SUCCESS | tenant: {} | identityHash: {} | roles: {}",
                userPrincipal.getCompanyId(),
                loginRequest.email().hashCode(),
                roles.size());

        return ResponseEntity.ok(
                ApiResponse.success("Authentication successful.", authResponse)
        );
    }

    /**
     * NEW (2026-10-28, Multiempresa Fase 2): PUBLIC pre-login lookup for the
     * Login screen's company dropdown. Deliberately returns ONLY the
     * companies the given email has an active UserRole for -- never a
     * global list of every company on the server (explicit product
     * decision: a user must only ever see their own options). Since this
     * runs before the password is checked, there is no JWT/SecurityContext
     * yet, which is exactly why it lives under /api/auth (permitAll,
     * skipped by JwtAuthenticationFilter/TenantFilter) rather than
     * /api/v1/session/companies (Fase 1's authenticated equivalent, which
     * answers "what can the ALREADY LOGGED IN user switch to").
     *
     * Returns an empty list (never a 404/error) for an unknown email, so
     * this alone can't be used to confirm whether an address is registered
     * -- same anti-enumeration spirit as /login's generic 401.
     *
     * TODO (SECURITY-RATE-LIMITING): same concern as /login -- this takes
     * arbitrary email input with no auth check, so it should share whatever
     * rate limiting /login eventually gets (see TODO above).
     */
    @PostMapping("/companies")
    public ResponseEntity<ApiResponse<List<CompanyOptionDTO>>> listCompaniesForLogin(
            @Valid @RequestBody EmailLookupRequest request) {

        String email = request.email().trim().toLowerCase();

        List<CompanyOptionDTO> companies = userRepository.findAccessibleCompaniesByEmail(email).stream()
                .map(c -> new CompanyOptionDTO(c.getId(), c.getLegalName(), c.getTradeName(), c.getTaxId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Companies retrieved successfully.", companies));
    }

}
