package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.UsuarioResponse;
import br.ufpr.tcc.backend_auth.application.service.VerificadorCredenciais;
import br.ufpr.tcc.backend_auth.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginUseCase {
    private final VerificadorCredenciais credenciais;

    @Transactional(readOnly = true)
    public UsuarioResponse execute(LoginRequest request) {
        Usuario usuario = credenciais.verificar(request);
        if (usuario.getPerfil() == null) {
            throw new CredenciaisInvalidasException();
        }
        return UsuarioResponse.fromEntity(usuario);
    }
}
