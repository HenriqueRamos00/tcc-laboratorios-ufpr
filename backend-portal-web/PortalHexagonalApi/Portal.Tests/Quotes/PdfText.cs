using UglyToad.PdfPig;
using UglyToad.PdfPig.Content;

namespace Portal.Tests.Quotes;

/// <summary>
/// O texto de um PDF gerado, pagina a pagina.
///
/// Procurar a string crua nos bytes nao serve mais: a QuestPDF comprime o fluxo de conteudo
/// e embute a fonte como subconjunto, entao o que esta no arquivo sao codigos de glifo
/// dentro de um fluxo comprimido. Um teste que procurasse a palavra nos bytes so poderia
/// passar com um gerador que nao comprime e nao embute fonte, ou seja, so poderia passar com
/// o gerador antigo.
///
/// O que a suite afirma sobre o documento e o que se mede sem fragilidade: quantas paginas
/// ele tem, em qual delas cada texto aparece e como o numero foi formatado. Pixel, posicao e
/// escolha de fonte nao entram: isso se confere renderizando o PDF e olhando.
/// </summary>
internal static class PdfText
{
    public static IReadOnlyList<string> OfEachPage(byte[] document)
    {
        using PdfDocument pdf = PdfDocument.Open(document);

        return [.. pdf.GetPages().Select(page => page.Text)];
    }

    public static string OfWholeDocument(byte[] document) =>
        string.Join('\n', OfEachPage(document));

    /// <summary>
    /// O mesmo texto sem nenhum espaco em branco. O espaco entre palavras pode viajar como
    /// glifo ou como deslocamento horizontal, e as duas formas desenham a mesma linha: uma
    /// afirmacao que dependa de qual delas o desenhista escolheu quebra sem que o documento
    /// tenha mudado.
    /// </summary>
    public static string Collapsed(string text) =>
        new([.. text.Where(letter => !char.IsWhiteSpace(letter))]);

    public static int PageCountOf(byte[] document)
    {
        using PdfDocument pdf = PdfDocument.Open(document);

        return pdf.NumberOfPages;
    }
}
