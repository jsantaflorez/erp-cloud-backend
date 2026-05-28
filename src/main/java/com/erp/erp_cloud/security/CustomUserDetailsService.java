package com.erp.erp_cloud.security;

import com.erp.erp_cloud.entity.User;
import com.erp.erp_cloud.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import javax.crypto.SecretKey;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Long contextCompanyId = resolveCompanyId(email);
        log.debug("Loading user: {} for companyId: {}", email, contextCompanyId);

        // 1. Fetch user using the optimized FETCH query to avoid N+1 issues
        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // 2. Extract role codes assigned to the user within the current tenant context
        Set<String> roleCodes = user.getUserRoles().stream()
                .filter(ur -> ur.getCompany().getId().equals(contextCompanyId))
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toSet());

        // 3. Extract standard permission string codes from database
        Set<String> permissionCodes = userRepository.findPermissionCodesByEmailAndCompanyId(email, contextCompanyId);

        log.debug("Roles loaded for {}: {}", email, roleCodes);
        log.debug("Permissions loaded for {}: {}", email, permissionCodes);

        // 4. Return fully populated principal for Spring Security validation engine
        return new UserPrincipal(user, contextCompanyId, roleCodes, permissionCodes);
    }

    /**
     * Resolves the current company ID context for the login request execution thread.
     */
    private Long resolveCompanyId(String email) {
        // Phase 1 (Current): Hardcoded fallback value matching local seed 'tenant-demo' (ID = 4)
        return 4L;

        // Phase 2 (JWT Implementation): Will resolve dynamically from Request Context / JWT claims
        // return TenantContext.getCurrentTenant();
    }
}