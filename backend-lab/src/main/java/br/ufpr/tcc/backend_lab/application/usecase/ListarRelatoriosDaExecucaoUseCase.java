package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.application.service.DisponibilidadeRelatorios;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorReferenciasEnsaio;
import br.ufpr.tcc.backend_lab.domain.exception.ExecucaoRelatorioNaoEncontradaException;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarRelatoriosDaExecucaoUseCase {
    private final ExecucaoEnsaioRepository ensaios;
    private final RelatorioTecnicoRepository relatorios;
    private final DisponibilidadeRelatorios disponibilidade;

    public ListarRelatoriosDaExecucaoUseCase(ExecucaoEnsaioRepository ensaios,
            RelatorioTecnicoRepository relatorios, DisponibilidadeRelatorios disponibilidade) {
        this.ensaios = ensaios;
        this.relatorios = relatorios;
        this.disponibilidade = disponibilidade;
    }

    @Transactional(readOnly = true)
    public RelatorioDtos.RelatoriosDaExecucao executar(String testId) {
        Long id = ResolvedorReferenciasEnsaio.id(testId);
        var execucao = ensaios.findById(id).orElseThrow(() -> new ExecucaoRelatorioNaoEncontradaException(
                "Ensaio não encontrado"));
        List<RelatorioDtos.RelatorioDaExecucao> items = relatorios.findByExecucaoIdOrderByIdAsc(id)
                .stream().map(RelatorioDtos::relatorioDaExecucao).toList();
        return new RelatorioDtos.RelatoriosDaExecucao(items,
                disponibilidade.existePublicadoDisponivel(id, execucao));
    }
}
