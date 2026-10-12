namespace Portal.Domain.LabResults;

/// <summary>
/// Conferir um totalizador tem tres desfechos, e nao dois. Um booleano obriga a escolher
/// entre chamar de divergente o laudo em que falta gas para somar, ou chamar de conferido
/// o que fechou por acidente com a soma incompleta. Os dois enganam: o primeiro acusa o
/// laboratorio de errar conta que nao fez, e o segundo afirma exatidao que ninguem mediu.
/// </summary>
public enum TotalizerCheckResult
{
    Matches,
    Diverges,
    IncompleteSum
}
