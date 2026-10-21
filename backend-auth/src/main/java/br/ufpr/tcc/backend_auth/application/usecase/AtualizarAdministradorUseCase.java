package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.request.AtualizarAdministradorRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.AdministradorDetalheResponse;
import br.ufpr.tcc.backend_auth.application.service.AlteracaoContaService;
import br.ufpr.tcc.backend_auth.domain.exception.ContaNaoEncontradaException;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtualizarAdministradorUseCase {
    private final UsuarioRepository usuarios;
    private final AlteracaoContaService alteracao;

    @Transactional
    public AdministradorDetalheResponse execute(Long id, AtualizarAdministradorRequest request) {
        alteracao.validar(request, request.password());
        var administradores = alteracao.bloquearAdministradores();
        var usuario = usuarios.findByIdAndAdministradorServicoTrue(id).orElseThrow(ContaNaoEncontradaException::new);
        if (!request.active()) {
            alteracao.preservarAdministrador(usuario, administradores);
        }
        alteracao.atualizar(usuario, request.name(), request.email(), request.password(), request.active());
        return AdministradorDetalheResponse.fromEntity(usuario);
    }
}
