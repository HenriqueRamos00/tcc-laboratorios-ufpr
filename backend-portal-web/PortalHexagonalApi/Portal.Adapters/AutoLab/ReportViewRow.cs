namespace Portal.Adapters.AutoLab;

public sealed record ReportViewRow(
    string? IDAmostra,
    string? Nome,
    string? ClienteSolicitante,
    DateTime? DataFinalProgramadaCliente,
    string? SituacaoRelatorio);
