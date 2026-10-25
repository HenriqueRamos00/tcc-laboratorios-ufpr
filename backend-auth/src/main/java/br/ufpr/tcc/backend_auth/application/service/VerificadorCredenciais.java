package br.ufpr.tcc.backend_auth.application.service;

import br.ufpr.tcc.backend_auth.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_auth.application.validation.SenhaValidator;
import br.ufpr.tcc.backend_auth.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_auth.domain.exception.LoginBloqueadoException;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import java.util.Optional;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificadorCredenciais {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final SenhaValidator senhaValidator;
    private final BloqueioLoginClienteService bloqueio;

    // As falhas e o bloqueio devem ser confirmados mesmo quando a resposta é 401 ou 429.
    @Transactional(noRollbackFor = {CredenciaisInvalidasException.class, LoginBloqueadoException.class})
    public Usuario verificar(LoginRequest request, Long servicoId) {
        if (servicoId == null) throw new CredenciaisInvalidasException();
        senhaValidator.validar(request.password());
        Usuario usuario = usuarios.findByEmailAndServicoIdAndServicoAtivoTrueAndPerfilIsNotNull(request.email(), servicoId)
                .filter(Usuario::isAtivo).orElseThrow(CredenciaisInvalidasException::new);
        bloqueio.verificar(usuario, Instant.now());
        if (!encoder.matches(request.password(), usuario.getSenhaHash())) {
            bloqueio.registrarFalha(usuario, Instant.now());
            throw new CredenciaisInvalidasException();
        }
        bloqueio.limpar(usuario);
        return usuario;
    }

    @Transactional
    public Usuario verificarAdministrador(LoginRequest request) {
        // Mantém a credencial bloqueada até a emissão do token, inclusive contra trocas de senha concorrentes.
        return validar(request, usuarios.findByEmailAndAdministradorServicoTrue(request.email()));
    }

    private Usuario validar(LoginRequest request, Optional<Usuario> encontrado) {
        senhaValidator.validar(request.password());
        Usuario usuario = encontrado
                .filter(Usuario::isAtivo)
                .orElseThrow(CredenciaisInvalidasException::new);
        if (!encoder.matches(request.password(), usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }
        return usuario;
    }
}
