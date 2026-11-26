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
import org.junit.jupiter.api.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Boots the full Spring context against the real datasource in application.properties.
// Needs a live MySQL on localhost:3308 (see README/HELP) -- run via `./gradlew integrationTest`.
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
// Many logins from the same IP in one context: the per-IP limit (A2) is
// covered by AuthRateLimitFilterTest instead.
@org.springframework.test.context.TestPropertySource(properties = "app.security.rate-limit.enabled=false")
class AuthControllerIntegrationTest {

    // --- Constants to avoid magic strings scattered across test methods ---
    private static final String TEST_EMAIL      = "jaime@erpcloud.com";
    private static final String TEST_PASSWORD   = "Password123!";
    private static final Long   TEST_COMPANY_ID = 4L;
    private static final String LOGIN_URL       = "/api/auth/login";
    private static final String SELECT_URL      = "/api/auth/select-company";

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
    // B1 (2026-11-25): two-step login. Step 1 = email + password only.
    // ---------------------------------------------------------------------------
    private Map<String, Object> buildLoginRequest(String email, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        return body;
    }

    private Company testCompany() {
        Company company = new Company();
        company.setId(TEST_COMPANY_ID);
        company.setLegalName("EMPRESA DE PRUEBA SAS");
        company.setTaxId("900111222");
        return company;
    }

    @Test
    @DisplayName("Step 1: right password -> 200 with pre-session token and the user's companies")
    void login_Success() throws Exception {
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(userRepository.findAccessibleCompaniesByEmail(TEST_EMAIL)).thenReturn(List.of(testCompany()));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLoginRequest(TEST_EMAIL, TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preAuthToken").exists())
                .andExpect(jsonPath("$.data.companies[0].id").value(TEST_COMPANY_ID))
                .andExpect(jsonPath("$.data.companies[0].taxId").value("900111222"));
    }

    @Test
    @DisplayName("Step 1: wrong password -> 401")
    void login_Failure_BadCredentials() throws Exception {
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLoginRequest(TEST_EMAIL, "WrongPassword!"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Step 1: locked account -> 401")
    void login_Failure_AccountLocked() throws Exception {
        mockUser.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLoginRequest(TEST_EMAIL, TEST_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Step 1: inactive user -> 401")
    void login_Failure_InactiveUser() throws Exception {
        mockUser.setActive(false);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLoginRequest(TEST_EMAIL, TEST_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Step 1: right password but no active company -> 400 NO_ACTIVE_COMPANIES")
    void login_Failure_NoActiveCompany() throws Exception {
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(userRepository.findAccessibleCompaniesByEmail(TEST_EMAIL)).thenReturn(List.of());

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLoginRequest(TEST_EMAIL, TEST_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("NO_ACTIVE_COMPANIES"));
    }

    @Test
    @DisplayName("Step 1: malformed email -> 400")
    void login_Failure_InvalidEmailFormat() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLoginRequest("not-an-email", TEST_PASSWORD))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Step 2 without a pre-session token -> 401 LOGIN_SESSION_EXPIRED")
    void selectCompany_WithoutPreSession() throws Exception {
        mockMvc.perform(post(SELECT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\": " + TEST_COMPANY_ID + "}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("LOGIN_SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("The removed public directory POST /api/auth/companies no longer answers with companies")
    void companiesDirectory_IsGone() throws Exception {
        mockMvc.perform(post("/api/auth/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + TEST_EMAIL + "\"}"))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
    }
}
