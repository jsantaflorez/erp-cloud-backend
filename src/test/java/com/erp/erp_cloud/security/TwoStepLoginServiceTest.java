package com.erp.erp_cloud.security;

import com.erp.erp_cloud.dto.auth.AuthResponse;
import com.erp.erp_cloud.dto.auth.CompanyOptionDTO;
import com.erp.erp_cloud.dto.auth.PreAuthResponse;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.User;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.LoginSessionExpiredException;
import com.erp.erp_cloud.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B1 (2026-11-25): login en dos pasos. Paso 1 verifica la contraseña antes
 * de listar empresas; el token de pre-sesión solo sirve para el paso 2 y el
 * de acceso no sirve como pre-sesión (ni al revés).
 */
class TwoStepLoginServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes-long!!";
    private static final String EMAIL = "admin.navarra@erpcloud.com";
    private static final Long USER_ID = 10L;
    private static final Long COMPANY_ID = 7L;

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private CustomUserDetailsService userDetailsService;

    private JwtTokenProvider tokenProvider;
    private TwoStepLoginService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tokenProvider = new JwtTokenProvider(SECRET, 28_800_000L, 300_000L);
        service = new TwoStepLoginService(authenticationManager, tokenProvider, userRepository,
                loginAttemptService, userDetailsService);
    }

    private static User user(Long id, LocalDateTime lockedUntil) {
        User u = new User();
        u.setId(id);
        u.setEmail(EMAIL);
        u.setPasswordHash("x");
        u.setFirstName("Admin");
        u.setLastName("Navarra");
        u.setActive(true);
        u.setLockedUntil(lockedUntil);
        return u;
    }

    private static Company company(Long id, String name, String nit) {
        Company c = new Company();
        c.setId(id);
        c.setLegalName(name);
        c.setTaxId(nit);
        return c;
    }

    private void passwordAccepted() {
        UserPrincipal preAuth = new UserPrincipal(user(USER_ID, null), null, Set.of(), Set.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(preAuth, null, preAuth.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
    }

    private String preAuthToken() {
        return tokenProvider.generatePreAuthToken(new UserPrincipal(user(USER_ID, null), null, Set.of(), Set.of()));
    }

    @Test
    @DisplayName("Paso 1: con la contraseña correcta devuelve token de pre-sesión y empresas con NIT")
    void start_success() {
        passwordAccepted();
        when(userRepository.findAccessibleCompaniesByEmail(EMAIL))
                .thenReturn(List.of(company(COMPANY_ID, "NAVARRA SAS", "900111222"), company(8L, "OTRA SAS", "800333444")));

        PreAuthResponse response = service.start("  Admin.Navarra@ERPCLOUD.com ", "secreta");

        // Authenticates the e-mail ALONE (no "|companyId"), normalized.
        verify(authenticationManager).authenticate(argThat(a -> EMAIL.equals(a.getPrincipal())));
        verify(loginAttemptService).recordSuccess(USER_ID);
        assertThat(response.companies()).extracting(CompanyOptionDTO::taxId).containsExactly("900111222", "800333444");
        assertThat(response.expiresIn()).isEqualTo(300);
        assertThat(response.fullName()).isNotBlank();
        // The pre-session token is NOT an access token.
        assertThatThrownBy(() -> tokenProvider.extractValidClaims(response.preAuthToken()))
                .isInstanceOf(JwtException.class);
        assertThat(tokenProvider.extractPreAuthClaims(response.preAuthToken()).userId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Paso 1: contraseña incorrecta cuenta el intento y no consulta empresas")
    void start_badPassword() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> service.start(EMAIL, "mala")).isInstanceOf(BadCredentialsException.class);

        verify(loginAttemptService).recordFailure(EMAIL);
        verify(userRepository, never()).findAccessibleCompaniesByEmail(any());
    }

    @Test
    @DisplayName("Paso 1: sin empresas activas -> NO_ACTIVE_COMPANIES")
    void start_noCompanies() {
        passwordAccepted();
        when(userRepository.findAccessibleCompaniesByEmail(EMAIL)).thenReturn(List.of());

        assertThatThrownBy(() -> service.start(EMAIL, "secreta"))
                .isInstanceOf(InvalidOperationException.class)
                .extracting("errorCode").isEqualTo("NO_ACTIVE_COMPANIES");
    }

    @Test
    @DisplayName("Paso 2: con pre-sesión válida emite el token de acceso de la empresa elegida")
    void selectCompany_success() {
        UserPrincipal target = new UserPrincipal(user(USER_ID, null), COMPANY_ID, Set.of("ROLE_COMPANY_ADMIN"), Set.of("JOURNAL_ENTRY_READ"));
        when(userDetailsService.loadUserByUsername(EMAIL + "|" + COMPANY_ID)).thenReturn(target);

        AuthResponse response = service.selectCompany("Bearer " + preAuthToken(), COMPANY_ID);

        assertThat(response.companyId()).isEqualTo(COMPANY_ID);
        assertThat(response.roles()).containsExactly("COMPANY_ADMIN");
        assertThat(tokenProvider.extractValidClaims(response.token()).companyId()).isEqualTo(COMPANY_ID);
    }

    @Test
    @DisplayName("Paso 2: sin token, con token alterado o con un token de ACCESO -> LOGIN_SESSION_EXPIRED")
    void selectCompany_invalidPreSession() {
        assertThatThrownBy(() -> service.selectCompany(null, COMPANY_ID))
                .isInstanceOf(LoginSessionExpiredException.class);
        assertThatThrownBy(() -> service.selectCompany("Bearer abc.def.ghi", COMPANY_ID))
                .isInstanceOf(LoginSessionExpiredException.class);

        UserPrincipal access = new UserPrincipal(user(USER_ID, null), COMPANY_ID, Set.of("ROLE_VIEWER"), Set.of());
        String accessToken = tokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(access, null, access.getAuthorities()));
        assertThatThrownBy(() -> service.selectCompany("Bearer " + accessToken, COMPANY_ID))
                .isInstanceOf(LoginSessionExpiredException.class);
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    @Test
    @DisplayName("Paso 2: pre-sesión vencida -> LOGIN_SESSION_EXPIRED")
    void selectCompany_expired() {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 28_800_000L, -1_000L);
        String expired = shortLived.generatePreAuthToken(new UserPrincipal(user(USER_ID, null), null, Set.of(), Set.of()));

        assertThatThrownBy(() -> service.selectCompany("Bearer " + expired, COMPANY_ID))
                .isInstanceOf(LoginSessionExpiredException.class);
    }

    @Test
    @DisplayName("Paso 2: empresa sin acceso -> NO_ACCESS_TO_COMPANY")
    void selectCompany_noAccess() {
        when(userDetailsService.loadUserByUsername(EMAIL + "|99")).thenThrow(new UsernameNotFoundException("no"));

        assertThatThrownBy(() -> service.selectCompany("Bearer " + preAuthToken(), 99L))
                .isInstanceOf(InvalidOperationException.class)
                .extracting("errorCode").isEqualTo("NO_ACCESS_TO_COMPANY");
    }

    @Test
    @DisplayName("Paso 2: otro usuario con el mismo correo, o cuenta bloqueada en el intermedio, se rechaza")
    void selectCompany_otherUserOrLocked() {
        UserPrincipal otherUser = new UserPrincipal(user(55L, null), COMPANY_ID, Set.of("ROLE_VIEWER"), Set.of());
        when(userDetailsService.loadUserByUsername(EMAIL + "|" + COMPANY_ID)).thenReturn(otherUser);
        assertThatThrownBy(() -> service.selectCompany("Bearer " + preAuthToken(), COMPANY_ID))
                .isInstanceOf(LoginSessionExpiredException.class);

        UserPrincipal locked = new UserPrincipal(user(USER_ID, LocalDateTime.now().plusMinutes(10)), COMPANY_ID, Set.of("ROLE_VIEWER"), Set.of());
        when(userDetailsService.loadUserByUsername(EMAIL + "|" + COMPANY_ID)).thenReturn(locked);
        assertThatThrownBy(() -> service.selectCompany("Bearer " + preAuthToken(), COMPANY_ID))
                .isInstanceOf(LockedException.class);
    }
}
