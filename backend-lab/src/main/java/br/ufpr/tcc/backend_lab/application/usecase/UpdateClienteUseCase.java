package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.UpdateClienteRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.ClienteResponse;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorVinculosEmpresasCliente;
import br.ufpr.tcc.backend_lab.application.validation.SenhaValidator;
import br.ufpr.tcc.backend_lab.domain.exception.EmailJaCadastradoException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateClienteUseCase {
    private final ClienteRepository repository;
    private final UsuarioRepository usuarios;
    private final ResolvedorVinculosEmpresasCliente resolvedorVinculosEmpresas;
    private final SenhaValidator senhaValidator;
    private final PasswordEncoder encoder;

    @Transactional
    public ClienteResponse execute(Long id, UpdateClienteRequest request) {
        var cliente =
                repository
                        .findById(id)
                        .orElseThrow(
                                () -> RegraNegocioException.naoEncontrado("Cliente não encontrado"));
        var usuario = cliente.getUsuario();
        if (usuario.getPerfil() != PerfilUsuario.CLIENTE) {
            throw RegraNegocioException.acessoNegado("Perfil inválido para cadastro de cliente");
        }
        if ((usuario.getEmail() == null || !usuario.getEmail().equalsIgnoreCase(request.email()))
                && usuarios.existsByEmailIgnoreCase(request.email())) {
            throw new EmailJaCadastradoException();
        }
        var empresas =
                resolvedorVinculosEmpresas.resolver(request.companyIds(), cliente.getEmpresas());
        senhaValidator.validar(request.password());
        usuario.setNome(request.name());
        usuario.setEmail(request.email());
        usuario.setAtivo(request.status().toAtivo());
        if (request.password() != null) usuario.setSenhaHash(encoder.encode(request.password()));
        cliente.setUsuario(usuarios.saveAndFlush(usuario));
        cliente.setPhone(request.phone());
        cliente.setPosition(request.position());
        cliente.getEmpresas().clear();
        cliente.getEmpresas().addAll(empresas);
        return ClienteResponse.from(repository.saveAndFlush(cliente));
    }
}
