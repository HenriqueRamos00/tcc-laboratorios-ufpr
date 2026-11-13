package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorReferenciasEnsaio;
import br.ufpr.tcc.backend_lab.domain.exception.EnsaioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.HistoricoStatusEnsaioRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarHistoricoStatusEnsaioUseCase {
    private final ExecucaoEnsaioRepository ensaios;
    private final HistoricoStatusEnsaioRepository historico;

    public ListarHistoricoStatusEnsaioUseCase(ExecucaoEnsaioRepository ensaios,
            HistoricoStatusEnsaioRepository historico) {
        this.ensaios = ensaios;
        this.historico = historico;
    }

    @Transactional(readOnly = true)
    public List<EnsaioDtos.HistoricoStatus> executar(String id) {
        Long chave = ResolvedorReferenciasEnsaio.id(id);
        if (!ensaios.existsById(chave)) {
            throw new EnsaioNaoEncontradoException("Ensaio não encontrado");
        }
        return historico.listarPorExecucao(chave).stream().map(EnsaioDtos::historicoStatus).toList();
    }
}
