namespace Portal.Application.CompanyRecords;

/// <summary>
/// Uma emissao de relatorio de uma empresa do escopo. <see cref="SignedFileId"/> aponta o
/// arquivo assinado dentro da fonte e nunca sai da API.
/// </summary>
public sealed record CompanyReportRow(
    Guid Id,
    string? Identification,
    string? RequestNumber,
    string? QuoteCode,
    DateTime? IssueDate,
    DateTime? PublicationDate,
    int? Revision,
    bool IsReplaced,
    Guid? SignedFileId,
    string? Cnpj = null);
