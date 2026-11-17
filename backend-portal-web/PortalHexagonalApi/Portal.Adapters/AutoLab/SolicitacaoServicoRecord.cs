namespace Portal.Adapters.AutoLab;

public sealed class SolicitacaoServicoRecord
{
    public Guid Oid { get; init; }

    public string? NumeroSolicitacao { get; init; }

    public string? OrcamentoCRM { get; init; }

    public Guid? Cliente { get; init; }

    public int? GCRecord { get; init; }
}
