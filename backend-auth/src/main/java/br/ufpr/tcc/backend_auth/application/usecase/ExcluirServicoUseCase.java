package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.service.ServicoService;
import br.ufpr.tcc.backend_auth.domain.exception.ConflitoServicoException;
import br.ufpr.tcc.backend_auth.domain.repository.ServicoRepository;
import br.ufpr.tcc.backend_auth.domain.repository.TokenServicoRepository;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExcluirServicoUseCase {
    private final ServicoRepository servicos;
    private final UsuarioRepository usuarios;
    private final TokenServicoRepository tokens;
    private final ServicoService cadastro;

    @Transactional
    public void execute(Long id) {
        var servico = cadastro.bloquear(id);
        if (usuarios.existsByServicoId(id)) {
            throw new ConflitoServicoException("Reassocie ou exclua os clientes antes de excluir o serviço");
        }
        tokens.excluirPorServico(id);
        servicos.delete(servico);
        servicos.flush();
    }
}
