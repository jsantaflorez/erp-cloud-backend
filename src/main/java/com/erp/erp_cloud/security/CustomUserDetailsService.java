package com.erp.erp_cloud.security;

import com.erp.erp_cloud.entity.User;
import com.erp.erp_cloud.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Locates the user identity context based on a unified multi-tenant principal string.
     * Expects the format: "email|companyId"
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String unifiedPrincipal) throws UsernameNotFoundException {

        // 1. Validates and parses the combined multi-tenant parameter structure
        if (unifiedPrincipal == null || !unifiedPrincipal.contains("|")) {
            log.warn("AUTH_FAIL | reason: MALFORMED_PRINCIPAL");
            throw new UsernameNotFoundException("Invalid identity credentials format context.");
        }

        String[] parts = unifiedPrincipal.split("\\|");
        if (parts.length != 2) {
            log.warn("AUTH_FAIL | reason: PRINCIPAL_SPLIT_MISMATCH");
            throw new UsernameNotFoundException("Invalid identity credentials format context.");
        }

        String email = parts[0];
        Long companyId;

        try {
            companyId = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            log.warn("AUTH_FAIL | reason: INVALID_COMPANY_ID_FORMAT");
            throw new UsernameNotFoundException("Invalid identity credentials format context.");
        }

        log.debug("AUTH_ATTEMPT | identityHash: {} | tenant: {}", email.hashCode(), companyId);

        // 2. Single database round-trip: User + UserRoles + Roles + Permissions
        User user = userRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> {
                    log.warn("AUTH_FAIL | reason: USER_NOT_FOUND | identityHash: {}", email.hashCode());
                    return new UsernameNotFoundException("Unauthorized identity criteria match rejected.");
                });

        // 3. Defensive security state checks
        if (!user.isActive()) {
            log.warn("AUTH_FAIL | reason: USER_INACTIVE | identityHash: {}", email.hashCode());
            throw new UsernameNotFoundException("Unauthorized identity criteria match rejected.");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            log.warn("AUTH_FAIL | reason: ACCOUNT_LOCKED | identityHash: {} | lockedUntil: {}",
                    email.hashCode(), user.getLockedUntil());
            throw new LockedException("Unauthorized identity criteria match rejected.");
        }

        // 4. Extract roles and permissions from the fetched graph,
        //    strictly scoped to the target companyId — all in memory, zero extra queries
        Set<String> roleCodes = new HashSet<>();
        Set<String> permissionCodes = new HashSet<>();

        user.getUserRoles().stream()
                .filter(ur -> ur.getCompany().getId().equals(companyId))
                .forEach(ur -> {
                    roleCodes.add("ROLE_" + ur.getRole().getCode());
                    ur.getRole().getPermissions()
                            .forEach(p -> permissionCodes.add(p.getCode()));
                });

        // 5. Multi-tenant barrier: user exists but has no role in the target company
        if (roleCodes.isEmpty()) {
            log.warn("AUTH_FAIL | reason: NO_COMPANY_ROLE | identityHash: {} | tenant: {}",
                    email.hashCode(), companyId);
            throw new UsernameNotFoundException("Unauthorized identity criteria match rejected.");
        }

        log.debug("AUTH_SUCCESS | identityHash: {} | tenant: {} | roles: {} | permissions: {}",
                email.hashCode(), companyId, roleCodes.size(), permissionCodes.size());

        // 6. Returns the finalized principal populated from real persistent database data
        return new UserPrincipal(user, companyId, roleCodes, permissionCodes);
    }
}