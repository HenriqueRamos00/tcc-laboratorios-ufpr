package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.DecisaoClienteInternaResponse;
import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoDetalheInternoResponse;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.enums.FormaAceiteOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.AceiteOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrdemCompraOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RecusaOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.TermoAceiteOrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetOrcamentoUseCase {
    private final OrcamentoRepository orcamentoRepository;
    private final DocumentoOrcamentoRepository documentoOrcamentoRepository;
    private final AceiteOrcamentoRepository aceiteRepository;
    private final OrdemCompraOrcamentoRepository ordemCompraRepository;
    private final TermoAceiteOrcamentoRepository termoRepository;
    private final RecusaOrcamentoRepository recusaRepository;

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
        var aceite = aceiteRepository.findById(orcamentoId).orElse(null);
        var termo =
                aceite != null && aceite.getMethod() == FormaAceiteOrcamento.TERMO_DE_ACEITE
                        ? termoRepository.findById(aceite.getId()).orElse(null)
                        : null;
        boolean ordemCompraDisponivel =
                aceite != null
                        && aceite.getMethod() == FormaAceiteOrcamento.ORDEM_DE_COMPRA
                        && ordemCompraRepository.existsById(aceite.getId());
        var recusa = recusaRepository.findById(orcamentoId).orElse(null);
        var decisaoCliente =
                DecisaoClienteInternaResponse.from(aceite, termo, ordemCompraDisponivel, recusa);
        return OrcamentoDetalheInternoResponse.from(orcamento, hasDocument, decisaoCliente);
    }
}
