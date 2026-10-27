package br.ufpr.tcc.backend_lab.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import br.ufpr.tcc.backend_lab.domain.model.entity.Amostra;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.InstrumentoLaboratorial;
import br.ufpr.tcc.backend_lab.domain.model.entity.MedicaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import java.util.List;
import java.util.Comparator;

public final class EnsaioDtos {
    private EnsaioDtos() { }

    public record Pagina<T>(List<T> items, int page, int pageSize, long totalItems) { }
    public record OrcamentoRef(String id, String code, String companyId, String companyName) { }
    public record EquipamentoAnalisado(String id, String tag, String serialNumber) { }
    public record AmostraRef(String id, String code, String description, OrcamentoRef quote,
            EquipamentoAnalisado analyzedEquipment) { }
    public record AmostraResumo(String id, String code, String description) { }
    public record TecnicoRef(String id, String name, String unit) { }
    public record ResponsavelRef(String id, String name, String unit, boolean active) { }
    public record InstrumentoRef(String id, String inventoryNumber, String name,
            String calibrationCertificate, String calibrationValidUntil) { }
    public record Medicao(String code, String name, String unit, String method,
            String referenceLimit, Resultado result) { }
    @JsonInclude(Include.NON_NULL)
    public record Resultado(String kind, String value, String text) { }
    public record Resumo(String id, String code, AmostraResumo sample, OrcamentoRef quote,
            String type, String analysisType, String scheduledDate, ResponsavelRef responsible,
            String status, long version) { }
    public record Detalhe(String id, String code, AmostraResumo sample, OrcamentoRef quote,
            String type, String analysisType, String method, String standard, String scheduledDate,
            ResponsavelRef responsible, String status, long version,
            EquipamentoAnalisado analyzedEquipment, List<String> equipmentIds,
            List<InstrumentoRef> equipment, String analysisClassification, String reportText,
            List<Medicao> measurements) { }
    public record TemplateLinha(String code, String name, String unit, String method,
            String referenceLimit) { }
    public record Template(String analysisType, List<TemplateLinha> measurements) { }

    public static AmostraRef amostra(Amostra amostra) {
        EquipamentoAnalisado equipamento = amostra.getEquipamentoAnalisado() == null ? null
                : new EquipamentoAnalisado(amostra.getEquipamentoAnalisado().getId().toString(),
                        amostra.getEquipamentoAnalisado().getTag(), amostra.getEquipamentoAnalisado().getNumeroSerie());
        return new AmostraRef(amostra.getId().toString(), amostra.getCodigo(), amostra.getDescricao(),
                orcamento(amostra.getOrcamento()), equipamento);
    }

    public static OrcamentoRef orcamento(Orcamento orcamento) {
        Empresa empresa = orcamento.getEmpresa();
        return new OrcamentoRef(orcamento.getId().toString(), orcamento.getCode(),
                empresa.getId().toString(), empresa.getLegalName());
    }

    public static InstrumentoRef instrumento(InstrumentoLaboratorial instrumento) {
        return new InstrumentoRef(instrumento.getId().toString(), instrumento.getNumeroPatrimonio(),
                instrumento.getNome(), instrumento.getCertificadoCalibracao(),
                instrumento.getCalibracaoValidaAte() == null ? null : instrumento.getCalibracaoValidaAte().toString());
    }

    public static Resumo resumo(ExecucaoEnsaio ensaio) {
        Amostra amostra = ensaio.getAmostra();
        ResponsavelRef responsavel = new ResponsavelRef(ensaio.getResponsavel().getId().toString(),
                ensaio.getResponsavel().getNome(), ensaio.getResponsavel().getUnidade(), ensaio.getResponsavel().isAtivo());
        return new Resumo(ensaio.getId().toString(), ensaio.getCodigo(),
                new AmostraResumo(amostra.getId().toString(), amostra.getCodigo(), amostra.getDescricao()),
                orcamento(ensaio.getOrcamento()), ensaio.getTipo(), ensaio.getTipoAnalise(),
                ensaio.getDataAgendada().toString(), responsavel, ensaio.getStatus().codigo(), ensaio.getVersion());
    }

    public static Detalhe detalhe(ExecucaoEnsaio ensaio) {
        Resumo resumo = resumo(ensaio);
        List<Medicao> medicoes = ensaio.getMedicoes().stream().map(EnsaioDtos::medicao).toList();
        Amostra amostra = ensaio.getAmostra();
        EquipamentoAnalisado analisado = amostra.getEquipamentoAnalisado() == null ? null
                : new EquipamentoAnalisado(amostra.getEquipamentoAnalisado().getId().toString(),
                        amostra.getEquipamentoAnalisado().getTag(), amostra.getEquipamentoAnalisado().getNumeroSerie());
        List<InstrumentoRef> instrumentos = ensaio.getInstrumentos().stream().map(EnsaioDtos::instrumento)
                .sorted(Comparator.comparing(InstrumentoRef::inventoryNumber)).toList();
        return new Detalhe(resumo.id(), resumo.code(), resumo.sample(), resumo.quote(), resumo.type(),
                resumo.analysisType(), ensaio.getMetodo(), ensaio.getNorma(), resumo.scheduledDate(),
                resumo.responsible(), resumo.status(), resumo.version(), analisado,
                instrumentos.stream().map(InstrumentoRef::id).toList(), instrumentos,
                ensaio.getClassificacaoAnalise(), ensaio.getTextoLaudo(), medicoes);
    }

    private static Medicao medicao(MedicaoEnsaio medicao) {
        return new Medicao(medicao.getCodigo(), medicao.getNome(), medicao.getUnidade(),
                medicao.getMetodo(), medicao.getLimiteReferencia(), resultado(medicao));
    }

    private static Resultado resultado(MedicaoEnsaio medicao) {
        return switch (medicao.getTipoResultado()) {
            case "quantity" -> new Resultado("quantity", medicao.getQuantidade().stripTrailingZeros().toPlainString(), null);
            case "text" -> new Resultado("text", null, medicao.getResultadoTextual());
            default -> new Resultado(medicao.getTipoResultado(), null, null);
        };
    }
}
