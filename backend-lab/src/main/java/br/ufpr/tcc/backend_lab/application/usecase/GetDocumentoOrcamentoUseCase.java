package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetDocumentoOrcamentoUseCase {
    private final OrcamentoRepository orcamentoRepository;
    private final DocumentoOrcamentoRepository documentoOrcamentoRepository;

    @Transactional(readOnly = true)
    public byte[] execute(Long orcamentoId) {
        if (!orcamentoRepository.existsById(orcamentoId)) {
            throw RegraNegocioException.naoEncontrado("Orçamento não encontrado");
        }
        return documentoOrcamentoRepository
                .findById(orcamentoId)
                .map(documento -> documento.getContent())
                .orElseThrow(
                        () ->
                                RegraNegocioException.naoEncontrado(
                                        "Documento não disponível para este orçamento"));
    }
}
