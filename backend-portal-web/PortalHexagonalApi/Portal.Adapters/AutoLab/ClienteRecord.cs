namespace Portal.Adapters.AutoLab;

public sealed class ClienteRecord
{
    public Guid Oid { get; init; }

    public string? CadastroNacional { get; init; }

    public int? GCRecord { get; init; }
}
