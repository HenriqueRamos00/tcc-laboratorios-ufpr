package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.ValidarRelatorioRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.application.service.GeradorPdfRelatorio;
import br.ufpr.tcc.backend_lab.application.service.MontadorSnapshotRelatorio;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoRelatorioException;
import br.ufpr.tcc.backend_lab.domain.exception.DadosRelatorioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.exception.RelatorioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoRelatorio;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoRelatorioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidarRelatorioUseCase {
    private final RelatorioTecnicoRepository relatorios;
    private final ExecucaoEnsaioRepository ensaios;
    private final DocumentoRelatorioRepository documentos;
    private final UsuarioRepository usuarios;
    private final MontadorSnapshotRelatorio snapshots;
    private final GeradorPdfRelatorio gerador;

    public ValidarRelatorioUseCase(RelatorioTecnicoRepository relatorios,
            ExecucaoEnsaioRepository ensaios, DocumentoRelatorioRepository documentos, UsuarioRepository usuarios,
            MontadorSnapshotRelatorio snapshots, GeradorPdfRelatorio gerador) {
        this.relatorios = relatorios;
        this.ensaios = ensaios;
        this.documentos = documentos;
        this.usuarios = usuarios;
        this.snapshots = snapshots;
        this.gerador = gerador;
    }

    @Transactional
    public RelatorioDtos.Validacao executar(String id, ValidarRelatorioRequest request, Long tecnicoId) {
        if (request == null || request.version() == null || request.version() < 0
                || request.contentFingerprint() == null
                || !request.contentFingerprint().matches("[0-9a-f]{64}")) {
            throw new DadosRelatorioInvalidosException(
                    "Versão e fingerprint do conteúdo revisado são obrigatórios e devem ser válidos");
        }
        if (tecnicoId == null || tecnicoId < 1) {
            throw new DadosRelatorioInvalidosException("Técnico autenticado não identificado");
        }
        long chave = ObterRelatorioUseCase.id(id);
        var referencia = relatorios.findById(chave)
                .orElseThrow(() -> new RelatorioNaoEncontradoException("Relatório não encontrado"));
        Long execucaoId = referencia.getExecucao().getId();
        ensaios.buscarParaEditar(execucaoId)
                .orElseThrow(() -> new RelatorioNaoEncontradoException("Ensaio do relatório não encontrado"));
        var execucaoAtual = ensaios.buscarCompleto(execucaoId)
                .orElseThrow(() -> new RelatorioNaoEncontradoException("Ensaio do relatório não encontrado"));
        var relatorio = relatorios.buscarParaValidacao(chave)
                .orElseThrow(() -> new RelatorioNaoEncontradoException("Relatório não encontrado"));
        if (request.version() != relatorio.getVersion()) {
            throw new ConflitoRelatorioException("O relatório foi alterado por outra sessão",
                    "report-version-conflict");
        }
        if (relatorio.getValidationStatus() == StatusValidacaoRelatorio.VALIDATED) {
            throw new ConflitoRelatorioException("O relatório já foi validado",
                    "report-already-validated");
        }
        var tecnico = usuarios.findById(tecnicoId)
                .filter(usuario -> usuario.isAtivo() && usuario.getPerfil() == PerfilUsuario.TECNICO)
                .orElseThrow(() -> new DadosRelatorioInvalidosException("Técnico autenticado não está ativo"));
        if (documentos.existsById(chave)) {
            throw new IllegalStateException("Documento inesperado para relatório pendente");
        }

        var snapshot = snapshots.atual(execucaoAtual);
        if (!snapshots.fingerprint(snapshot).equals(request.contentFingerprint())) {
            throw new ConflitoRelatorioException(
                    "Os dados técnicos mudaram desde a leitura do relatório. Recarregue o detalhe.",
                    "report-content-changed");
        }
        if (snapshot.quote().protocol() == null || snapshot.quote().protocol().isBlank()) {
            throw new DadosRelatorioInvalidosException("Informe o protocolo no orçamento antes de validar o relatório");
        }
        if (snapshot.quote().laboratoryName() == null || snapshot.quote().laboratoryName().isBlank()) {
            throw new DadosRelatorioInvalidosException("Informe o laboratório no orçamento antes de validar o relatório");
        }
        if (snapshot.sample().code() == null || snapshot.sample().code().isBlank()) {
            throw new DadosRelatorioInvalidosException("Identifique a amostra do ensaio antes de validar o relatório");
        }
        if (snapshot.type() == null || snapshot.type().isBlank()) {
            throw new DadosRelatorioInvalidosException("Informe o tipo do ensaio antes de validar o relatório");
        }
        OffsetDateTime validadoEm = OffsetDateTime.now(ZoneOffset.UTC);
        RelatorioDtos.PessoaRef validador = new RelatorioDtos.PessoaRef(
                tecnico.getId().toString(), tecnico.getNome(), tecnico.getUnidade());
        byte[] pdf = gerador.gerarValidado(id, snapshot, validadoEm, validador);
        relatorio.setValidatedSnapshot(snapshots.serializar(snapshot));
        relatorio.setValidatedSearch(snapshots.termosDeBusca(snapshot));
        relatorio.setValidationStatus(StatusValidacaoRelatorio.VALIDATED);
        relatorio.setValidatedBy(tecnico);
        relatorio.setValidatedAt(validadoEm);
        relatorio.setValidatedCompany(execucaoAtual.getOrcamento().getEmpresa());
        relatorio.setValidatedQuote(execucaoAtual.getOrcamento());
        relatorio.setValidatedSample(execucaoAtual.getAmostra());
        var persisted = relatorios.saveAndFlush(relatorio);

        DocumentoRelatorio documento = new DocumentoRelatorio();
        documento.setRelatorio(persisted);
        documento.setContent(pdf);
        documentos.saveAndFlush(documento);
        return RelatorioDtos.validacao(persisted);
    }
}
