package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Amostra;
import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.InstrumentoLaboratorial;
import br.ufpr.tcc.backend_lab.domain.model.entity.MedicaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.RelatorioTecnico;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.application.dto.RelatorioSnapshot;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

public final class RelatorioDtos {
    private RelatorioDtos() { }

    public record Pagina<T>(List<T> items, int page, int pageSize, long totalItems) { }
    public record EmpresaRef(String id, String name) { }
    public record OrcamentoRef(String id, String code, String protocol, String laboratoryName) { }
    public record AmostraRef(String id, String code, String description) { }
    public record PessoaRef(String id, String name, String unit) { }
    public record EquipamentoRef(String id, String tag, String serialNumber) { }
    public record InstrumentoRef(String id, String inventoryNumber, String name,
            String calibrationCertificate, String calibrationValidUntil) { }
    public record Resultado(String kind, String value, String text) { }
    public record Medicao(String code, String name, String unit, String method,
            String referenceLimit, Resultado result) { }
    public record Ensaio(String id, String code, String type, String analysisType,
            String method, String standard, String scheduledDate, String technicalStatus,
            PessoaRef responsible, EquipamentoRef analyzedEquipment,
            List<InstrumentoRef> instruments, String analysisClassification,
            String reportText, List<Medicao> measurements) { }
    public record Item(String id, EmpresaRef company, OrcamentoRef quote, AmostraRef sample,
            String type, String analysisType, String validationStatus, boolean published,
            boolean pdfAvailable, OffsetDateTime createdAt,
            OffsetDateTime validatedAt, OffsetDateTime publishedAt,
            long version) { }
    public record Detalhe(String id, EmpresaRef company, OrcamentoRef quote, AmostraRef sample,
            String type, String analysisType, String validationStatus, boolean published,
            boolean pdfAvailable, OffsetDateTime createdAt,
            OffsetDateTime validatedAt, PessoaRef validatedBy,
            OffsetDateTime publishedAt, PessoaRef publishedBy, long version,
            String contentFingerprint, Ensaio test) { }
    public record Validacao(String id, String validationStatus, boolean published,
            OffsetDateTime validatedAt, PessoaRef validatedBy,
            OffsetDateTime publishedAt, long version, boolean pdfAvailable) { }
    public record Publicacao(String id, String validationStatus, boolean published,
            OffsetDateTime publishedAt, PessoaRef publishedBy, long version,
            boolean pdfAvailable) { }
    public record RelatorioDaExecucao(String id, String validationStatus, boolean published,
            OffsetDateTime publishedAt, long version) { }
    public record RelatoriosDaExecucao(List<RelatorioDaExecucao> items,
            boolean publishedDocumentAvailable) { }
    public record Preparacao(String outcome, String reportId, List<RelatorioDaExecucao> reports) { }
    public record PublicadoCliente(String id, String code, String protocol, String sample,
            String laboratory, OffsetDateTime publishedOn, String testType) { }
    public record ConclusaoCliente(String protocol, String testType, String summary,
            List<PublicadoCliente> reports) { }

    public static Item item(RelatorioTecnico relatorio, RelatorioSnapshot snapshot, boolean pdfAvailable) {
        if (snapshot == null) {
            ExecucaoEnsaio ensaio = relatorio.getExecucao();
            Amostra amostra = ensaio.getAmostra();
            Orcamento orcamento = ensaio.getOrcamento();
            return new Item(relatorio.getId().toString(), empresa(orcamento), orcamento(orcamento),
                    amostra(amostra), ensaio.getTipo(), ensaio.getTipoAnalise(),
                    relatorio.getValidationStatus().codigo(), relatorio.isPublished(), pdfAvailable,
                    relatorio.getCreatedAt(), relatorio.getValidatedAt(), relatorio.getPublishedAt(),
                    relatorio.getVersion());
        }
        return new Item(relatorio.getId().toString(), snapshot.company(), snapshot.quote(),
                snapshot.sample(), snapshot.type(), snapshot.analysisType(),
                relatorio.getValidationStatus().codigo(), relatorio.isPublished(), pdfAvailable,
                relatorio.getCreatedAt(), relatorio.getValidatedAt(), relatorio.getPublishedAt(),
                relatorio.getVersion());
    }

    public static Detalhe detalhe(RelatorioTecnico relatorio, RelatorioSnapshot snapshot,
            boolean pdfAvailable, String contentFingerprint) {
        PessoaRef validador = relatorio.getValidatedBy() == null ? null : pessoa(relatorio.getValidatedBy());
        return new Detalhe(relatorio.getId().toString(), snapshot.company(), snapshot.quote(),
                snapshot.sample(), snapshot.type(), snapshot.analysisType(),
                relatorio.getValidationStatus().codigo(), relatorio.isPublished(), pdfAvailable,
                relatorio.getCreatedAt(), relatorio.getValidatedAt(), validador,
                relatorio.getPublishedAt(), pessoa(relatorio.getPublishedBy()),
                relatorio.getVersion(), contentFingerprint, snapshot.test());
    }

    public static Validacao validacao(RelatorioTecnico relatorio) {
        return new Validacao(relatorio.getId().toString(), relatorio.getValidationStatus().codigo(),
                relatorio.isPublished(), relatorio.getValidatedAt(), pessoa(relatorio.getValidatedBy()),
                relatorio.getPublishedAt(), relatorio.getVersion(), true);
    }

    public static Publicacao publicacao(RelatorioTecnico relatorio) {
        return new Publicacao(relatorio.getId().toString(), relatorio.getValidationStatus().codigo(),
                relatorio.isPublished(), relatorio.getPublishedAt(), pessoa(relatorio.getPublishedBy()),
                relatorio.getVersion(), true);
    }

    public static RelatorioDaExecucao relatorioDaExecucao(RelatorioTecnico relatorio) {
        return new RelatorioDaExecucao(relatorio.getId().toString(),
                relatorio.getValidationStatus().codigo(), relatorio.isPublished(),
                relatorio.getPublishedAt(), relatorio.getVersion());
    }

    public static PublicadoCliente publicadoCliente(RelatorioTecnico relatorio, RelatorioSnapshot snapshot) {
        AmostraRef amostra = snapshot.sample();
        String codigo = amostra.code();
        String descricao = amostra.description();
        String identificacaoAmostra = codigo == null || codigo.isBlank() ? descricao
                : descricao == null || descricao.isBlank() ? codigo : codigo + " - " + descricao;
        String id = relatorio.getId().toString();
        return new PublicadoCliente(id, id, snapshot.quote().protocol(), identificacaoAmostra,
                snapshot.quote().laboratoryName(),
                relatorio.getPublishedAt(), snapshot.type());
    }

    public static RelatorioSnapshot snapshot(ExecucaoEnsaio ensaio) {
        Amostra amostra = ensaio.getAmostra();
        Orcamento orcamento = ensaio.getOrcamento();
        Equipamento equipamento = amostra.getEquipamentoAnalisado();
        EquipamentoRef equipamentoRef = equipamento == null ? null
                : new EquipamentoRef(equipamento.getId().toString(), equipamento.getTag(), equipamento.getNumeroSerie());
        List<InstrumentoRef> instrumentos = ensaio.getInstrumentos().stream()
                .sorted(Comparator.comparing(InstrumentoLaboratorial::getNumeroPatrimonio))
                .map(RelatorioDtos::instrumento).toList();
        List<Medicao> medicoes = ensaio.getMedicoes().stream()
                .sorted(Comparator.comparingInt(MedicaoEnsaio::getPosicao))
                .map(RelatorioDtos::medicao).toList();
        Ensaio detalheEnsaio = new Ensaio(ensaio.getId().toString(), ensaio.getCodigo(), ensaio.getTipo(),
                ensaio.getTipoAnalise(), ensaio.getMetodo(), ensaio.getNorma(),
                ensaio.getDataAgendada().toString(), ensaio.getStatus().codigo(),
                pessoa(ensaio.getResponsavel()), equipamentoRef, instrumentos,
                ensaio.getClassificacaoAnalise(), ensaio.getTextoLaudo(), medicoes);
        return new RelatorioSnapshot(empresa(orcamento), orcamento(orcamento), amostra(amostra),
                ensaio.getTipo(), ensaio.getTipoAnalise(), detalheEnsaio);
    }

    private static EmpresaRef empresa(Orcamento orcamento) {
        return new EmpresaRef(orcamento.getEmpresa().getId().toString(), orcamento.getEmpresa().getLegalName());
    }

    private static OrcamentoRef orcamento(Orcamento orcamento) {
        return new OrcamentoRef(orcamento.getId().toString(), orcamento.getCode(),
                orcamento.getProtocol(), orcamento.getLaboratoryName());
    }

    private static AmostraRef amostra(Amostra amostra) {
        return new AmostraRef(amostra.getId().toString(), amostra.getCodigo(), amostra.getDescricao());
    }

    private static PessoaRef pessoa(Usuario usuario) {
        return usuario == null ? null : new PessoaRef(usuario.getId().toString(), usuario.getNome(), usuario.getUnidade());
    }

    private static InstrumentoRef instrumento(InstrumentoLaboratorial instrumento) {
        return new InstrumentoRef(instrumento.getId().toString(), instrumento.getNumeroPatrimonio(),
                instrumento.getNome(), instrumento.getCertificadoCalibracao(),
                instrumento.getCalibracaoValidaAte() == null ? null : instrumento.getCalibracaoValidaAte().toString());
    }

    private static Medicao medicao(MedicaoEnsaio medicao) {
        String kind = medicao.getTipoResultado();
        Resultado resultado = switch (kind) {
            case "quantity" -> new Resultado(kind, medicao.getQuantidade().toPlainString(), null);
            case "text" -> new Resultado(kind, null, medicao.getResultadoTextual());
            case "not-detected", "not-performed" -> new Resultado(kind, null, null);
            default -> throw new IllegalStateException("Tipo de resultado de medição desconhecido: " + kind);
        };
        return new Medicao(medicao.getCodigo(), medicao.getNome(), medicao.getUnidade(),
                medicao.getMetodo(), medicao.getLimiteReferencia(), resultado);
    }
}
