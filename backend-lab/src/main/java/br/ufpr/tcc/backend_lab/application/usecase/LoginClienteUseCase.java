package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.ClienteLoginResponse;
import br.ufpr.tcc.backend_lab.application.validation.SenhaValidator;
import br.ufpr.tcc.backend_lab.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class LoginClienteUseCase {
    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final SenhaValidator senhaValidator;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    @Transactional(readOnly = true)
    public ClienteLoginResponse execute(LoginRequest request) {
        senhaValidator.validar(request.password());
        var usuario =
                usuarios.findByEmailIgnoreCase(request.email().trim())
                        .filter(Usuario::isAtivo)
                        .filter(x -> x.getPerfil() == PerfilUsuario.CLIENTE)
                        .orElseThrow(CredenciaisInvalidasException::new);
        if (!encoder.matches(request.password(), usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }
        var cliente =
                clientes.findByUsuarioId(usuario.getId())
                        .orElseThrow(CredenciaisInvalidasException::new);
        var companies =
                cliente.getEmpresas().stream()
                        .filter(Empresa::isAtivo)
                        .sorted(Comparator.comparing(Empresa::getId))
                        .map(e -> new ClienteLoginResponse.Company(e.getId().toString(), e.getLegalName()))
                        .toList();
        return new ClienteLoginResponse(
                jwt.generateToken(usuario),
                "Bearer",
                jwt.getExpirationSeconds(),
                new ClienteLoginResponse.User(
                        usuario.getId().toString(),
                        usuario.getNome(),
                        usuario.getEmail(),
                        "Client",
                        companies));
    }
}
