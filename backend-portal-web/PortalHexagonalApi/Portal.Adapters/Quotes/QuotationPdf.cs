using Portal.Adapters.Quotes.Exceptions;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

using QuestPDF.Drawing.Exceptions;
using QuestPDF.Fluent;
using QuestPDF.Infrastructure;

namespace Portal.Adapters.Quotes;

/// <summary>
/// A porta de entrada do desenho: recebe o orcamento e devolve os bytes do PDF.
///
/// A licenca da QuestPDF e declarada aqui, no construtor estatico, e nao no <c>Program</c>.
/// A biblioteca recusa gerar o primeiro documento enquanto ninguem a declarou, e o
/// <c>Program</c> nao roda na suite: escrita la, a API funcionaria e todo teste que desenha
/// um PDF morreria de <see cref="InitializationException"/>, que e o jeito mais caro de
/// descobrir onde a linha deveria estar. No construtor estatico o CLR garante que ela e
/// escrita uma vez, antes do primeiro uso, por qualquer caminho que chegue ate aqui. A
/// edicao Community e a que o dono do projeto escolheu ao adotar a biblioteca.
///
/// Toda falha de desenho sai como <see cref="QuoteDocumentUnavailableException"/>, que e a
/// falha que a porta ja declara para "o orcamento existe e o documento nao pode ser
/// entregue", e que o controller ja traduz em 502. Uma excecao nova para o mesmo desfecho
/// HTTP so duplicaria o bloco de traducao.
/// </summary>
public static class QuotationPdf
{
    static QuotationPdf()
    {
        QuestPDF.Settings.License = LicenseType.Community;

    }

    public static byte[] Write(Quote quote, IReadOnlyList<QuotationItem> items)
    {
        ArgumentNullException.ThrowIfNull(quote);
        ArgumentNullException.ThrowIfNull(items);

        try
        {
            return new QuotationDocument(quote, items).GeneratePdf();
        }
        catch (Exception failure) when (failure is QuotationAssetMissingException
            or DocumentComposeException
            or DocumentDrawingException
            or DocumentLayoutException
            or InitializationException)
        {
            throw new QuoteDocumentUnavailableException(
                "Não foi possível desenhar o PDF do orçamento.", failure);
        }
    }
}
