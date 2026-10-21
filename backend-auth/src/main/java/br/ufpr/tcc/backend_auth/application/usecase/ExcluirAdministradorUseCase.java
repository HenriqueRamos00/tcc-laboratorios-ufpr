package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.service.AlteracaoContaService;
import br.ufpr.tcc.backend_auth.domain.exception.ContaNaoEncontradaException;
import br.ufpr.tcc.backend_auth.domain.repository.SessaoAdministrativaRepository;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExcluirAdministradorUseCase {
    private final UsuarioRepository usuarios;
    private final SessaoAdministrativaRepository sessoes;
    private final AlteracaoContaService alteracao;

    @Transactional
    public void execute(Long id) {
        var administradores = alteracao.bloquearAdministradores();
        var usuario = usuarios.findByIdAndAdministradorServicoTrue(id).orElseThrow(ContaNaoEncontradaException::new);
        alteracao.preservarAdministrador(usuario, administradores);
        sessoes.excluirPorUsuario(id);
        if (usuario.getPerfil() == null) {
            usuarios.delete(usuario);
            usuarios.flush();
        } else {
            usuario.setAdministradorServico(false);
            usuarios.saveAndFlush(usuario);
        }
    }
}
