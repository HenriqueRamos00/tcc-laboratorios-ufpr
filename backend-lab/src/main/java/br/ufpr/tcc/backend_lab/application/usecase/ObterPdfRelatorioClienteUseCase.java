package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.PdfRelatorio;
import br.ufpr.tcc.backend_lab.application.service.AcessoClienteAtual;
import br.ufpr.tcc.backend_lab.application.service.DisponibilidadeRelatorios;
import br.ufpr.tcc.backend_lab.domain.exception.RelatorioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObterPdfRelatorioClienteUseCase {
    private final RelatorioTecnicoRepository relatorios;
    private final AcessoClienteAtual acessoCliente;
    private final DisponibilidadeRelatorios disponibilidade;

    public ObterPdfRelatorioClienteUseCase(RelatorioTecnicoRepository relatorios,
            AcessoClienteAtual acessoCliente, DisponibilidadeRelatorios disponibilidade) {
        this.relatorios = relatorios;
        this.acessoCliente = acessoCliente;
        this.disponibilidade = disponibilidade;
    }

    @Transactional(readOnly = true)
    public PdfRelatorio executar(Long usuarioId, String reportId) {
        var cliente = acessoCliente.execute(usuarioId);
        long id = ObterRelatorioUseCase.id(reportId);
        var relatorio = relatorios.buscarDetalhe(id)
                .orElseThrow(() -> new RelatorioNaoEncontradoException("Relatório publicado não encontrado"));
        if (relatorio.getValidationStatus() != StatusValidacaoRelatorio.VALIDATED
                || !relatorio.isPublished() || relatorio.getValidatedCompany() == null
                || relatorio.getValidatedQuote() == null) {
            throw new RelatorioNaoEncontradoException("Relatório publicado não encontrado");
        }
        acessoCliente.autorizar(cliente, relatorio.getValidatedCompany());
        var documento = disponibilidade.documentoPersistido(relatorio)
                .orElseThrow(() -> new RelatorioNaoEncontradoException("Relatório publicado não encontrado"));
        return new PdfRelatorio(documento.getContent(), "relatorio-" + id + ".pdf",
                "published", relatorio.getVersion());
    }
}
