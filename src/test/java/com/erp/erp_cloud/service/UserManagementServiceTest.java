package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChangePasswordRequest;
import com.erp.erp_cloud.entity.User;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.repository.RoleRepository;
import com.erp.erp_cloud.repository.UserRepository;
import com.erp.erp_cloud.repository.UserRoleRepository;
import com.erp.erp_cloud.security.UserPrincipal;
import com.erp.erp_cloud.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cross-tenant guards of user administration (audit 2026-11-02, C2 + A1).
 * A User is global; one company's admin must not change the identity of a
 * user that also works for other companies.
 */
class UserManagementServiceTest {

    private static final Long COMPANY_ID = 4L;
    private static final Long ADMIN_ID = 1L;
    private static final Long TARGET_ID = 50L;

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private UserManagementService service;
    private User target;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentTenant(COMPANY_ID);
        UserPrincipal admin = new UserPrincipal(ADMIN_ID, "admin@erpcloud.com", "", "Admin", COMPANY_ID, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        service = new UserManagementService(userRepository, userRoleRepository, roleRepository,
                companyRepository, passwordEncoder);

        target = new User();
        target.setId(TARGET_ID);
        target.setEmail("contador@erpcloud.com");
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("C2: cannot change the password of a user shared with another company")
    void changePassword_sharedUser_isRejected() {
        when(userRoleRepository.existsByUserIdAndCompanyId(TARGET_ID, COMPANY_ID)).thenReturn(true);
        when(userRoleRepository.countDistinctCompaniesByUserId(TARGET_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.changePassword(TARGET_ID, new ChangePasswordRequest("NuevaClave123")))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode())
                        .isEqualTo("USER_SHARED_WITH_OTHER_COMPANIES"));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("C2: can change the password of a user that belongs only to this company")
    void changePassword_exclusiveUser_succeeds() {
        when(userRoleRepository.existsByUserIdAndCompanyId(TARGET_ID, COMPANY_ID)).thenReturn(true);
        when(userRoleRepository.countDistinctCompaniesByUserId(TARGET_ID)).thenReturn(1L);

        service.changePassword(TARGET_ID, new ChangePasswordRequest("NuevaClave123"));

        assertThat(target.getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    @DisplayName("C2: a shared user can still change their own password")
    void changePassword_self_succeedsEvenIfShared() {
        User self = new User();
        self.setId(ADMIN_ID);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(self));
        when(userRoleRepository.existsByUserIdAndCompanyId(ADMIN_ID, COMPANY_ID)).thenReturn(true);
        when(userRoleRepository.countDistinctCompaniesByUserId(ADMIN_ID)).thenReturn(3L);

        service.changePassword(ADMIN_ID, new ChangePasswordRequest("NuevaClave123"));

        assertThat(self.getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    @DisplayName("C2: cannot deactivate a shared user globally")
    void toggleActive_sharedUser_isRejected() {
        target.setActive(true);
        when(userRoleRepository.existsByUserIdAndCompanyId(TARGET_ID, COMPANY_ID)).thenReturn(true);
        when(userRoleRepository.countDistinctCompaniesByUserId(TARGET_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.toggleActive(TARGET_ID))
                .isInstanceOf(InvalidOperationException.class);
        assertThat(target.isActive()).isTrue();
    }

    @Test
    @DisplayName("A1: cannot assign a role to a user outside the current company")
    void assignRole_userOutsideCompany_isRejected() {
        when(userRoleRepository.existsByUserIdAndCompanyId(TARGET_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.assignRole(TARGET_ID, 7L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRoleRepository, never()).save(any());
    }
}
