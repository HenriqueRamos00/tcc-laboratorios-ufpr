namespace Portal.Domain.LabResults;

/// <summary>
/// Um resultado do laudo como a fonte o escreveu: o nome que veio na coluna e o valor ja
/// interpretado, ainda sem saber a qual item medido ele corresponde. E a entrada de
/// <see cref="MeasuredItemRecognition"/>, e deixa de existir assim que o item e reconhecido.
///
/// Existe como tipo proprio porque reconhecer o totalizador exige ver o grupo inteiro: o
/// nome sozinho nao prova nada, e a soma so fecha com todas as linhas do mesmo protocolo
/// na mao.
/// </summary>
public sealed record ResultBeforeRecognition(string SourceName, MeasuredValue Value);
