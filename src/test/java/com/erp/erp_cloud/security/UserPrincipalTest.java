package com.erp.erp_cloud.security;

import com.erp.erp_cloud.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-11-21: permission authorities must never look like roles. The ROLE
 * resource's own permissions are called ROLE_CREATE, ROLE_READ... and used to
 * show up as four extra roles ("ADMINISTRADOR +4" in the header).
 */
class UserPrincipalTest {

    @Test
    @DisplayName("Permissions get the PERM_ prefix, so only real roles start with ROLE_")
    void permissionsDoNotLookLikeRoles() {
        User user = new User();
        user.setId(1L);
        user.setEmail("admin.navarra@erpcloud.com");
        user.setFirstName("Admin");
        user.setLastName("Navarra");
        user.setActive(true);

        UserPrincipal principal = new UserPrincipal(user, 5L,
                Set.of("ROLE_COMPANY_ADMIN"),
                Set.of("ROLE_CREATE", "ROLE_READ", "ROLE_UPDATE", "ROLE_DELETE", "JOURNAL_ENTRY_READ"));

        Set<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        Set<String> roles = authorities.stream()
                .filter(a -> a.startsWith("ROLE_"))
                .collect(Collectors.toSet());

        assertThat(roles).containsExactly("ROLE_COMPANY_ADMIN");
        assertThat(authorities).contains("PERM_ROLE_CREATE", "PERM_JOURNAL_ENTRY_READ");
    }
}
