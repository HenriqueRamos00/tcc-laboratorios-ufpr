package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;
import br.ufpr.tcc.backend_lab.application.dto.response.EmpresaResponse;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangeEmpresaStatusUseCase {
    private final EmpresaRepository repository;

    @Transactional
    public EmpresaResponse execute(Long id, CadastroStatus status) {
        var e =
                repository
                        .findById(id)
                        .orElseThrow(
                                () -> RegraNegocioException.naoEncontrado("Empresa não encontrada"));
        e.setAtivo(status.toAtivo());
        return EmpresaResponse.from(repository.saveAndFlush(e));
    }
}
