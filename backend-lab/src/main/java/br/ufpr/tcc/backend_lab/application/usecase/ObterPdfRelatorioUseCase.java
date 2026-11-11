package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.PdfRelatorio;
import br.ufpr.tcc.backend_lab.application.service.GeradorPdfRelatorio;
import br.ufpr.tcc.backend_lab.application.service.MontadorSnapshotRelatorio;
import br.ufpr.tcc.backend_lab.application.service.DisponibilidadeRelatorios;
import br.ufpr.tcc.backend_lab.domain.exception.FalhaGeracaoPdfRelatorioException;
import br.ufpr.tcc.backend_lab.domain.exception.RelatorioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObterPdfRelatorioUseCase {
    private final RelatorioTecnicoRepository relatorios;
    private final MontadorSnapshotRelatorio snapshots;
    private final GeradorPdfRelatorio gerador;
    private final DisponibilidadeRelatorios disponibilidade;

    public ObterPdfRelatorioUseCase(RelatorioTecnicoRepository relatorios,
            MontadorSnapshotRelatorio snapshots, GeradorPdfRelatorio gerador,
            DisponibilidadeRelatorios disponibilidade) {
        this.relatorios = relatorios;
        this.snapshots = snapshots;
        this.gerador = gerador;
        this.disponibilidade = disponibilidade;
    }

    @Transactional(readOnly = true)
    public PdfRelatorio executar(String id) {
        long chave = ObterRelatorioUseCase.id(id);
        var relatorio = relatorios.buscarDetalhe(chave)
                .orElseThrow(() -> new RelatorioNaoEncontradoException("Relatório não encontrado"));
        if (relatorio.getValidationStatus() == StatusValidacaoRelatorio.VALIDATED) {
            var documento = disponibilidade.documentoPersistido(relatorio)
                    .orElseThrow(() -> new FalhaGeracaoPdfRelatorioException(
                            "O PDF validado não está disponível", new IllegalStateException("Arquivo ausente")));
            return new PdfRelatorio(documento.getContent(), "relatorio-" + chave + ".pdf",
                    "validated", relatorio.getVersion());
        }
        var snapshot = snapshots.atual(relatorio);
        byte[] content = gerador.gerarRascunho(id, snapshot, OffsetDateTime.now(ZoneOffset.UTC));
        return new PdfRelatorio(content, "relatorio-" + chave + "-rascunho.pdf",
                "draft", relatorio.getVersion());
    }
}
