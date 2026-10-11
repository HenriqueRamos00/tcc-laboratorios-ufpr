package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;
import br.ufpr.tcc.backend_lab.application.dto.response.ClienteResponse;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangeClienteStatusUseCase {
    private final ClienteRepository repository;

    @Transactional
    public ClienteResponse execute(Long id, CadastroStatus status) {
        var c =
                repository
                        .findById(id)
                        .orElseThrow(
                                () -> RegraNegocioException.naoEncontrado("Cliente não encontrado"));
        c.getUsuario().setAtivo(status.toAtivo());
        return ClienteResponse.from(repository.saveAndFlush(c));
    }
}
