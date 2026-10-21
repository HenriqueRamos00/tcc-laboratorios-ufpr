package br.ufpr.tcc.backend_auth.infrastructure.security;

import br.ufpr.tcc.backend_auth.application.service.TokenAdministrativoService;
import br.ufpr.tcc.backend_auth.application.service.TokenServicoService;
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
public class BearerTokenIntrospector implements OpaqueTokenIntrospector {
    private final TokenAdministrativoService tokens;
    private final TokenServicoService tokensServico;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        try {
            if (token != null && token.length() == 47 && token.startsWith("svc_")) {
                Long servicoId = tokensServico.autenticar(token);
                return new DefaultOAuth2AuthenticatedPrincipal("service:" + servicoId,
                        Map.of("sub", "service:" + servicoId, "serviceId", servicoId),
                        List.of(new SimpleGrantedAuthority("ROLE_APPLICATION_SERVICE")));
            }
            var admin = tokens.autenticar(token);
            return new DefaultOAuth2AuthenticatedPrincipal(admin.id(), Map.of("sub", admin.id()),
                    List.of(new SimpleGrantedAuthority("ROLE_SERVICE_ADMIN")));
        } catch (CredenciaisInvalidasException exception) {
            throw new BadOpaqueTokenException("Token inválido ou expirado");
        }
    }
}
