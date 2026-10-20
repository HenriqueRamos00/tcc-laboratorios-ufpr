package br.ufpr.tcc.auth.auth.web;

import br.ufpr.tcc.auth.auth.model.IntegrationPrincipal;
import br.ufpr.tcc.auth.auth.web.dto.IntegrationIdentityResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/integrations")
class IntegrationController {
    @GetMapping("/me")
    IntegrationIdentityResponse me(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof IntegrationPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chave de API inválida.");
        }
        return new IntegrationIdentityResponse(principal.apiKeyId(), principal.name(),
                principal.keyPrefix(), principal.expiresAt());
    }
}
