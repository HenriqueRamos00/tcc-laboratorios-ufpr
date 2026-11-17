namespace Portal.Adapters.AutoLab;

public sealed class AmostraObjetoRecord
{
    public Guid Oid { get; init; }

    public Guid? ProtocoloAmostra { get; init; }

    public Guid? SolicitacaoServico { get; init; }

    public int? GCRecord { get; init; }
}
