package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoRelatorio;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.RelatorioTecnico;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoRelatorioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DisponibilidadeRelatorios {
    private final RelatorioTecnicoRepository relatorios;
    private final DocumentoRelatorioRepository documentos;

    public DisponibilidadeRelatorios(RelatorioTecnicoRepository relatorios,
            DocumentoRelatorioRepository documentos) {
        this.relatorios = relatorios;
        this.documentos = documentos;
    }

    public Optional<DocumentoRelatorio> documentoPersistido(RelatorioTecnico relatorio) {
        return documentos.findByRelatorioId(relatorio.getId())
                .filter(documento -> pdfValido(documento.getContent()));
    }

    public boolean pdfInternoDisponivel(RelatorioTecnico relatorio) {
        return relatorio.getValidationStatus() != StatusValidacaoRelatorio.VALIDATED
                || documentoPersistido(relatorio).isPresent();
    }

    public boolean publicadoDisponivel(RelatorioTecnico relatorio) {
        return relatorio.getValidationStatus() == StatusValidacaoRelatorio.VALIDATED
                && relatorio.isPublished()
                && documentoPersistido(relatorio).isPresent();
    }

    public Set<Long> idsPdfValidos(Collection<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(documentos.buscarIdsComPdfValido(reportIds));
    }

    public boolean pdfInternoDisponivel(RelatorioTecnico relatorio, Set<Long> idsPdfValidos) {
        return relatorio.getValidationStatus() != StatusValidacaoRelatorio.VALIDATED
                || idsPdfValidos.contains(relatorio.getId());
    }

    public boolean publicadoDisponivel(RelatorioTecnico relatorio, Set<Long> idsPdfValidos) {
        return relatorio.getValidationStatus() == StatusValidacaoRelatorio.VALIDATED
                && relatorio.isPublished()
                && idsPdfValidos.contains(relatorio.getId());
    }

    public boolean existePublicadoDisponivel(Long execucaoId, ExecucaoEnsaio execucaoAtual) {
        Long quoteIdAtual = execucaoAtual.getOrcamento().getId();
        Long companyIdAtual = execucaoAtual.getOrcamento().getEmpresa().getId();
        var candidatos = relatorios.buscarPublicadosDaExecucao(execucaoId, StatusValidacaoRelatorio.VALIDATED)
                .stream()
                .filter(relatorio -> relatorio.getValidatedQuote() != null
                        && relatorio.getValidatedCompany() != null
                        && relatorio.getValidatedQuote().getId().equals(quoteIdAtual)
                        && relatorio.getValidatedCompany().getId().equals(companyIdAtual))
                .toList();
        Set<Long> idsPdfValidos = idsPdfValidos(candidatos.stream().map(RelatorioTecnico::getId).toList());
        return candidatos.stream().anyMatch(relatorio -> publicadoDisponivel(relatorio, idsPdfValidos));
    }

    public static boolean pdfValido(byte[] bytes) {
        return DocumentoRelatorio.conteudoPdfValido(bytes);
    }
}
