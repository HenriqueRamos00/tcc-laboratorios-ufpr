namespace Portal.Domain.LabResults;

/// <summary>
/// Uma linha do historico de laudos de um equipamento. Carrega o protocolo porque e ele
/// que endereca a analise na tela de indicadores, e a identificacao porque e o numero que
/// o cliente le no papel.
/// </summary>
public sealed record EquipmentReport(
    string ProtocoloLactec,
    string Identification,
    DateTime AnalysisEndDate,
    DateTime IssueDate,
    ReportAnalysisType AnalysisType)
{
    /// <summary>
    /// Laudo com data de emissao esta concluido, porque emitir e o ato de publicar o
    /// resultado. Sem data de emissao a analise terminou e o laudo ainda nao saiu, e e isso
    /// que a tela deve dizer, em vez de prometer um documento que ninguem assinou.
    /// </summary>
    public ReportStatus Status => IssueDate == default
        ? ReportStatus.InProgress
        : ReportStatus.Completed;
}
