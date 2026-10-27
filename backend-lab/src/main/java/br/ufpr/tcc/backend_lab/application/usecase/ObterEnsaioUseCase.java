package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorReferenciasEnsaio;
import br.ufpr.tcc.backend_lab.domain.exception.EnsaioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObterEnsaioUseCase {
    private final ExecucaoEnsaioRepository ensaios;
    public ObterEnsaioUseCase(ExecucaoEnsaioRepository ensaios) { this.ensaios = ensaios; }
    @Transactional(readOnly=true)
    public EnsaioDtos.Detalhe executar(String id) {
        return ensaios.buscarCompleto(ResolvedorReferenciasEnsaio.id(id))
                .map(EnsaioDtos::detalhe)
                .orElseThrow(() -> new EnsaioNaoEncontradoException("Ensaio não encontrado"));
    }
}
