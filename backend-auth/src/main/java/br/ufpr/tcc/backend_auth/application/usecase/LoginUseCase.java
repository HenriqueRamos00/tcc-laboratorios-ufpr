package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.LoginUsuarioResponse;
import br.ufpr.tcc.backend_auth.application.service.VerificadorCredenciais;
import br.ufpr.tcc.backend_auth.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_auth.domain.exception.LoginBloqueadoException;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginUseCase {
    private final VerificadorCredenciais credenciais;

    @Transactional(noRollbackFor = {CredenciaisInvalidasException.class, LoginBloqueadoException.class})
    public LoginUsuarioResponse execute(LoginRequest request, Long servicoId) {
        Usuario usuario = credenciais.verificar(request, servicoId);
        if (usuario.getPerfil() == null) {
            throw new CredenciaisInvalidasException();
        }
        return LoginUsuarioResponse.fromEntity(usuario);
    }
}
