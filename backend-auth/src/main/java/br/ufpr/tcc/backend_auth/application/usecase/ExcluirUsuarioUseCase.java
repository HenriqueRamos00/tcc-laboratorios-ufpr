package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.domain.exception.ContaNaoEncontradaException;
import br.ufpr.tcc.backend_auth.domain.repository.SessaoAdministrativaRepository;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExcluirUsuarioUseCase {
    private final UsuarioRepository usuarios;
    private final SessaoAdministrativaRepository sessoes;

    @Transactional
    public void execute(Long id) {
        usuarios.bloquearAdministradores();
        var usuario = usuarios.findByIdAndPerfilIsNotNull(id).orElseThrow(ContaNaoEncontradaException::new);
        if (usuario.isAdministradorServico()) {
            usuario.setPerfil(null);
            usuario.setServico(null);
            usuarios.saveAndFlush(usuario);
        } else {
            sessoes.excluirPorUsuario(id);
            usuarios.delete(usuario);
            usuarios.flush();
        }
    }
}
