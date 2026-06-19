package com.erp.erp_cloud.security;


import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.Permission;
import com.erp.erp_cloud.entity.Role;
import com.erp.erp_cloud.entity.User;
import com.erp.erp_cloud.entity.UserRole;
import com.erp.erp_cloud.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    // --- Constants to avoid magic strings scattered across test methods ---
    private static final String TEST_EMAIL      = "jaime@erpcloud.com";
    private static final String TEST_PASSWORD   = "Password123!";
    private static final Long   TEST_COMPANY_ID = 4L;
    private static final String LOGIN_URL       = "/api/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    // Shared test fixtures — rebuilt fresh before every test
    private User     mockUser;
    private UserRole mockUserRole;

    @BeforeEach
    void setUp() {

        // 1. Build leaf nodes first to avoid null circular references

        // Permission: the atomic capability granted to a role
        Permission permission = new Permission();
        permission.setCode("INVOICE_READ");

        // Role: groups permissions together — code will be prefixed with ROLE_ by the service
        Role role = new Role();
        role.setCode("ACCOUNTANT");
        role.setName("Contador Senior");
        role.setPermissions(Set.of(permission));

        // Company: the tenant the user is trying to log into
        Company company = new Company();
        company.setId(TEST_COMPANY_ID);

        // 2. Build the User node — no roles assigned yet to prevent the circular reference
        mockUser = new User();
        mockUser.setId(100L);
        mockUser.setEmail(TEST_EMAIL);
        // Use a real BCrypt hash so DaoAuthenticationProvider can verify without mocking PasswordEncoder
        mockUser.setPasswordHash(new BCryptPasswordEncoder().encode(TEST_PASSWORD));
        mockUser.setFirstName("Jaime");   // getFullName() derives "Jaime Santa" from these two fields
        mockUser.setLastName("Santa");
        mockUser.setActive(true);
        mockUser.setLockedUntil(null);

        // 3. Build the UserRole bridge — now both User and Role exist, safe to reference both
        mockUserRole = new UserRole();
        mockUserRole.setUser(mockUser);
        mockUserRole.setRole(role);
        mockUserRole.setCompany(company);

        // 4. Close the graph by attaching the role to the user last
        mockUser.setUserRoles(Set.of(mockUserRole));
    }

    // ---------------------------------------------------------------------------
    // Helper: builds the JSON login request body
    // ---------------------------------------------------------------------------
    private Map<String, Object> buildLoginRequest(String email, String password, Long companyId) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("companyId", companyId);
        return body;
    }

    // ---------------------------------------------------------------------------
    // Scenario 1 — Happy path
    // ---------------------------------------------------------------------------
    @Test
    @DisplayName("Should authenticate successfully and return 200 OK with a valid JWT token")
    void login_Success() throws Exception {


        when(userRepository.findByEmailWithRolesAndPermissionsForCompany(TEST_EMAIL, TEST_COMPANY_ID))
                .thenReturn(Optional.of(mockUser));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildLoginRequest(TEST_EMAIL, TEST_PASSWORD, TEST_COMPANY_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.user.fullName").value("Jaime Santa"))
                .andExpect(jsonPath("$.user.companyId").value(TEST_COMPANY_ID));
    }

    // ---------------------------------------------------------------------------
    // Scenario 2 — Wrong password
    // ---------------------------------------------------------------------------
    @Test
    @DisplayName("Should return 401 when the provided password does not match")
    void login_Failure_BadCredentials() throws Exception {

        when(userRepository.findByEmailWithRolesAndPermissionsForCompany(TEST_EMAIL, TEST_COMPANY_ID))
                .thenReturn(Optional.of(mockUser));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildLoginRequest(TEST_EMAIL, "WrongPassword!", TEST_COMPANY_ID))))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------
    // Scenario 3 — Account locked
    // ---------------------------------------------------------------------------
    @Test
    @DisplayName("Should return 401 when the account is temporarily locked due to failed attempts")
    void login_Failure_AccountLocked() throws Exception {

        // Override the lock state — everything else from setUp() remains valid
        mockUser.setLockedUntil(LocalDateTime.now().plusMinutes(15));

        when(userRepository.findByEmailWithRolesAndPermissionsForCompany(TEST_EMAIL, TEST_COMPANY_ID))
                .thenReturn(Optional.of(mockUser));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildLoginRequest(TEST_EMAIL, TEST_PASSWORD, TEST_COMPANY_ID))))
                .andExpect(status().isUnauthorized());
        // Note: if your GlobalExceptionHandler maps LockedException → 423 Locked, change to:
        // .andExpect(status().isLocked());
    }

    // ---------------------------------------------------------------------------
    // Scenario 4 — Inactive user
    // ---------------------------------------------------------------------------
    @Test
    @DisplayName("Should return 401 when the user account is marked as inactive")
    void login_Failure_InactiveUser() throws Exception {

        mockUser.setActive(false);


        when(userRepository.findByEmailWithRolesAndPermissionsForCompany(TEST_EMAIL, TEST_COMPANY_ID))
                .thenReturn(Optional.of(mockUser));


        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildLoginRequest(TEST_EMAIL, TEST_PASSWORD, TEST_COMPANY_ID))))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------
    // Scenario 5 — User exists but has no role assigned in the requested tenant
    // ---------------------------------------------------------------------------
    @Test
    @DisplayName("Should return 401 when the user has no role assigned in the requested company")
    void login_Failure_NoRoleInCompany() throws Exception {

        // Detach the role from the target company by assigning it to a different tenant
        Company otherCompany = new Company();
        otherCompany.setId(99L); // Different tenant — not the one being requested
        mockUserRole.setCompany(otherCompany);


        when(userRepository.findByEmailWithRolesAndPermissionsForCompany(TEST_EMAIL, TEST_COMPANY_ID))
                .thenReturn(Optional.of(mockUser));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildLoginRequest(TEST_EMAIL, TEST_PASSWORD, TEST_COMPANY_ID))))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------
    // Scenario 6 — Malformed email fails Bean Validation before hitting the service
    // ---------------------------------------------------------------------------
    @Test
    @DisplayName("Should return 400 Bad Request when the email format is invalid")
    void login_Failure_InvalidEmailFormat() throws Exception {

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildLoginRequest("not-an-email", TEST_PASSWORD, TEST_COMPANY_ID))))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    // Scenario 7 — Missing companyId fails Bean Validation before hitting the service
    // ---------------------------------------------------------------------------
    @Test
    @DisplayName("Should return 400 Bad Request when the companyId is missing from the request body")
    void login_Failure_MissingCompanyId() throws Exception {

        // Build the request manually without the companyId field
        Map<String, Object> incompleteRequest = new HashMap<>();
        incompleteRequest.put("email", TEST_EMAIL);
        incompleteRequest.put("password", TEST_PASSWORD);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incompleteRequest)))
                .andExpect(status().isBadRequest());
    }
}