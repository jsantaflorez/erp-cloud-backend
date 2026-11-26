package com.erp.erp_cloud.security;

import com.erp.erp_cloud.entity.User;
import com.erp.erp_cloud.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B1 (2026-11-25): el correo solo (paso 1 del login) carga al usuario para
 * verificar la contraseña, sin compañía ni permisos.
 */
class CustomUserDetailsServicePreAuthTest {

    @Mock private UserRepository userRepository;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CustomUserDetailsService(userRepository);
    }

    private static User user(boolean active) {
        User u = new User();
        u.setId(10L);
        u.setEmail("admin.navarra@erpcloud.com");
        u.setPasswordHash("hash");
        u.setActive(active);
        return u;
    }

    @Test
    @DisplayName("Correo solo: principal sin compañía ni autoridades")
    void emailOnly_loadsUserWithoutCompany() {
        when(userRepository.findByEmail("admin.navarra@erpcloud.com")).thenReturn(Optional.of(user(true)));

        UserDetails details = service.loadUserByUsername("admin.navarra@erpcloud.com");

        assertThat(details).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) details).getCompanyId()).isNull();
        assertThat(details.getAuthorities()).isEmpty();
        assertThat(details.getPassword()).isEqualTo("hash");
        verify(userRepository, never()).findByEmailWithRolesAndPermissionsForCompany(any(), any());
    }

    @Test
    @DisplayName("Correo solo: desconocido o inactivo -> UsernameNotFoundException")
    void emailOnly_unknownOrInactive() {
        when(userRepository.findByEmail("nadie@x.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("inactivo@x.com")).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> service.loadUserByUsername("nadie@x.com")).isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> service.loadUserByUsername("inactivo@x.com")).isInstanceOf(UsernameNotFoundException.class);
    }
}
