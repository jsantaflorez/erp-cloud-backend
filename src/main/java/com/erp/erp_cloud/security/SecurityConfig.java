package com.erp.erp_cloud.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    // Injects the custom tenant-isolated identity resolver service
    private final CustomUserDetailsService userDetailsService;

    // Read-only profile (2026-11-19): decides which roles may write.
    private final AuthorizationRules authorizationRules;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures the explicit authentication manager blueprint.
     * Binds the custom multi-tenant UserDetailsService and BCrypt hash validator
     * together to eliminate fallback or misconfigured NoOp default states.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Injects dynamic CORS properties structure
                .cors(cors -> {})

                // Disables CSRF protection since our REST API uses stateless JWT authentication tokens
                .csrf(AbstractHttpConfigurer::disable)

                // Plugs custom exception entry points to handle clean JSON error formats
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )

                // Establishes a fully stateless environment execution model
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Route access definition rules matching the filter exclusion logic
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // Secures advanced system metrics. Only users with master roles can access monitor fields
                        .requestMatchers("/actuator/**").hasRole("SYS_ADMIN")
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // NEW (2026-11-19, perfiles Administrador / Auxiliar / Consulta):
                        // every write under /api/v1 requires a writer role
                        // (AuthorizationRules.canWrite). A read-only profile
                        // (VIEWER) gets 403 ACCESS_DENIED on any POST/PUT/
                        // PATCH/DELETE, central and by default, so a new
                        // endpoint is protected without anyone remembering
                        // to annotate it. Admin-only actions keep their own
                        // @PreAuthorize("@authz.isAdmin()") on top of this.
                        // Only exception: switching the active company,
                        // which changes no data.
                        .requestMatchers(HttpMethod.POST, "/api/v1/session/switch-company").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/**").access(writeAccess())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/**").access(writeAccess())
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/**").access(writeAccess())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").access(writeAccess())

                        // All other business domains require valid authenticated credentials
                        .anyRequest().authenticated()
                );

        // Explicitly registers the customized data access authentication provider wireframe
        http.authenticationProvider(authenticationProvider());

        // Injects our custom JWT processing step right before the standard execution interceptor
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private AuthorizationManager<RequestAuthorizationContext> writeAccess() {
        return (authentication, context) ->
                new AuthorizationDecision(authorizationRules.canWrite(authentication.get()));
    }
}