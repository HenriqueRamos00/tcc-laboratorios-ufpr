package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.service.AcessoClienteAtual;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetDocumentoOrcamentoClienteUseCase {
    private final DocumentoOrcamentoRepository documentoOrcamentoRepository;
    private final OrcamentoRepository orcamentoRepository;
    private final AcessoClienteAtual acessoClienteAtual;

    @Transactional(readOnly = true)
    public byte[] execute(Long usuarioId, Long orcamentoId) {
        var cliente = acessoClienteAtual.execute(usuarioId);
        var orcamento =
                orcamentoRepository
                        .findById(orcamentoId)
                        .orElseThrow(
                                () ->
                                        RegraNegocioException.naoEncontrado(
                                                "Orçamento não encontrado"));
        acessoClienteAtual.autorizar(cliente, orcamento);
        return documentoOrcamentoRepository
                .findById(orcamentoId)
                .orElseThrow(
                        () ->
                                RegraNegocioException.naoEncontrado(
                                        "Documento não disponível para este orçamento"))
                .getContent();
    }
}
