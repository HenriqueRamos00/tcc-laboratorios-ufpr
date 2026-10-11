namespace Portal.Domain.LabResults;

/// <summary>
/// O resultado de conferir um totalizador publicado contra a soma dos itens medidos que ele
/// deveria somar. <see cref="GasesWithoutTest"/> acompanha o veredito porque e a
/// unica coisa que explica <see cref="TotalizerCheckResult.IncompleteSum"/>
/// sem quem le ter que ir conferir a amostra.
/// </summary>
public sealed record TotalizerCheck(
    string TotalizerCode,
    decimal PublishedValue,
    decimal MeasuredSum,
    int GasesWithoutTest,
    TotalizerCheckResult Result);
