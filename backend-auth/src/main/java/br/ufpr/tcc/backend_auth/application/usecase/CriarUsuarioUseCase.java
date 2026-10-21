package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarUsuarioRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.UsuarioResponse;
import br.ufpr.tcc.backend_auth.application.validation.SenhaValidator;
import br.ufpr.tcc.backend_auth.domain.exception.DadosInvalidosException;
import br.ufpr.tcc.backend_auth.domain.exception.EmailJaCadastradoException;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import jakarta.validation.Validator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CriarUsuarioUseCase {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final SenhaValidator senhaValidator;
    private final Validator validator;

    @Transactional
    public UsuarioResponse execute(CriarUsuarioRequest request) {
        var erros = validator.validate(request);
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException(erros.stream().map(e -> e.getMessage())
                    .sorted().collect(Collectors.joining(", ")));
        }
        senhaValidator.validar(request.password());
        if (usuarios.existsByEmail(request.email())) {
            throw new EmailJaCadastradoException();
        }
        Usuario usuario = new Usuario();
        usuario.setNome(request.name());
        usuario.setEmail(request.email());
        usuario.setSenhaHash(encoder.encode(request.password()));
        usuario.setPerfil(request.role());
        return UsuarioResponse.fromEntity(usuarios.saveAndFlush(usuario));
    }
}
