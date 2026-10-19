package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoDetalheInternoResponse;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetOrcamentoUseCase {
    private final OrcamentoRepository orcamentoRepository;
    private final DocumentoOrcamentoRepository documentoOrcamentoRepository;

    @Transactional(readOnly = true)
    public OrcamentoDetalheInternoResponse execute(Long orcamentoId) {
        var orcamento =
                orcamentoRepository
                        .buscarDetalhePorId(orcamentoId)
                        .orElseThrow(
                                () ->
                                        RegraNegocioException.naoEncontrado(
                                                "Orçamento não encontrado"));
        boolean hasDocument = documentoOrcamentoRepository.existsById(orcamentoId);
        return OrcamentoDetalheInternoResponse.from(orcamento, hasDocument);
    }
}
