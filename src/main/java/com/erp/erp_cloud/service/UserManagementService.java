package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChangePasswordRequest;
import com.erp.erp_cloud.dto.CreateUserRequest;
import com.erp.erp_cloud.dto.RoleOptionDTO;
import com.erp.erp_cloud.dto.UpdateUserRequest;
import com.erp.erp_cloud.dto.UserLookupResultDTO;
import com.erp.erp_cloud.dto.UserRoleAssignmentDTO;
import com.erp.erp_cloud.dto.UserSummaryDTO;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.Role;
import com.erp.erp_cloud.entity.User;
import com.erp.erp_cloud.entity.UserRole;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.repository.RoleRepository;
import com.erp.erp_cloud.repository.UserRepository;
import com.erp.erp_cloud.repository.UserRoleRepository;
import com.erp.erp_cloud.security.UserPrincipal;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NEW (2026-10-29, Multiempresa Fase 3): backend for the
 * UserManagementPage.jsx admin screen. Every operation here is scoped
 * to the CURRENT company (TenantAwareService.currentTenantId()) -- an
 * admin logged into company 4 can only see/manage users AS THEY RELATE
 * TO company 4, never another tenant's assignments, even though the
 * underlying User/password/active flag are genuinely global (one human,
 * one account, usable across every company they have a role in -- see
 * each method's own note on which fields are global vs. company-scoped).
 */
@Service
@RequiredArgsConstructor
public class UserManagementService extends TenantAwareService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Every user visible from the CURRENT company's admin screen: anyone
     * with at least one UserRole here, with only THEIR roles IN THIS
     * COMPANY attached -- never roles the same person might hold in some
     * other tenant, which this screen has no business exposing.
     */
    @Transactional(readOnly = true)
    public List<UserSummaryDTO> listUsers() {
        Long companyId = currentTenantId();
        List<UserRole> rows = userRoleRepository.findAllWithUserAndRoleByCompanyId(companyId);

        Map<Long, User> usersById = new LinkedHashMap<>();
        Map<Long, List<UserRoleAssignmentDTO>> rolesByUserId = new LinkedHashMap<>();

        for (UserRole ur : rows) {
            Long userId = ur.getUser().getId();
            usersById.putIfAbsent(userId, ur.getUser());
            rolesByUserId.computeIfAbsent(userId, id -> new ArrayList<>())
                    .add(new UserRoleAssignmentDTO(ur.getRole().getId(), ur.getRole().getName(), ur.getRole().getCode()));
        }

        return usersById.values().stream()
                .map(u -> toSummaryDto(u, rolesByUserId.getOrDefault(u.getId(), List.of())))
                .toList();
    }

    /**
     * Looks up whether an email is already a registered user ANYWHERE in
     * the system -- lets the "add user" form in the UI switch between
     * "brand-new person" (needs a password) and "existing person, just
     * grant access here" (no password field) before submitting.
     */
    @Transactional(readOnly = true)
    public UserLookupResultDTO lookupByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .map(u -> new UserLookupResultDTO(true, u.getFullName()))
                .orElseGet(() -> new UserLookupResultDTO(false, null));
    }

    @Transactional(readOnly = true)
    public List<RoleOptionDTO> listAvailableRoles() {
        return roleRepository.findAvailableForCompany(currentTenantId()).stream()
                .map(r -> new RoleOptionDTO(r.getId(), r.getName(), r.getCode()))
                .toList();
    }

    /**
     * Creates a brand-new person (with a password) OR, if the email is
     * already a registered user elsewhere, simply grants THEM access to
     * the current company with the requested roles -- User.email is
     * globally unique, so this never creates a duplicate account.
     */
    @Transactional
    public UserSummaryDTO createOrInviteUser(CreateUserRequest request) {
        Long companyId = currentTenantId();
        List<Role> roles = resolveAndValidateRoles(request.roleIds(), companyId);

        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Only required on THIS branch -- an existing user's name
            // and password are never touched by this call (see the
            // class-level note and CreateUserRequest's own doc comment).
            if (request.firstName() == null || request.firstName().isBlank()
                    || request.lastName() == null || request.lastName().isBlank()) {
                throw new InvalidOperationException(
                        "Nombre y apellido son obligatorios para una persona nueva.", "NAME_REQUIRED_FOR_NEW_USER");
            }
            validatePasswordLength(request.password());

            user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setFirstName(request.firstName());
            user.setLastName(request.lastName());
            user.setActive(true);
            user.setEmailVerified(false);
            user.setFailedLoginAttempts(0);
            user = userRepository.save(user);

            log.info("USER_CREATED | newUserId: {} | companyId: {}", user.getId(), companyId);
        } else {
            log.info("USER_INVITED_TO_COMPANY | existingUserId: {} | companyId: {}", user.getId(), companyId);
        }

        grantRoles(user, roles, companyId);

        return toSummary(user, companyId);
    }

    @Transactional
    public UserSummaryDTO updateUser(Long userId, UpdateUserRequest request) {
        Long companyId = currentTenantId();
        User user = requireUserInCurrentCompany(userId, companyId);

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());

        return toSummary(user, companyId);
    }

    /**
     * Flips the user's GLOBAL active flag. Deliberately NOT the primary
     * "remove this user" action on this screen (see revokeCompanyAccess
     * below for that) -- this affects login across EVERY company the
     * user has access to, which the frontend must make very clear before
     * calling this.
     */
    @Transactional
    public UserSummaryDTO toggleActive(Long userId) {
        Long companyId = currentTenantId();
        User user = requireUserInCurrentCompany(userId, companyId);

        if (user.getId().equals(currentUserId())) {
            throw new InvalidOperationException(
                    "No puedes desactivar tu propia cuenta.", "CANNOT_DEACTIVATE_OWN_ACCOUNT");
        }

        user.setActive(!user.isActive());
        return toSummary(user, companyId);
    }

    /**
     * Admin-triggered password reset (no old-password check). The
     * password is global to the User entity -- this changes how the
     * person logs into EVERY company they have access to, not just the
     * one this admin is currently in.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        Long companyId = currentTenantId();
        User user = requireUserInCurrentCompany(userId, companyId);

        validatePasswordLength(request.newPassword());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
    }

    /**
     * Grants one role, IN THE CURRENT COMPANY, to any existing user.
     * Deliberately NOT restricted to users already visible from this
     * company's own list: granting someone's FIRST role here is exactly
     * how an accountant who already works with a different company gets
     * brought into this one too.
     */
    @Transactional
    public UserSummaryDTO assignRole(Long userId, Long roleId) {
        Long companyId = currentTenantId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        List<Role> roles = resolveAndValidateRoles(List.of(roleId), companyId);

        grantRoles(user, roles, companyId);

        return toSummary(user, companyId);
    }

    @Transactional
    public UserSummaryDTO removeRole(Long userId, Long roleId) {
        Long companyId = currentTenantId();
        User user = requireUserInCurrentCompany(userId, companyId);

        UserRole ur = userRoleRepository.findByUserIdAndRoleIdAndCompanyId(userId, roleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("UserRole", roleId));
        userRoleRepository.delete(ur);

        return toSummary(user, companyId);
    }

    /**
     * Bulk "quitar de esta empresa": deletes EVERY role this user holds
     * in the current company in one shot, without touching their access
     * to any other company (their User row, password, and any OTHER
     * company's UserRole rows are untouched).
     */
    @Transactional
    public void revokeCompanyAccess(Long userId) {
        Long companyId = currentTenantId();
        requireUserInCurrentCompany(userId, companyId);

        if (userId.equals(currentUserId())) {
            throw new InvalidOperationException(
                    "No puedes retirarte a ti mismo de esta empresa.", "CANNOT_REVOKE_OWN_ACCESS");
        }

        List<UserRole> rows = userRoleRepository.findByUserIdAndCompanyId(userId, companyId);
        userRoleRepository.deleteAll(rows);
    }

    // -----------------------------------------------------------------

    private void grantRoles(User user, List<Role> roles, Long companyId) {
        Company company = companyRepository.getReferenceById(companyId);
        for (Role role : roles) {
            if (!userRoleRepository.existsByUserIdAndRoleIdAndCompanyId(user.getId(), role.getId(), companyId)) {
                UserRole ur = new UserRole();
                ur.setUser(user);
                ur.setRole(role);
                ur.setCompany(company);
                ur.setAssignedAt(LocalDateTime.now());
                userRoleRepository.save(ur);
            }
        }
    }

    private List<Role> resolveAndValidateRoles(List<Long> roleIds, Long companyId) {
        return roleIds.stream().map(roleId -> {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
            boolean belongsHere = role.getCompany() == null || role.getCompany().getId().equals(companyId);
            if (!belongsHere) {
                throw new InvalidOperationException(
                        "El rol seleccionado no pertenece a esta compania.", "ROLE_NOT_IN_COMPANY");
            }
            return role;
        }).toList();
    }

    /**
     * Tenant-isolation guard: the target user must already be visible
     * from the CURRENT company's own user list (i.e. have at least one
     * role here) before this admin can touch them AT ALL. Without this,
     * a company-4 admin could update/deactivate/reset-password on a user
     * who has nothing to do with company 4, just by guessing an id --
     * the single most important security property of this whole feature.
     */
    private User requireUserInCurrentCompany(Long userId, Long companyId) {
        if (!userRoleRepository.existsByUserIdAndCompanyId(userId, companyId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private void validatePasswordLength(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidOperationException(
                    "La contrasena debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres.",
                    "PASSWORD_TOO_SHORT");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private UserSummaryDTO toSummary(User user, Long companyId) {
        List<UserRoleAssignmentDTO> roles = userRoleRepository.findByUserIdAndCompanyId(user.getId(), companyId)
                .stream()
                .map(ur -> new UserRoleAssignmentDTO(ur.getRole().getId(), ur.getRole().getName(), ur.getRole().getCode()))
                .toList();
        return toSummaryDto(user, roles);
    }

    private UserSummaryDTO toSummaryDto(User user, List<UserRoleAssignmentDTO> roles) {
        return new UserSummaryDTO(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getFullName(), user.isActive(), roles);
    }

    private Long currentUserId() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getId();
    }
}
