package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.PublicarRelatorioRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.AtualizarStatusEnsaioRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.application.service.DisponibilidadeRelatorios;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoRelatorioException;
import br.ufpr.tcc.backend_lab.domain.exception.DadosRelatorioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.exception.RelatorioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicarRelatorioUseCase {
    private final RelatorioTecnicoRepository relatorios;
    private final UsuarioRepository usuarios;
    private final DisponibilidadeRelatorios disponibilidade;
    private final ExecucaoEnsaioRepository ensaios;
    private final AtualizarStatusEnsaioUseCase atualizarStatus;
    private final EntityManager entityManager;

    public PublicarRelatorioUseCase(RelatorioTecnicoRepository relatorios,
            UsuarioRepository usuarios, DisponibilidadeRelatorios disponibilidade,
            ExecucaoEnsaioRepository ensaios, AtualizarStatusEnsaioUseCase atualizarStatus,
            EntityManager entityManager) {
        this.relatorios = relatorios;
        this.usuarios = usuarios;
        this.disponibilidade = disponibilidade;
        this.ensaios = ensaios;
        this.atualizarStatus = atualizarStatus;
        this.entityManager = entityManager;
    }

    @Transactional
    public RelatorioDtos.Publicacao executar(String reportId, PublicarRelatorioRequest request,
            Long tecnicoId) {
        if (request == null || request.version() == null || request.version() < 0) {
            throw new DadosRelatorioInvalidosException(
                    "Versão do relatório é obrigatória e deve ser zero ou positiva");
        }
        if (tecnicoId == null || tecnicoId < 1) {
            throw new DadosRelatorioInvalidosException("Técnico autenticado não identificado");
        }
        long id = ObterRelatorioUseCase.id(reportId);
        var referencia = relatorios.findById(id)
                .orElseThrow(() -> new RelatorioNaoEncontradoException("Relatório não encontrado"));
        var ensaio = ensaios.buscarParaEditar(referencia.getExecucao().getId())
                .orElseThrow(() -> new RelatorioNaoEncontradoException("Ensaio do relatório não encontrado"));
        var relatorio = relatorios.buscarParaValidacao(id)
                .orElseThrow(() -> new RelatorioNaoEncontradoException("Relatório não encontrado"));
        if (request.version() != relatorio.getVersion()) {
            throw new ConflitoRelatorioException("O relatório foi alterado por outra sessão",
                    "report-version-conflict");
        }
        if (relatorio.isPublished()) {
            throw new ConflitoRelatorioException("O relatório já está publicado",
                    "report-already-published");
        }
        if (relatorio.getValidationStatus() != StatusValidacaoRelatorio.VALIDATED) {
            throw new ConflitoRelatorioException("Somente relatório validado pode ser publicado",
                    "report-not-validated");
        }
        entityManager.refresh(ensaio.getOrcamento(), LockModeType.PESSIMISTIC_WRITE);
        if (!DisponibilidadeRelatorios.correspondeAoVinculo(relatorio, ensaio)) {
            throw new ConflitoRelatorioException(
                    "O vínculo do ensaio mudou. Prepare e valide um novo relatório",
                    "report-commercial-link-changed");
        }
        var documento = disponibilidade.documentoPersistido(relatorio)
                .orElseThrow(() -> new ConflitoRelatorioException(
                        "O PDF validado não está acessível para publicação",
                        "validated-document-unavailable"));
        if (documento.getContent() == null) {
            throw new ConflitoRelatorioException("O PDF validado não está acessível para publicação",
                    "validated-document-unavailable");
        }
        var tecnico = usuarios.findById(tecnicoId)
                .filter(usuario -> usuario.isAtivo() && usuario.getPerfil() == PerfilUsuario.TECNICO)
                .orElseThrow(() -> new DadosRelatorioInvalidosException(
                        "Técnico autenticado não está ativo"));

        relatorio.setPublished(true);
        relatorio.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        relatorio.setPublishedBy(tecnico);
        relatorios.saveAndFlush(relatorio);
        atualizarStatus.executar(ensaio.getId().toString(),
                new AtualizarStatusEnsaioRequest("set-status", "report-published",
                        "Relatório publicado para o cliente", ensaio.getVersion()), tecnicoId);
        return RelatorioDtos.publicacao(relatorio);
    }
}
