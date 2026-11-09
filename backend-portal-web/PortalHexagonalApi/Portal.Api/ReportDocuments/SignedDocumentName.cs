using Portal.Application.ReportDocuments;

namespace Portal.Api.ReportDocuments;

public static class SignedDocumentName
{
    public static string Of(SignedReportDocument document)
    {
        ArgumentNullException.ThrowIfNull(document);

        string? name = string.IsNullOrWhiteSpace(document.FileName)
            ? null
            : Path.GetFileName(document.FileName.Replace('\\', '/').Trim());

        if (string.IsNullOrWhiteSpace(name)) return "laudo.pdf";

        return name.EndsWith(".pdf", StringComparison.OrdinalIgnoreCase) ? name : name + ".pdf";
    }
}
