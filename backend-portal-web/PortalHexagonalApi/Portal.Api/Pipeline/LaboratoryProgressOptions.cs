namespace Portal.Api.Pipeline;

/// <summary>
/// Configuracao da fonte que informa a etapa do laboratorio.
///
/// <see cref="Source"/> ausente ou vazia vale <see cref="NotConnectedSource"/>, e essa e a
/// unica diferenca deliberada em relacao a <c>LabResults:Source</c>, que derruba a
/// inicializacao quando falta. Os dois casos nao tem o mesmo risco. Sem fonte de ensaios a
/// tela de indicadores nao tem dado nenhum, e subir assim e servir uma tela vazia como se
/// fosse um laudo. Sem fonte de etapa, um campo da listagem de orcamentos volta a ser o que
/// era antes desta fatia: a etapa que o lado comercial escreveu. O padrao e de falha
/// fechada, e o pior que ele produz e nenhuma promocao.
///
/// Valor escrito errado continua derrubando a inicializacao. Um padrao que absorvesse
/// tambem o erro de digitacao esconderia a fatia desligada num deployment que pensa que a
/// ligou.
/// </summary>
public sealed class LaboratoryProgressOptions
{
    public const string SectionName = "LaboratoryProgress";

    public const string NotConnectedSource = "not-connected";
    public const string InMemorySampleSource = "in-memory-sample";
    public const string SqlServerSource = "sql-server";

    public string? Source { get; init; }
}
