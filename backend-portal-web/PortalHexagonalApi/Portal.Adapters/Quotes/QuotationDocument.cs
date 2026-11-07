using System.Globalization;

using Portal.Domain.Quotes;

using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace Portal.Adapters.Quotes;

/// <summary>
/// O orcamento desenhado no papel timbrado do Lactec, nas cinco paginas do documento que o
/// comercial ja emite: identificacao e descricao, servicos e impostos, faturamento e prazos,
/// entrega e informacoes adicionais, e a pagina institucional.
///
/// As quebras de pagina entre os cinco grupos sao explicitas, e nao consequencia do que
/// coube: o documento emitido hoje tem essa divisao, e deixar o texto escorrer faria a mesma
/// secao cair numa pagina diferente a cada mudanca de redacao. A tabela de servicos continua
/// livre para ganhar paginas, porque o numero de itens e do orcamento e nao do desenho.
///
/// As medidas nao sao gosto: saem do proprio documento alvo, medido em pontos. A margem
/// lateral e 44, a faixa do topo ocupa a largura inteira da pagina e as seis colunas da
/// tabela somam os 524 que sobram. A faixa do rodape e desenhada no fundo da pagina e sai
/// pela borda de baixo e pela esquerda, como no original, onde so a ponta dela aparece.
/// </summary>
internal sealed class QuotationDocument : IDocument
{
    private const float SideMargin = 44f;
    private const float ContentTopGap = 17.4f;
    private const float FooterSideMargin = 31f;
    private const float FooterBottomMargin = 23.5f;
    private const float FooterRevisionOffset = 283.5f;

    private const float IdentificationGap = 6f;
    private const float QuotationBoxWidth = 112.5f;
    private const float QuotationBoxPadding = 5.5f;
    private const float IssuerContactColumnWidth = 117f;
    private const float ClientSideColumnWidth = 135f;
    private const float ClientContactRatio = 1.28f;

    private const float ItemColumnWidth = 34f;
    private const float DescriptionColumnWidth = 188f;
    private const float StandardColumnWidth = 102f;
    private const float QuantityColumnWidth = 49f;
    private const float UnitPriceColumnWidth = 75f;
    private const float TotalPriceColumnWidth = 76f;

    private const float StripesWidth = 267f;
    private const float StripesLeftOverflow = -27f;
    private const float StripesBottomOverflow = 47f;
    private const float SocialNetworksWidth = 105f;

    private const float BorderWidth = 0.75f;
    private const float BoxPadding = 7.2f;
    private const float FieldSpacing = 3f;
    private const float ColumnSpacing = 7.2f;
    private const float FieldHeight = 12f;

    private const float DocumentTitleSize = 14f;
    private const float BlockTitleSize = 13f;
    private const float SectionTitleSize = 10f;
    private const float ParagraphSize = 10f;
    private const float FieldSize = 9f;
    private const float FooterSize = 8f;
    private const float ParagraphLineHeight = 1.53f;

    /// <summary>
    /// A Lato viaja dentro da propria QuestPDF, entao o documento sai igual em qualquer
    /// maquina e em qualquer contentor, com acento e sem depender de fonte instalada. O
    /// documento alvo usa Helvetica sem embutir, e por isso perde o acento em leitor que nao
    /// a tenha. O nome vai como texto porque a lista de familias da biblioteca foi
    /// descontinuada: ela prometia fontes do sistema que o contentor nao tem.
    /// </summary>
    private const string FontName = "Lato";

    private const string ItemsAreNotPublished =
        "A relação de itens não é publicada pela integração do portal. O valor total abaixo é "
        + "o valor fechado do orçamento.";

    private static readonly Color Brand = Color.FromHex("#3C3671");
    private static readonly Color FieldBackground = Color.FromHex("#F2F2F2");
    private static readonly Color Paper = Color.FromHex("#FFFFFF");
    private static readonly Color Ink = Color.FromHex("#000000");

    private readonly Quote _quote;
    private readonly IReadOnlyList<QuotationItem> _items;

    public QuotationDocument(Quote quote, IReadOnlyList<QuotationItem> items)
    {
        _quote = quote;
        _items = items;
    }

    public DocumentMetadata GetMetadata() => new()
    {
        Title = $"Orçamento {_quote.Code}",
        Author = QuotationIssuer.LegalName,
        Subject = "Orçamento - Prestação de Serviços",
        Creator = QuotationIssuer.LegalName,
        Language = "pt-BR"
    };

    public DocumentSettings GetSettings() => DocumentSettings.Default;

    public void Compose(IDocumentContainer container)
    {
        container.Page(page =>
        {
            page.Size(PageSizes.Letter);
            page.Margin(0);
            page.DefaultTextStyle(style => style
                .FontFamily(FontName)
                .FontSize(ParagraphSize)
                .FontColor(Ink));

            page.Background().Element(DrawFooterStripes);
            page.Header().Element(DrawHeaderBanner);
            page.Content().PaddingHorizontal(SideMargin).PaddingTop(ContentTopGap).Element(DrawBody);
            page.Footer().Element(DrawFooter);
        });
    }

    /// <summary>
    /// A faixa decorativa fica no fundo da pagina, alinhada embaixo e a esquerda, e e
    /// empurrada para fora dos dois lados. O documento alvo mostra so a ponta dela, e o que
    /// corta o resto e a borda do papel: desenhar a figura inteira dentro da margem mudaria
    /// a proporcao do unico elemento que todas as paginas repetem.
    /// </summary>
    private static void DrawFooterStripes(IContainer container)
    {
        container
            .AlignBottom()
            .AlignLeft()
            .OffsetX(StripesLeftOverflow)
            .OffsetY(StripesBottomOverflow)
            .Width(StripesWidth)
            .Image(QuotationAssets.FooterStripes).UseOriginalImage()
            .FitWidth();
    }

    private static void DrawHeaderBanner(IContainer container)
    {
        container.Image(QuotationAssets.HeaderBanner).UseOriginalImage().FitWidth();
    }

    private static void DrawFooter(IContainer container)
    {
        container
            .PaddingHorizontal(FooterSideMargin)
            .PaddingBottom(FooterBottomMargin)
            .Row(row =>
            {
                row.ConstantItem(FooterRevisionOffset);
                row.AutoItem().Text(text =>
                {
                    text.DefaultTextStyle(style => style.FontSize(FooterSize));
                    text.Span(QuotationIssuer.DocumentRevision);
                });
                row.RelativeItem().Text(text =>
                {
                    text.DefaultTextStyle(style => style.FontSize(FooterSize));
                    text.AlignRight();
                    text.Span("Página ");
                    text.CurrentPageNumber();
                    text.Span(" de ");
                    text.TotalPages();
                });
            });
    }

    private void DrawBody(IContainer container)
    {
        container.Column(column =>
        {
            column.Item().Element(DrawIdentification);
            column.Item().PaddingTop(18).Element(DrawDocumentTitle);
            column.Item().PaddingTop(16).Element(DrawClient);
            column.Item().PaddingTop(21).Element(DrawGreeting);
            column.Item().PaddingTop(18).Element(DrawQuotationDescription);
            column.Item().PageBreak();
            column.Item().Element(DrawServicesAndTaxes);
            column.Item().PageBreak();
            column.Item().Element(DrawBillingAndResults);
            column.Item().PageBreak();
            column.Item().Element(DrawDeliveryAndAdditionalInformation);
            column.Item().PageBreak();
            column.Item().Element(DrawAboutLactec);
        });
    }

    private void DrawIdentification(IContainer container)
    {
        container.Row(row =>
        {
            DrawIssuer(row.RelativeItem());
            row.ConstantItem(IdentificationGap);
            DrawQuotationBox(row.ConstantItem(QuotationBoxWidth));
        });
    }

    /// <summary>
    /// O bloco do emitente tem as mesmas quatro linhas em duas colunas do documento alvo. O
    /// nome, o telefone e o e-mail de quem atende sao de uma pessoa e nao existem em lugar
    /// nenhum do sistema: as linhas ficam com o rotulo e o valor vazio, pela mesma regra do
    /// bloco do cliente. Apagar as duas linhas encolhe a caixa da esquerda sem encolher a do
    /// numero do orcamento, e sobra um vao cinza de quarenta pontos no alto da primeira
    /// pagina, que e exatamente o que o leitor nota primeiro.
    /// </summary>
    private static void DrawIssuer(IContainer container)
    {
        DrawBox(container, column =>
        {
            DrawBlockTitle(column.Item(), QuotationIssuer.LegalName);
            DrawIssuerLine(column, "CNPJ:", QuotationIssuer.Registration,
                "IE:", QuotationIssuer.StateRegistration);
            DrawIssuerLine(column, "Endereço de Faturamento:", QuotationIssuer.BillingAddress,
                "Fone Geral:", QuotationIssuer.GeneralPhone);
            DrawIssuerLine(column, "Contato:", string.Empty, "Fone:", string.Empty);
            DrawIssuerLine(column, "E-mail:", string.Empty, "Website:", QuotationIssuer.Website);
        });
    }

    private static void DrawIssuerLine(
        ColumnDescriptor column, string label, string value, string sideLabel, string sideValue) =>
        column.Item().Row(row =>
        {
            DrawField(row.RelativeItem(), label, value);
            row.ConstantItem(ColumnSpacing);
            DrawField(row.ConstantItem(IssuerContactColumnWidth), sideLabel, sideValue);
        });

    /// <summary>
    /// A data de emissao e a data de criacao do registro na fonte comercial, que e a unica
    /// data que atravessa a porta. A data de validade nao atravessa nenhuma: o rotulo fica e
    /// o valor fica em branco, porque apagar a linha mudaria a caixa de tres faixas para duas
    /// e preencher com qualquer coisa seria inventar prazo.
    /// </summary>
    private void DrawQuotationBox(IContainer container)
    {
        DrawBox(container, column =>
        {
            DrawBlockTitle(column.Item(), "Orçamento nº");
            DrawCenteredField(column.Item(), _quote.Code);
            DrawFieldTitle(column.Item(), "Data Emissão");
            DrawCenteredField(column.Item(), IssueDateOf(_quote));
            DrawFieldTitle(column.Item(), "Data de Validade:");
            DrawCenteredField(column.Item(), string.Empty);
        }, QuotationBoxPadding);
    }

    /// <summary>
    /// Do cliente so a razao social atravessa a porta hoje. Os outros cinco campos ficam com
    /// o rotulo e o valor vazio: eles existem na fonte, o mapeamento ainda nao os carrega, e
    /// a caixa com tres linhas e o que segura o desenho da primeira pagina.
    ///
    /// O dono da oportunidade nao entra aqui. <c>ExternalContactName</c> vem de
    /// <c>Opportunity.Owner</c>, que e gente do Lactec: publicado como contato do cliente,
    /// ele diria ao cliente que o representante dele e um vendedor do fornecedor.
    /// </summary>
    private void DrawClient(IContainer container)
    {
        DrawBox(container, column =>
        {
            DrawBlockTitle(column.Item(), "Cliente");
            column.Item().Row(row =>
            {
                DrawField(row.RelativeItem(), "Razão Social:", _quote.CompanyName);
                row.ConstantItem(ColumnSpacing);
                DrawField(row.ConstantItem(ClientSideColumnWidth), "CNPJ/CPF:", string.Empty);
            });
            column.Item().Row(row =>
            {
                DrawField(row.RelativeItem(), "Endereço:", string.Empty);
                row.ConstantItem(ColumnSpacing);
                DrawField(row.ConstantItem(ClientSideColumnWidth), "Fone Geral:", string.Empty);
            });
            column.Item().Row(row =>
            {
                DrawField(row.RelativeItem(ClientContactRatio), "Contato:", string.Empty);
                row.ConstantItem(ColumnSpacing);
                DrawField(row.RelativeItem(1), "E-mail:", string.Empty);
                row.ConstantItem(ColumnSpacing);
                DrawField(row.ConstantItem(ClientSideColumnWidth), "Fone:", string.Empty);
            });
        });
    }

    private static void DrawGreeting(IContainer container)
    {
        container.Column(column =>
        {
            column.Spacing(4);
            DrawParagraph(column.Item(), QuotationTerms.Greeting, justified: false);
            DrawParagraph(column.Item(), QuotationTerms.Introduction, justified: false);
        });
    }

    private void DrawQuotationDescription(IContainer container)
    {
        container.Column(column =>
        {
            DrawSectionTitle(column.Item(), QuotationTerms.SectionTitles[0]);
            DrawParagraph(column.Item().PaddingTop(8), _quote.Description, justified: false);
        });
    }

    private void DrawServicesAndTaxes(IContainer container)
    {
        container.Column(column =>
        {
            DrawSectionTitle(column.Item(), QuotationTerms.SectionTitles[1]);
            column.Item().PaddingTop(10).Element(DrawServices);
            column.Item().PaddingTop(26).Element(section => DrawSectionTitle(section, QuotationTerms.SectionTitles[2]));
            column.Item().PaddingTop(10).Element(taxes => DrawParagraphsInABox(taxes, QuotationTerms.Taxes));
        });
    }

    private void DrawServices(IContainer container)
    {
        container.Table(table =>
        {
            table.ColumnsDefinition(columns =>
            {
                columns.ConstantColumn(ItemColumnWidth);
                columns.ConstantColumn(DescriptionColumnWidth);
                columns.ConstantColumn(StandardColumnWidth);
                columns.ConstantColumn(QuantityColumnWidth);
                columns.ConstantColumn(UnitPriceColumnWidth);
                columns.ConstantColumn(TotalPriceColumnWidth);
            });

            table.Header(header =>
            {
                DrawCenteredStrong(HeaderCell(header.Cell()), "Item");
                DrawCenteredStrong(HeaderCell(header.Cell()), "Descrição serviço/ensaio");
                DrawCenteredStrong(HeaderCell(header.Cell()), "Norma/Procedimento");
                DrawCenteredStrong(HeaderCell(header.Cell()), "Quant.");
                DrawCenteredStrong(HeaderCell(header.Cell()), "Valor unitário");
                DrawCenteredStrong(HeaderCell(header.Cell()), "Valor total");
            });

            if (_items.Count == 0)
            {
                DrawLeftAligned(BodyCell(table.Cell().ColumnSpan(6)), ItemsAreNotPublished);
            }

            foreach ((QuotationItem item, int position) in _items.Select((item, index) => (item, index + 1)))
            {
                DrawCentered(BodyCell(table.Cell()), position.ToString(CultureInfo.InvariantCulture));
                DrawLeftAligned(BodyCell(table.Cell()), item.Description);
                DrawCentered(BodyCell(table.Cell()), item.Standard);
                DrawCentered(BodyCell(table.Cell()), BrazilianNumber.Quantity(item.Quantity));
                DrawRightAligned(BodyCell(table.Cell()), BrazilianNumber.Amount(item.UnitPrice));
                DrawRightAligned(BodyCell(table.Cell()), BrazilianNumber.Amount(item.TotalPrice));
            }

            table.Footer(footer =>
            {
                DrawRightAlignedStrong(TotalCell(footer.Cell().ColumnSpan(5)), "Valor total");
                DrawRightAlignedStrong(TotalCell(footer.Cell()), BrazilianNumber.Amount(_quote.TotalPrice));
            });
        });
    }

    private static void DrawBillingAndResults(IContainer container)
    {
        container.Column(column =>
        {
            DrawSectionTitle(column.Item(), QuotationTerms.SectionTitles[3]);
            column.Item().PaddingTop(10).Element(billing => DrawParagraphsInABox(billing, QuotationTerms.Billing));
            DrawSection(column, QuotationTerms.SectionTitles[4], [QuotationTerms.InsuranceAndGuarantees]);
            DrawSection(column, QuotationTerms.SectionTitles[5], [QuotationTerms.PriceAdjustment]);
            DrawSection(column, QuotationTerms.SectionTitles[6], [QuotationTerms.ExecutionDeadline]);
            DrawSection(column, QuotationTerms.SectionTitles[7], [QuotationTerms.Results]);
        });
    }

    private static void DrawDeliveryAndAdditionalInformation(IContainer container)
    {
        container.Column(column =>
        {
            DrawSectionTitle(column.Item(), QuotationTerms.SectionTitles[8]);
            column.Item().PaddingTop(10).Element(DrawDeliveryAddress);
            DrawSection(column, QuotationTerms.SectionTitles[9], QuotationTerms.Return);
            DrawSection(column, QuotationTerms.SectionTitles[10], [QuotationTerms.Freight]);
            DrawSection(column, QuotationTerms.SectionTitles[11], [QuotationTerms.AdditionalInformation]);
        });
    }

    private static void DrawDeliveryAddress(IContainer container)
    {
        DrawBox(container, column =>
        {
            DrawStrongParagraph(column.Item(), QuotationTerms.DeliveryAddressTitle);

            foreach (string paragraph in QuotationTerms.Delivery)
                DrawParagraphOnAField(column.Item(), paragraph);
        });
    }

    /// <summary>
    /// A pagina institucional nao depende de nenhum dado do orcamento, e por isso ela existe
    /// em todo documento gerado, inclusive no do orcamento que so tem codigo e valor.
    /// </summary>
    private static void DrawAboutLactec(IContainer container)
    {
        container.Column(column =>
        {
            DrawSectionTitle(column.Item(), QuotationTerms.AboutTitle);
            DrawParagraph(column.Item().PaddingTop(8), QuotationTerms.About, justified: true);
            column.Item().PaddingTop(26).Element(title => DrawSectionTitle(title, QuotationTerms.LearnMoreTitle));
            DrawParagraph(column.Item().PaddingTop(8), QuotationTerms.LearnMore, justified: false);
            column.Item().PaddingTop(14).Width(SocialNetworksWidth)
                .Image(QuotationAssets.SocialNetworks).UseOriginalImage().FitWidth();
        });
    }

    private static void DrawSection(ColumnDescriptor column, string title, IReadOnlyList<string> paragraphs)
    {
        column.Item().PaddingTop(22).Element(section => DrawSectionTitle(section, title));

        foreach (string paragraph in paragraphs)
            DrawParagraph(column.Item().PaddingTop(8), paragraph, justified: true);
    }

    /// <summary>
    /// O paragrafo corrido do documento alvo respira: dez pontos de corpo com quinze e meio
    /// de entrelinha. A entrelinha fica aqui e nao no estilo da pagina porque a faixa branca
    /// dos campos e a celula da tabela sao caixas de altura medida, e esticar a linha dentro
    /// delas mudaria a altura de toda caixa do documento.
    /// </summary>
    private static void DrawParagraph(IContainer container, string paragraph, bool justified) =>
        container.Text(text =>
        {
            text.DefaultTextStyle(style => style.LineHeight(ParagraphLineHeight));

            if (justified) text.Justify();

            text.Span(paragraph);
        });

    private static void DrawParagraphsInABox(IContainer container, IReadOnlyList<string> paragraphs)
    {
        DrawBox(container, column =>
        {
            foreach (string paragraph in paragraphs)
                DrawParagraphOnAField(column.Item(), paragraph);
        });
    }

    /// <summary>
    /// A caixa das secoes com dado tabelado: borda na cor da marca, fundo cinza e cada
    /// paragrafo numa faixa branca, como as caixas de identificacao da primeira pagina. As
    /// tres caixas do documento alvo nao pintam o fundo da mesma forma; aqui elas pintam,
    /// porque repetir a inconsistencia do gerador antigo custa mais do que corrigi-la.
    /// </summary>
    private static void DrawBox(IContainer container, Action<ColumnDescriptor> content, float padding = BoxPadding)
    {
        container
            .Border(BorderWidth, Unit.Point)
            .BorderColor(Brand)
            .Background(FieldBackground)
            .Padding(padding)
            .Column(column =>
            {
                column.Spacing(FieldSpacing);
                content(column);
            });
    }

    private static IContainer HeaderCell(IContainer container) =>
        container.Border(BorderWidth, Unit.Point).BorderColor(Brand).Background(FieldBackground)
            .PaddingVertical(3).PaddingHorizontal(3);

    private static IContainer BodyCell(IContainer container) =>
        container.Border(BorderWidth, Unit.Point).BorderColor(Brand).Background(Paper)
            .PaddingVertical(4).PaddingHorizontal(3);

    private static IContainer TotalCell(IContainer container) =>
        container.Border(BorderWidth, Unit.Point).BorderColor(Brand).Background(FieldBackground)
            .PaddingVertical(3).PaddingHorizontal(3);

    private static void DrawBlockTitle(IContainer container, string title) =>
        container.Text(text =>
        {
            text.DefaultTextStyle(style => style.FontSize(BlockTitleSize).FontColor(Brand));
            text.AlignCenter();
            text.Span(title).SemiBold();
        });

    private static void DrawFieldTitle(IContainer container, string title) =>
        container.Text(text =>
        {
            text.DefaultTextStyle(style => style.FontSize(FieldSize).FontColor(Brand));
            text.AlignCenter();
            text.Span(title).SemiBold();
        });

    private static void DrawSectionTitle(IContainer container, string title) =>
        container.Text(text =>
        {
            text.DefaultTextStyle(style => style.FontSize(SectionTitleSize).FontColor(Brand));
            text.Span(title).SemiBold();
        });

    private static void DrawStrongParagraph(IContainer container, string paragraph) =>
        container.Background(Paper).MinHeight(FieldHeight).PaddingVertical(2).PaddingHorizontal(4)
            .Text(text =>
            {
                text.DefaultTextStyle(style => style.FontSize(ParagraphSize));
                text.Span(paragraph).SemiBold().FontColor(Brand);
            });

    private static void DrawParagraphOnAField(IContainer container, string paragraph) =>
        container.Background(Paper).MinHeight(FieldHeight).PaddingVertical(2).PaddingHorizontal(4)
            .Text(text =>
            {
                text.DefaultTextStyle(style => style.FontSize(ParagraphSize));
                text.Span(paragraph);
            });

    private static void DrawField(IContainer container, string label, string value) =>
        container.Background(Paper).MinHeight(FieldHeight).PaddingVertical(2).PaddingHorizontal(4)
            .Text(text =>
            {
                text.DefaultTextStyle(style => style.FontSize(FieldSize));
                text.Span(label).SemiBold().FontColor(Brand);
                text.Span(" ");
                text.Span(value);
            });

    private static void DrawCenteredField(IContainer container, string value) =>
        container.Background(Paper).MinHeight(FieldHeight).PaddingVertical(2).PaddingHorizontal(3)
            .Text(text =>
            {
                text.DefaultTextStyle(style => style.FontSize(FieldSize));
                text.AlignCenter();
                text.Span(value);
            });

    private static void DrawLeftAligned(IContainer container, string content) =>
        container.Text(text =>
        {
            text.DefaultTextStyle(style => style.FontSize(FieldSize));
            text.Span(content);
        });

    private static void DrawCentered(IContainer container, string content) =>
        container.Text(text =>
        {
            text.DefaultTextStyle(style => style.FontSize(FieldSize));
            text.AlignCenter();
            text.Span(content);
        });

    private static void DrawRightAligned(IContainer container, string content) =>
        container.Text(text =>
        {
            text.DefaultTextStyle(style => style.FontSize(FieldSize));
            text.AlignRight();
            text.Span(content);
        });

    private static void DrawCenteredStrong(IContainer container, string content) =>
        container.Text(text =>
        {
            text.DefaultTextStyle(style => style.FontSize(FieldSize));
            text.AlignCenter();
            text.Span(content).SemiBold();
        });

    private static void DrawRightAlignedStrong(IContainer container, string content) =>
        container.Text(text =>
        {
            text.DefaultTextStyle(style => style.FontSize(FieldSize));
            text.AlignRight();
            text.Span(content).SemiBold();
        });

    private static void DrawDocumentTitle(IContainer container) =>
        container.Text(text =>
        {
            text.DefaultTextStyle(style => style.FontSize(DocumentTitleSize).FontColor(Brand));
            text.AlignCenter();
            text.Span("Orçamento - Prestação de Serviços").SemiBold();
        });

    /// <summary>
    /// A data que o documento declara, no fuso de quem emite.
    ///
    /// A fonte comercial manda o instante com deslocamento zero, e imprimir o dia desse
    /// deslocamento faz o documento discordar da tela: a tela formata o mesmo instante no fuso
    /// do navegador, entao um orcamento criado as 22 horas de Brasilia aparece com um dia na
    /// listagem e com o dia seguinte no PDF que a propria listagem oferece para baixar.
    ///
    /// O deslocamento e fixo e nao vem da base de fusos do contentor. O Brasil nao tem horario
    /// de verao desde 2019, e pedir o fuso pelo nome faria o desenho inteiro depender de uma
    /// base que a imagem pode nao ter: o documento deixaria de sair, em vez de sair com a data
    /// de ontem.
    /// </summary>
    private static readonly TimeSpan IssuerOffset = TimeSpan.FromHours(-3);

    private static string IssueDateOf(Quote quote) =>
        quote.CreatedDate?.ToOffset(IssuerOffset).ToString("dd/MM/yyyy", CultureInfo.InvariantCulture)
        ?? string.Empty;
}
