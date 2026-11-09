namespace Portal.Adapters.AutoLab;

public sealed class SituaProcessRecord
{
    public string? OrcamentoCRM { get; init; }

    public bool? Recebido { get; init; }

    public bool? Protocolado { get; init; }

    public int? SituacaoEnsaios { get; init; }

    public int SolicitacaoRelatorio { get; init; }
}
