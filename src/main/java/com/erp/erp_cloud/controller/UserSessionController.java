package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.auth.AuthResponse;
import com.erp.erp_cloud.dto.auth.CompanyOptionDTO;
import com.erp.erp_cloud.dto.auth.SwitchCompanyRequest;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.UserRepository;
import com.erp.erp_cloud.security.CustomUserDetailsService;
import com.erp.erp_cloud.security.JwtTokenProvider;
import com.erp.erp_cloud.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NEW (2026-10-26, Multiempresa Fase 1): the "hot-swap active company"
 * mechanism the frontend's AppHeader dropdown needs.
 *
 * Deliberately placed under /api/v1/session rather than /api/auth: both
 * JwtAuthenticationFilter and TenantFilter explicitly skip the whole
 * /api/auth/** prefix (see their own shouldNotFilter()), and SecurityConfig
 * marks it permitAll() -- so an endpoint that needs the CALLING user's
 * already-authenticated identity (both endpoints below do) cannot live
 * there, since SecurityContextHolder would be empty. /api/v1/... is the
 * existing convention for authenticated business endpoints (see
 * CompanyController) and IS processed normally by both filters.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/session")
@Tag(name = "Session", description = "Active-company switching for multi-tenant accountants")
@RequiredArgsConstructor
public class UserSessionController {

    private final UserRepository userRepository;
    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenProvider tokenProvider;

    /**
     * Lists every company the authenticated user can switch into (i.e. has
     * at least one UserRole for), for the AppHeader dropdown. Deliberately
     * minimal -- GET /api/v1/companies/me already covers "give me
     * everything about my CURRENT company"; this is just the switch list.
     */
    @GetMapping("/companies")
    @Operation(
            summary = "List companies the current user can switch into",
            description = "Every company where the authenticated user holds at least one active role assignment."
    )
    public ResponseEntity<ApiResponse<List<CompanyOptionDTO>>> listMyCompanies() {
        String email = currentEmail();

        List<CompanyOptionDTO> companies = userRepository.findAccessibleCompaniesByEmail(email).stream()
                .map(c -> new CompanyOptionDTO(c.getId(), c.getLegalName(), c.getTradeName(), c.getTaxId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Companies retrieved successfully.", companies));
    }

    /**
     * Re-validates the authenticated user's access to the requested company
     * (same checks as login: active user, not locked, has a UserRole there)
     * and, if it passes, issues a brand-new JWT scoped to that company --
     * exactly as if the user had logged in directly against it. The
     * frontend swaps its stored token for this one and the whole session
     * (TenantContext, every subsequent request) re-scopes immediately.
     *
     * Deliberately bypasses AuthenticationManager/password check: the user
     * is already authenticated (this endpoint itself requires a valid JWT)
     * -- this is a re-scope, not a fresh login.
     */
    @PostMapping("/switch-company")
    @Operation(
            summary = "Switch the active company and issue a new JWT",
            description = "Validates the authenticated user's access to the target company and returns a new token scoped to it."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> switchCompany(
            @Valid @RequestBody SwitchCompanyRequest request) {

        String email = currentEmail();
        Long targetCompanyId = request.companyId();

        UserPrincipal targetPrincipal;
        try {
            targetPrincipal = (UserPrincipal) userDetailsService
                    .loadUserByUsername(email + "|" + targetCompanyId);
        } catch (UsernameNotFoundException ex) {
            log.warn("SWITCH_COMPANY_DENIED | reason: NO_ACCESS | identityHash: {} | tenant: {}",
                    email.hashCode(), targetCompanyId);
            throw new InvalidOperationException(
                    "No tiene acceso a la compania seleccionada.", "NO_ACCESS_TO_COMPANY");
        } catch (LockedException ex) {
            log.warn("SWITCH_COMPANY_DENIED | reason: ACCOUNT_LOCKED | identityHash: {}", email.hashCode());
            throw new InvalidOperationException(
                    "La cuenta esta temporalmente bloqueada.", "ACCOUNT_LOCKED");
        }

        Authentication newAuthentication = new UsernamePasswordAuthenticationToken(
                targetPrincipal, null, targetPrincipal.getAuthorities());

        String jwt = tokenProvider.generateToken(newAuthentication);

        Set<String> roles = targetPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .collect(Collectors.toSet());

        long expiresInSeconds = tokenProvider.getJwtExpirationInMs() / 1000;

        AuthResponse authResponse = new AuthResponse(
                jwt,
                "Bearer",
                targetPrincipal.getUsername(),
                targetPrincipal.getFullName(),
                targetPrincipal.getCompanyId(),
                roles,
                expiresInSeconds
        );

        log.info("SWITCH_COMPANY_SUCCESS | identityHash: {} | tenant: {} | roles: {}",
                email.hashCode(), targetCompanyId, roles.size());

        return ResponseEntity.ok(ApiResponse.success("Company switched successfully.", authResponse));
    }

    private String currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return principal.getUsername();
    }
}
