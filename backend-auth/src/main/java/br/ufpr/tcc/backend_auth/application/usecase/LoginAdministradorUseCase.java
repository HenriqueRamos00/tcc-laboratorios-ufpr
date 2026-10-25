package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.LoginAdministradorResponse;
import br.ufpr.tcc.backend_auth.application.service.TokenAdministrativoService;
import br.ufpr.tcc.backend_auth.application.service.VerificadorCredenciais;
import br.ufpr.tcc.backend_auth.domain.exception.CredenciaisInvalidasException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginAdministradorUseCase {
    private final VerificadorCredenciais credenciais;
    private final TokenAdministrativoService tokens;

    @Transactional
    public LoginAdministradorResponse execute(LoginRequest request) {
        var usuario = credenciais.verificarAdministrador(request);
        if (!usuario.isAdministradorServico()) {
            throw new CredenciaisInvalidasException();
        }
        return tokens.emitir(usuario);
    }
}
