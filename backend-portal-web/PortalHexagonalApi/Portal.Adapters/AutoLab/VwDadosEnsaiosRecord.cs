namespace Portal.Adapters.AutoLab;

public sealed class VwDadosEnsaiosRecord
{
    public string? Identificacao { get; init; }

    public string? ProtocoloLactec { get; init; }

    public string? NumSerie { get; init; }

    public string? Name { get; init; }

    public string? Resultado { get; init; }

    public string? EmpresaTrafo { get; init; }

    public DateTime? DataEmissao { get; init; }

    public DateTime? DataFinalAnalise { get; init; }
}
