namespace Portal.Domain.LabResults;

/// <summary>
/// Em que pe esta o laudo, do ponto de vista de quem espera o resultado.
///
/// Aqui a situacao e derivada da data de emissao, e o enum tem so os dois desfechos que o
/// dado disponivel sustenta: nao ha como deduzir cancelamento de uma view que lista itens
/// medidos.
///
/// A coluna <c>SituacaoRelatorio</c> da view irma <c>vwRelatoriosEnsaios</c> e lida desde
/// a fatia do arquivo de laudos, e o adaptador do AutoLAB a publica crua em
/// <c>LabReport.Status</c>, que viaja como texto. Entao o portal tem hoje duas gramaticas
/// para a mesma pergunta: este enum, derivado, e o texto que a fonte escreveu. Enquanto as
/// duas existirem, o valor cru nao tem conjunto de valores declarado em lugar nenhum do
/// portal, e renomear o rotulo na fonte muda o que o cliente le na tela.
/// </summary>
public enum ReportStatus
{
    Completed,
    InProgress
}
