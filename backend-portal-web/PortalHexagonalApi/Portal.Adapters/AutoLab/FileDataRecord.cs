namespace Portal.Adapters.AutoLab;

public sealed class FileDataRecord
{
    public Guid Oid { get; init; }

    public string? FileName { get; init; }

    public byte[]? Content { get; init; }

    public int? GCRecord { get; init; }
}
