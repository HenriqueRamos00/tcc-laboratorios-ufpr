package br.ufpr.tcc.backend_lab.application.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class ClienteResultadoDtos {
    private ClienteResultadoDtos() { }

    public record Pagina<T>(List<T> items, int page, int pageSize, long totalItems) { }

    public record EmpresaRef(String id, String name) { }
    public record OrcamentoRef(String id, String protocol) { }
    public record AmostraRef(String id, String code, String name) { }
    public record EquipamentoRef(String id, String serialNumber, String tag, String type) { }

    public record AnalisePublicadaRef(String id, String reportId, String testId,
            String testCode, String reportCode, String testType, String analysisType,
            String scheduledAt, OffsetDateTime validatedAt, OffsetDateTime publishedAt,
            String technicalClassification, String conclusion, boolean pdfAvailable) { }

    public record EquipamentoCliente(String id, EmpresaRef company, String serialNumber,
            String tag, String type, String substation, BigDecimal primaryVoltageKv,
            String manufacturer, Integer manufactureYear, BigDecimal powerMva,
            String oilType, BigDecimal oilVolumeLiters, long publishedAnalysisCount,
            AnalisePublicadaRef latestPublishedAnalysis) { }

    public record Resultado(String kind, String value, String text) { }
    public record Medicao(String code, String name, String unit, String method,
            String referenceLimit, Resultado result) { }

    public record AnalisePublicada(String id, String reportId, String testId,
            String testCode, String reportCode, EmpresaRef company, OrcamentoRef quote,
            AmostraRef sample, EquipamentoRef equipment, String testType, String analysisType,
            String scheduledAt, OffsetDateTime validatedAt, OffsetDateTime publishedAt,
            String technicalClassification, String conclusion, boolean pdfAvailable,
            String fileUrl, List<Medicao> measurements) { }

    public record RelatorioDetalhe(String id, String reportId, String analysisId,
            String testId, String testCode, String reportCode, EmpresaRef company,
            OrcamentoRef quote, AmostraRef sample, EquipamentoRef equipment,
            String testType, String analysisType, String scheduledAt,
            OffsetDateTime validatedAt, OffsetDateTime publishedAt,
            String technicalClassification, String conclusion, boolean pdfAvailable,
            String fileUrl) { }
}
