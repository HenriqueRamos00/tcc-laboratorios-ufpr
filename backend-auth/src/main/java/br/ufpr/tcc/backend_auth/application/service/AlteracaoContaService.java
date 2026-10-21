package br.ufpr.tcc.backend_auth.application.service;

import br.ufpr.tcc.backend_auth.application.validation.SenhaValidator;
import br.ufpr.tcc.backend_auth.domain.exception.ConflitoContaException;
import br.ufpr.tcc.backend_auth.domain.exception.DadosInvalidosException;
import br.ufpr.tcc.backend_auth.domain.exception.EmailJaCadastradoException;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.repository.SessaoAdministrativaRepository;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import jakarta.validation.Validator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlteracaoContaService {
    private final UsuarioRepository usuarios;
    private final SessaoAdministrativaRepository sessoes;
    private final Validator validator;
    private final SenhaValidator senhaValidator;
    private final PasswordEncoder encoder;

    public void validar(Object request, String password) {
        var erros = validator.validate(request);
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException(erros.stream().map(e -> e.getMessage()).sorted().collect(Collectors.joining(", ")));
        }
        if (password != null) {
            senhaValidator.validar(password);
        }
    }

    public List<Usuario> bloquearAdministradores() {
        return usuarios.bloquearAdministradores();
    }

    public void preservarAdministrador(Usuario usuario, List<Usuario> administradores) {
        if (usuario.isAdministradorServico() && usuario.isAtivo()
                && administradores.stream().filter(Usuario::isAtivo).count() <= 1) {
            throw new ConflitoContaException("É necessário manter pelo menos um administrador ativo");
        }
    }

    public void atualizar(Usuario usuario, String name, String email, String password, boolean active) {
        if (usuarios.existsByEmailAndIdNot(email, usuario.getId())) {
            throw new EmailJaCadastradoException();
        }
        usuario.setNome(name);
        usuario.setEmail(email);
        usuario.setAtivo(active);
        if (password != null) {
            usuario.setSenhaHash(encoder.encode(password));
        }
        if (password != null || !active) {
            sessoes.excluirPorUsuario(usuario.getId());
        }
        usuarios.saveAndFlush(usuario);
    }
}
