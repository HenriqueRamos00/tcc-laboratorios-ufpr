package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.request.AtualizarUsuarioRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.UsuarioDetalheResponse;
import br.ufpr.tcc.backend_auth.application.service.AlteracaoContaService;
import br.ufpr.tcc.backend_auth.domain.exception.ContaNaoEncontradaException;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_auth.application.service.ServicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtualizarUsuarioUseCase {
    private final UsuarioRepository usuarios;
    private final ServicoService servicos;
    private final AlteracaoContaService alteracao;

    @Transactional
    public UsuarioDetalheResponse execute(Long id, AtualizarUsuarioRequest request) {
        alteracao.validar(request, request.password());
        var administradores = alteracao.bloquearAdministradores();
        var usuario = usuarios.findByIdAndPerfilIsNotNull(id).orElseThrow(ContaNaoEncontradaException::new);
        if (!request.active()) {
            alteracao.preservarAdministrador(usuario, administradores);
        }
        usuario.setPerfil(request.role());
        if (usuario.getServico() == null || !usuario.getServico().getId().equals(request.serviceId())) {
            usuario.setServico(servicos.exigirAtivo(request.serviceId()));
        }
        alteracao.atualizar(usuario, request.name(), request.email(), request.password(), request.active());
        return UsuarioDetalheResponse.fromEntity(usuario);
    }
}
