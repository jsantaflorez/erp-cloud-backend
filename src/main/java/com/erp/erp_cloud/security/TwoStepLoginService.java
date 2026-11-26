package com.erp.erp_cloud.security;

import com.erp.erp_cloud.dto.auth.AuthResponse;
import com.erp.erp_cloud.dto.auth.CompanyOptionDTO;
import com.erp.erp_cloud.dto.auth.PreAuthResponse;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.LoginSessionExpiredException;
import com.erp.erp_cloud.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * B1 (auditoría 2026-11-19, F1-13; 2026-11-25): login en dos pasos.
 *
 * Antes, la pantalla de ingreso consultaba POST /api/auth/companies SIN
 * contraseña para llenar el selector de empresa: cualquiera podía averiguar
 * para qué empresas trabaja un correo (un directorio público de clientes).
 *
 * Paso 1 -- {@link #start}: correo + contraseña. Se verifica la contraseña
 * (mismo AuthenticationManager, mismo conteo de intentos fallidos y bloqueo)
 * y solo entonces se devuelven sus empresas, con nombre y NIT, junto con un
 * token de pre-sesión de vida corta (5 min) que no abre ninguna otra ruta.
 *
 * Paso 2 -- {@link #selectCompany}: con ese token y la empresa elegida se
 * emite el token de acceso normal, con las mismas validaciones que el login
 * de siempre (usuario activo, no bloqueado, con rol en esa empresa activa).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoStepLoginService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;
    private final CustomUserDetailsService userDetailsService;

    @Transactional(readOnly = true)
    public PreAuthResponse start(String rawEmail, String password) {
        String email = rawEmail.trim().toLowerCase();
        log.info("AUTH_STEP1_ATTEMPT | identityHash: {}", email.hashCode());

        Authentication authentication;
        try {
            // E-mail alone (no "|companyId"): CustomUserDetailsService loads the
            // user for the password check only. Unknown user, wrong password and
            // inactive user all end as the same generic INVALID_CREDENTIALS.
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
        } catch (BadCredentialsException ex) {
            loginAttemptService.recordFailure(email);
            throw ex;
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        loginAttemptService.recordSuccess(principal.getId());

        List<CompanyOptionDTO> companies = userRepository.findAccessibleCompaniesByEmail(email).stream()
                .map(c -> new CompanyOptionDTO(c.getId(), c.getLegalName(), c.getTradeName(), c.getTaxId()))
                .collect(Collectors.toList());

        if (companies.isEmpty()) {
            // Only reachable with the right password: telling the user why is fine.
            log.warn("AUTH_STEP1_DENIED | reason: NO_ACTIVE_COMPANIES | identityHash: {}", email.hashCode());
            throw new InvalidOperationException("The user has no active company.", "NO_ACTIVE_COMPANIES");
        }

        log.info("AUTH_STEP1_SUCCESS | identityHash: {} | companies: {}", email.hashCode(), companies.size());
        return new PreAuthResponse(
                tokenProvider.generatePreAuthToken(principal),
                tokenProvider.getPreAuthExpirationInMs() / 1000,
                principal.getUsername(),
                principal.getFullName(),
                companies);
    }

    @Transactional(readOnly = true)
    public AuthResponse selectCompany(String authorizationHeader, Long companyId) {
        PreAuthClaims claims = preAuthClaims(authorizationHeader);

        UserPrincipal target;
        try {
            target = (UserPrincipal) userDetailsService.loadUserByUsername(claims.email() + "|" + companyId);
        } catch (UsernameNotFoundException ex) {
            log.warn("AUTH_STEP2_DENIED | reason: NO_ACCESS | identityHash: {} | tenant: {}",
                    claims.email().hashCode(), companyId);
            throw new InvalidOperationException("No access to the selected company.", "NO_ACCESS_TO_COMPANY");
        }
        // The e-mail could have been given to another account in the 5 minutes
        // the pre-session token lives: it must be the same user.
        if (!target.getId().equals(claims.userId())) {
            throw new LoginSessionExpiredException();
        }
        if (!target.isAccountNonLocked()) {
            throw new LockedException("Account locked.");
        }
        if (!target.isEnabled()) {
            throw new DisabledException("Account disabled.");
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                target, null, target.getAuthorities());
        String jwt = tokenProvider.generateToken(authentication);

        Set<String> roles = target.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .collect(Collectors.toSet());

        log.info("AUTH_SUCCESS | tenant: {} | identityHash: {} | roles: {}",
                target.getCompanyId(), claims.email().hashCode(), roles.size());

        return new AuthResponse(
                jwt,
                "Bearer",
                target.getUsername(),
                target.getFullName(),
                target.getCompanyId(),
                roles,
                tokenProvider.getJwtExpirationInMs() / 1000);
    }

    private PreAuthClaims preAuthClaims(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new LoginSessionExpiredException();
        }
        try {
            PreAuthClaims claims = tokenProvider.extractPreAuthClaims(authorizationHeader.substring(BEARER_PREFIX.length()));
            if (claims.email() == null || claims.userId() == null) {
                throw new LoginSessionExpiredException();
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("AUTH_STEP2_DENIED | reason: INVALID_PRE_SESSION | {}", ex.getClass().getSimpleName());
            throw new LoginSessionExpiredException();
        }
    }
}
