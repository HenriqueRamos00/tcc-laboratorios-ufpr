using Portal.Domain.LabResults;

namespace Portal.Application.LabResults;

/// <summary>
/// O que a porta condutora devolve. Duas series porque a tela tem duas tabelas, e cada uma
/// so existe quando o laudo traz o tipo de item medido correspondente: laudo de area que nao e
/// Oleos devolve a serie de gases vazia, e isso e resposta bem sucedida.
/// </summary>
public sealed record SampleHealthIndicators(
    string? SerialNumber,
    string Identification,
    AnalysisSeries PhysicalChemicalSeries,
    AnalysisSeries GasSeries,
    SampleAnalysis? SelectedAnalysis,
    IReadOnlyList<UnreadableResult> UnreadableResults,
    IReadOnlyList<TotalizerCheck> TotalizerChecks);
