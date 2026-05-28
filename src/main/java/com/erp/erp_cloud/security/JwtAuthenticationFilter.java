package com.erp.erp_cloud.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    /**
     * Rutas que nunca cargarán JWT — el filtro las omite por completo.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/")
                || path.startsWith("/actuator/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    /**
     * Intercepts every HTTP request to extract, parse, and validate the JWT token.
     * A single parse operation extracts all claims (email, userId, companyId, authorities).
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {

                // Single parse: validates + extracts all claims in one shot.
                // Returns null if the token is invalid or expired.
                UserPrincipalClaims claims = tokenProvider.extractValidClaims(jwt);

                if (claims != null) {

                    // Maps raw authority strings back to Spring Security GrantedAuthority structures
                    List<SimpleGrantedAuthority> authorities = claims.authorities().stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

                    // Builds a rich UserPrincipal from JWT claims — no database call needed
                    UserPrincipal userPrincipal = UserPrincipal.fromClaims(claims, authorities);

                    // Principal is now a typed UserPrincipal, not a plain String.
                    // Controllers can safely cast: (UserPrincipal) auth.getPrincipal()
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userPrincipal,  // objeto rico con id, email, companyId
                                    null,           // credentials null — ya fue validado por JWT
                                    authorities
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    // Locks authentication into the current request thread context
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Autenticado: {} | tenant: {} | URI: {} {}",
                            claims.email(),
                            claims.companyId(),
                            request.getMethod(),
                            request.getRequestURI());
                }
            }

        } catch (ExpiredJwtException ex) {
            // Caso de negocio esperado — no es un error del sistema
            log.warn("Token expirado | subject: {} | URI: {}",
                    ex.getClaims().getSubject(),
                    request.getRequestURI());

        } catch (JwtException ex) {
            // Token malformado, firma inválida, etc.
            log.warn("JWT inválido: {} | URI: {}", ex.getMessage(), request.getRequestURI());

        } catch (Exception ex) {
            // Bug inesperado en el filtro — stack trace completo para diagnóstico
            log.error("Error inesperado en filtro JWT | URI: {} | Error: {}",
                    request.getRequestURI(), ex.getMessage(), ex);
        }

        // Delegates execution to the next filter in the chain regardless of auth result
        filterChain.doFilter(request, response);
    }

    /**
     * Parses the HTTP Authorization header looking for standard Bearer Token prefix.
     * Returns null if the header is absent or malformed.
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}