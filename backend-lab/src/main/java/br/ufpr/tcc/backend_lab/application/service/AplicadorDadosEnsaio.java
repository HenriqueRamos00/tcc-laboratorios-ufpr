package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.application.dto.request.EnsaioRequest;
import br.ufpr.tcc.backend_lab.application.validation.ValidadorDadosEnsaio;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.MedicaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Component;

@Component
public class AplicadorDadosEnsaio {
    private final ResolvedorReferenciasEnsaio referencias;
    private final ValidadorDadosEnsaio validador;
    private final ExecucaoEnsaioRepository ensaios;

    private final RelatorioTecnicoRepository relatorios;

    public AplicadorDadosEnsaio(ResolvedorReferenciasEnsaio referencias,
            ValidadorDadosEnsaio validador, ExecucaoEnsaioRepository ensaios,
            RelatorioTecnicoRepository relatorios) {
        this.referencias = referencias;
        this.validador = validador;
        this.ensaios = ensaios;
        this.relatorios = relatorios;
    }

    public void aplicar(ExecucaoEnsaio ensaio, EnsaioRequest request, boolean atualizar) {
        var metadados = validador.validarMetadados(request);
        List<MedicaoEnsaio> medicoes = validador.validarMedicoes(request.measurements());
        Long orcamentoOriginalId = atualizar ? ensaio.getOrcamento().getId() : null;
        var referenciasResolvidas = referencias.resolver(
                request, atualizar ? ensaio.getResponsavel() : null, orcamentoOriginalId);
        if (atualizar && !orcamentoOriginalId.equals(referenciasResolvidas.amostra().getOrcamento().getId())
                && relatorios.existsByExecucaoIdAndPublishedTrue(ensaio.getId())) {
            throw RegraNegocioException.conflito(
                    "Não é possível trocar o orçamento de um ensaio com relatório publicado");
        }
        ensaio.setAmostra(referenciasResolvidas.amostra());
        ensaio.setOrcamento(referenciasResolvidas.amostra().getOrcamento());
        ensaio.setResponsavel(referenciasResolvidas.responsavel());
        ensaio.setTipo(metadados.type());
        ensaio.setTipoAnalise(metadados.analysisType());
        ensaio.setMetodo(metadados.method());
        ensaio.setNorma(metadados.standard());
        ensaio.setDataAgendada(metadados.scheduledDate());
        ensaio.setClassificacaoAnalise(metadados.classification());
        ensaio.setTextoLaudo(metadados.reportText());
        ensaio.setInstrumentos(new LinkedHashSet<>(referenciasResolvidas.instrumentos()));
        ensaio.setAtualizadoEm(OffsetDateTime.now());
        ensaio.getMedicoes().clear();
        if (atualizar) ensaios.flush();
        for (MedicaoEnsaio medicao : medicoes) {
            medicao.setExecucao(ensaio);
            ensaio.getMedicoes().add(medicao);
        }
    }
}
