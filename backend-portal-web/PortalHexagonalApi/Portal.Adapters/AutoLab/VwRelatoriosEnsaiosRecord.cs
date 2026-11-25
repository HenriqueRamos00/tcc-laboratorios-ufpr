namespace Portal.Adapters.AutoLab;

/// <summary>
/// As colunas baratas de <c>vwRelatoriosEnsaios</c>, lidas uma emissao por vez pelo
/// <c>Oid</c>. <c>Identificacao</c>, <c>DataEmissao</c>, <c>DataPublicacao</c> e
/// <c>PdfAssinado</c> nao estao mapeadas de proposito: na view elas custam caro por linha, e
/// os mesmos valores vem de <c>RelatorioEmissao</c>.
/// </summary>
public sealed class VwRelatoriosEnsaiosRecord
{
    public Guid Oid { get; init; }

    public string? IDAmostra { get; init; }

    public string? Nome { get; init; }

    public string? ClienteSolicitante { get; init; }

    public DateTime? DataFinalProgramadaCliente { get; init; }

    public string? SituacaoRelatorio { get; init; }
}
