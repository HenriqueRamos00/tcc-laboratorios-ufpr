package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorReferenciasEnsaio;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoRelatorioException;
import br.ufpr.tcc.backend_lab.domain.exception.ExecucaoRelatorioNaoEncontradaException;
import br.ufpr.tcc.backend_lab.domain.model.entity.RelatorioTecnico;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrepararRelatorioUseCase {
    private final ExecucaoEnsaioRepository ensaios;
    private final RelatorioTecnicoRepository relatorios;

    public PrepararRelatorioUseCase(ExecucaoEnsaioRepository ensaios,
            RelatorioTecnicoRepository relatorios) {
        this.ensaios = ensaios;
        this.relatorios = relatorios;
    }

    @Transactional
    public RelatorioDtos.Preparacao executar(String testId) {
        Long id = ResolvedorReferenciasEnsaio.id(testId);
        var ensaio = ensaios.buscarParaPrepararRelatorio(id)
                .orElseThrow(() -> new ExecucaoRelatorioNaoEncontradaException("Ensaio não encontrado"));

        List<RelatorioTecnico> existentes = relatorios.findByExecucaoIdOrderByIdAsc(id);
        if (!existentes.isEmpty()) {
            return prepararResposta("existing", existentes);
        }
        if (ensaio.getStatus() != StatusEnsaio.DRAFTING_REPORT) {
            throw new ConflitoRelatorioException(
                    "Só é possível preparar relatório para ensaio em Elaborando relatório",
                    "report-preparation-state-invalid");
        }

        RelatorioTecnico novo = new RelatorioTecnico();
        novo.setExecucao(ensaio);
        RelatorioTecnico salvo = relatorios.saveAndFlush(novo);
        return prepararResposta("created", List.of(salvo));
    }

    private RelatorioDtos.Preparacao prepararResposta(String resultado,
            List<RelatorioTecnico> existentes) {
        List<RelatorioDtos.RelatorioDaExecucao> items = existentes.stream()
                .map(RelatorioDtos::relatorioDaExecucao).toList();
        if (existentes.size() > 1) {
            return new RelatorioDtos.Preparacao("selection-required", null, items);
        }
        String reportId = existentes.getFirst().getId().toString();
        return new RelatorioDtos.Preparacao(resultado, reportId, items);
    }
}
