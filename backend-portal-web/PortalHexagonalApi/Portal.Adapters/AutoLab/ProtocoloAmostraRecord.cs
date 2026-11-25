namespace Portal.Adapters.AutoLab;

public sealed class ProtocoloAmostraRecord
{
    public Guid Oid { get; init; }

    public string? Identificacao { get; init; }

    public int? GCRecord { get; init; }
}
