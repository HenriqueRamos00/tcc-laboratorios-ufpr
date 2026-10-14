using System.Globalization;
using System.Text;

using Portal.Domain.Quotes;

namespace Portal.Adapters.Quotes;

/// <summary>
/// Escreve, em uma pagina, a proposta comercial de um orcamento. O PDF e montado
/// aqui mesmo, sem biblioteca: sao cinco objetos e uma tabela de referencia cruzada,
/// e a dependencia nova nao se pagaria por um documento de demonstracao.
///
/// O texto sai sem acento de proposito. A fonte Helvetica padrao do PDF le os bytes
/// como WinAnsi, e caractere fora da tabela viraria interrogacao no meio da palavra;
/// tirar o acento mantem a palavra legivel.
/// </summary>
internal static class QuoteProposalPdf
{
    private static readonly Encoding WinAnsi = Encoding.Latin1;

    /// <summary>So o separador: pedir "a cultura do Brasil" traria junto moeda e calendario.</summary>
    private static readonly NumberFormatInfo BrazilianAmount = new()
    {
        NumberDecimalSeparator = ",",
        NumberGroupSeparator = ".",
        NumberGroupSizes = [3],
    };

    private const string Title = "PROPOSTA COMERCIAL";
    private const int CharactersPerLine = 78;
    private const int FirstLineBaseline = 790;
    private const int LineHeight = 18;
    private const int LineLimit = 40;

    public static byte[] Write(Quote quote)
    {
        List<string> lines =
        [
            $"Proposta: {quote.Code}",
            .. Wrapped($"Objeto: {quote.Name}"),
            $"Cliente: {quote.CompanyName}",
            $"Contato: {ContactOf(quote)}",
            $"Data: {CreatedDateOf(quote)}",
            $"Situacao: {quote.Stage}",
            "",
            "OBJETIVO",
            .. Wrapped(quote.Description),
            "",
            $"TOTAL DA PROPOSTA: R$ {quote.TotalPrice.ToString("N2", BrazilianAmount)}",
            "",
            .. Wrapped(
                "Documento gerado pelo portal enquanto a proposta do Salesforce nao esta "
                + "integrada. Nao constitui oferta comercial."),
        ];

        return Assemble(ContentStream(lines.Take(LineLimit).ToList()));
    }

    private static string ContactOf(Quote quote) =>
        string.IsNullOrWhiteSpace(quote.ExternalContactName)
            ? quote.ExternalContactEmail
            : quote.ExternalContactName;

    private static string CreatedDateOf(Quote quote) =>
        quote.CreatedDate?.ToString("dd/MM/yyyy", CultureInfo.InvariantCulture) ?? "Nao informada";

    private static IEnumerable<string> Wrapped(string paragraph)
    {
        var line = new StringBuilder();

        foreach (string word in paragraph.Split(' ', StringSplitOptions.RemoveEmptyEntries))
        {
            if (line.Length > 0 && line.Length + 1 + word.Length > CharactersPerLine)
            {
                yield return line.ToString();
                line.Clear();
            }

            if (line.Length > 0) line.Append(' ');
            line.Append(word);
        }

        if (line.Length > 0) yield return line.ToString();
    }

    private static string ContentStream(IReadOnlyList<string> lines)
    {
        var content = new StringBuilder();
        content.Append("BT\n/F1 18 Tf\n");
        content.Append($"50 {FirstLineBaseline} Td\n({Escaped(Title)}) Tj\n");
        content.Append("/F1 11 Tf\n");
        content.Append($"0 -{LineHeight + 16} Td\n");

        bool firstLineWritten = false;
        foreach (string line in lines)
        {
            if (firstLineWritten) content.Append($"0 -{LineHeight} Td\n");
            firstLineWritten = true;
            if (line.Length > 0) content.Append($"({Escaped(line)}) Tj\n");
        }

        content.Append("ET");
        return content.ToString();
    }

    private static string Escaped(string text)
    {
        string withoutAccents = new(
            text.Normalize(NormalizationForm.FormD)
                .Where(letter => CharUnicodeInfo.GetUnicodeCategory(letter) != UnicodeCategory.NonSpacingMark)
                .ToArray());

        return withoutAccents
            .Replace("\\", "\\\\")
            .Replace("(", "\\(")
            .Replace(")", "\\)");
    }

    // A tabela de referencia cruzada guarda o deslocamento em bytes de cada objeto, e
    // leitor que confere a tabela recusa o arquivo quando o numero nao bate. Por isso o
    // deslocamento e medido em bytes ja codificados, e nao no tamanho da string.
    private static byte[] Assemble(string content)
    {
        string[] objects =
        [
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            $"<< /Length {WinAnsi.GetByteCount(content)} >>\nstream\n{content}\nendstream",
        ];

        var document = new StringBuilder("%PDF-1.4\n");
        var offsets = new List<int>();

        for (int number = 1; number <= objects.Length; number++)
        {
            offsets.Add(WinAnsi.GetByteCount(document.ToString()));
            document.Append($"{number} 0 obj\n{objects[number - 1]}\nendobj\n");
        }

        int crossReferenceOffset = WinAnsi.GetByteCount(document.ToString());
        document.Append($"xref\n0 {objects.Length + 1}\n");
        document.Append("0000000000 65535 f \n");
        foreach (int offset in offsets) document.Append($"{offset:D10} 00000 n \n");
        document.Append($"trailer\n<< /Size {objects.Length + 1} /Root 1 0 R >>\n");
        document.Append($"startxref\n{crossReferenceOffset}\n%%EOF\n");

        return WinAnsi.GetBytes(document.ToString());
    }
}
