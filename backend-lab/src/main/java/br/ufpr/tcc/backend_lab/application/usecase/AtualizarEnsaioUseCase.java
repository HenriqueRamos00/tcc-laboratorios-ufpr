package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.EnsaioRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.application.service.AplicadorDadosEnsaio;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorReferenciasEnsaio;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoEdicaoEnsaioException;
import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.exception.EnsaioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AtualizarEnsaioUseCase {
    private final ExecucaoEnsaioRepository ensaios;
    private final AplicadorDadosEnsaio aplicador;

    public AtualizarEnsaioUseCase(ExecucaoEnsaioRepository ensaios, AplicadorDadosEnsaio aplicador) {
        this.ensaios = ensaios;
        this.aplicador = aplicador;
    }

    @Transactional
    public EnsaioDtos.Detalhe executar(String id, EnsaioRequest request) {
        if (request == null) throw new DadosEnsaioInvalidosException("Dados do ensaio são obrigatórios");
        if (request.version() == null || request.version() < 0) {
            throw new DadosEnsaioInvalidosException("Versão do ensaio é obrigatória");
        }
        Long chave = ResolvedorReferenciasEnsaio.id(id);
        ExecucaoEnsaio ensaio = ensaios.buscarParaEditar(chave)
                .orElseThrow(() -> new EnsaioNaoEncontradoException("Ensaio não encontrado"));
        if (request.version() != ensaio.getVersion()) {
            throw new ConflitoEdicaoEnsaioException("O ensaio foi alterado por outra sessão");
        }
        aplicador.aplicar(ensaio, request, true);
        return EnsaioDtos.detalhe(ensaios.saveAndFlush(ensaio));
    }
}
