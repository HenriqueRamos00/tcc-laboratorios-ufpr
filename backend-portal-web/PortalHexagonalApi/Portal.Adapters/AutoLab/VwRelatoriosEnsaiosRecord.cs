namespace Portal.Adapters.AutoLab;

public sealed class VwRelatoriosEnsaiosRecord
{
    public string? NumeroSolicitacao { get; init; }

    public Guid Oid { get; init; }

    public string? OrcamentoCRM { get; init; }

    public string? IDAmostra { get; init; }

    public string? Nome { get; init; }

    public Guid? PdfAssinado { get; init; }

    public string? ClienteSolicitante { get; init; }

    public string? Identificacao { get; init; }

    public DateTime? DataFinalProgramadaCliente { get; init; }

    public string? SituacaoRelatorio { get; init; }

    public DateTime? DataEmissao { get; init; }

    public DateTime? DataPublicacao { get; init; }

    public int? DiasIntervalo { get; init; }
}
