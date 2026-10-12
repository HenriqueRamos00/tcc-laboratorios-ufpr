namespace Portal.Application.LabResults;

/// <summary>
/// Um item medido cujo resultado chegou como texto que nenhuma estrategia de parse aceita. Sai
/// da tabela e e declarado aqui: uma celula ruim degrada a propria linha, e lancar excecao
/// derrubaria os onze itens corretos do mesmo laudo por causa de um.
/// </summary>
public sealed record UnreadableResult(
    string Code,
    string Label,
    string OriginalText,
    string ProtocoloLactec);
