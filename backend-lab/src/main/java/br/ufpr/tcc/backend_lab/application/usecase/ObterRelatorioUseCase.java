package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.application.service.MontadorSnapshotRelatorio;
import br.ufpr.tcc.backend_lab.application.service.DisponibilidadeRelatorios;
import br.ufpr.tcc.backend_lab.domain.exception.RelatorioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObterRelatorioUseCase {
    private final RelatorioTecnicoRepository relatorios;
    private final MontadorSnapshotRelatorio snapshots;
    private final DisponibilidadeRelatorios disponibilidade;

    public ObterRelatorioUseCase(RelatorioTecnicoRepository relatorios,
            MontadorSnapshotRelatorio snapshots, DisponibilidadeRelatorios disponibilidade) {
        this.relatorios = relatorios;
        this.snapshots = snapshots;
        this.disponibilidade = disponibilidade;
    }

    @Transactional(readOnly = true)
    public RelatorioDtos.Detalhe executar(String id) {
        long chave = id(id);
        var relatorio = relatorios.buscarDetalhe(chave)
                .orElseThrow(() -> new RelatorioNaoEncontradoException("Relatório não encontrado"));
        var snapshot = relatorio.getValidationStatus() == StatusValidacaoRelatorio.VALIDATED
                ? snapshots.conteudo(relatorio) : snapshots.atual(relatorio);
        return RelatorioDtos.detalhe(relatorio, snapshot,
                disponibilidade.pdfInternoDisponivel(relatorio), snapshots.fingerprint(snapshot));
    }

    static long id(String id) {
        try {
            long chave = Long.parseLong(id);
            if (chave < 1) {
                throw new NumberFormatException();
            }
            return chave;
        } catch (NumberFormatException exception) {
            throw new RelatorioNaoEncontradoException("Relatório não encontrado");
        }
    }
}
