namespace Portal.Domain.LabResults;

/// <summary>
/// Em que pe esta o laudo, do ponto de vista de quem espera o resultado.
///
/// A situacao de verdade e uma coluna da view irma <c>vwRelatoriosEnsaios</c>
/// (<c>SituacaoRelatorio</c>), que esta fatia nao le. Aqui ela e derivada da data de
/// emissao, e o enum tem so os dois desfechos que o dado disponivel sustenta: nao ha como
/// deduzir cancelamento de uma view que lista itens medidos medidos.
/// </summary>
public enum ReportStatus
{
    Completed,
    InProgress
}
