namespace Portal.Adapters.AutoLab;

public sealed class RelatorioEmissaoRecord
{
    public Guid Oid { get; init; }

    public string? Identificacao { get; init; }

    public Guid? PdfAssinado { get; init; }

    public int? GCRecord { get; init; }

    public int? Revisao { get; init; }

    public Guid? RelatorioEnsaioSubstituto { get; init; }

    public DateTime? DataEmissao { get; init; }

    public DateTime? DataPublicacao { get; init; }

    public string? TituloRelatorio { get; init; }

    public string? Conclusao { get; init; }
}
