package br.ufpr.tcc.backend_auth.infrastructure.security;

import br.ufpr.tcc.backend_auth.application.service.TokenAdministrativoService;
import br.ufpr.tcc.backend_auth.domain.exception.CredenciaisInvalidasException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminTokenIntrospector implements OpaqueTokenIntrospector {
    private final TokenAdministrativoService tokens;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        try {
            var admin = tokens.autenticar(token);
            return new DefaultOAuth2AuthenticatedPrincipal(admin.id(), Map.of("sub", admin.id()),
                    List.of(new SimpleGrantedAuthority("ROLE_SERVICE_ADMIN")));
        } catch (CredenciaisInvalidasException exception) {
            throw new BadOpaqueTokenException("Token administrativo inválido ou expirado");
        }
    }
}
