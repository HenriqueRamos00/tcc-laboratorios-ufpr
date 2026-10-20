package br.ufpr.tcc.auth.auth.security;

import java.io.IOException;
import java.util.List;

import br.ufpr.tcc.auth.auth.application.ApiKeyService;
import br.ufpr.tcc.auth.auth.model.ApiKey;
import br.ufpr.tcc.auth.auth.model.IntegrationPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates service-to-service requests using the X-API-Key header.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-API-Key";

    private final ApiKeyService apiKeys;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeys) {
        this.apiKeys = apiKeys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if (rawKey != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            apiKeys.authenticate(rawKey).ifPresent(apiKey -> authenticate(apiKey, request));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(ApiKey apiKey, HttpServletRequest request) {
        IntegrationPrincipal principal = new IntegrationPrincipal(apiKey.getId(),
                apiKey.getName(), apiKey.getKeyPrefix(), apiKey.getExpiresAt());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_INTEGRATION")));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
