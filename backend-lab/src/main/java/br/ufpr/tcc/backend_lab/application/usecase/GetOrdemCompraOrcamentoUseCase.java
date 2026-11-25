package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.ArquivoOrdemCompraResponse;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.repository.OrdemCompraOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetOrdemCompraOrcamentoUseCase {
    private final OrcamentoRepository orcamentoRepository;
    private final OrdemCompraOrcamentoRepository ordemCompraRepository;

    @Transactional(readOnly = true)
    public ArquivoOrdemCompraResponse execute(Long orcamentoId) {
        if (!orcamentoRepository.existsById(orcamentoId)) {
            throw RegraNegocioException.naoEncontrado("Orçamento não encontrado");
        }
        return ordemCompraRepository
                .findById(orcamentoId)
                .map(arquivo -> new ArquivoOrdemCompraResponse(
                        arquivo.getContent(), arquivo.getFileName(), arquivo.getContentType()))
                .orElseThrow(() -> RegraNegocioException.naoEncontrado(
                        "Ordem de compra não disponível para este orçamento"));
    }
}
