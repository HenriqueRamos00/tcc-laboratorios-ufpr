package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoTecnicoDetalheResponse;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.OrcamentoTecnicoConsultaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetOrcamentoTecnicoUseCase {
    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoTecnicoConsultaRepository consultaTecnica;

    @Transactional(readOnly = true)
    public OrcamentoTecnicoDetalheResponse execute(Long orcamentoId) {
        var orcamento =
                orcamentoRepository
                        .buscarDetalhePorId(orcamentoId)
                        .orElseThrow(
                                () ->
                                        RegraNegocioException.naoEncontrado(
                                                "Orçamento não encontrado"));
        var status = consultaTecnica.buscarStatus(orcamentoId).orElse(null);
        return OrcamentoTecnicoDetalheResponse.from(orcamento, status);
    }
}
