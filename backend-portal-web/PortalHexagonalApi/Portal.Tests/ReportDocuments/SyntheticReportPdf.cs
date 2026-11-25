using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace Portal.Tests.ReportDocuments;

public static class SyntheticReportPdf
{
    public const string GasConclusionTitle = "LAUDO DA ANÁLISE CROMATOGRÁFICA";
    public const string PhysicalChemicalConclusionTitle = "LAUDO DAS ANÁLISES FÍSICO-QUÍMICAS";
    public const string GasParagraph =
        "Baseado nos critérios adotados pelos Institutos Lactec, os resultados obtidos na análise são considerados normais.";
    public const string PhysicalChemicalParagraph =
        "De acordo com a norma ABNT NBR 10576/17, os resultados obtidos nesta análise indicam que o óleo mineral isolante encontra-se em boas condições fisico-químicas.";
    public const string NewAnalysis = "Sugere-se nova análise no prazo de 1 (um) ano.";
    public const string LongParagraph =
        "Recomenda-se acompanhar a evolução dos gases dissolvidos com nova coleta em prazo reduzido porque os valores "
        + "medidos de hidrogênio e de acetileno cresceram em relação ao histórico do equipamento e indicam atividade "
        + "de descargas de baixa energia no interior do transformador sob ensaio.";

    static SyntheticReportPdf()
    {
        QuestPDF.Settings.License = LicenseType.Community;
    }

    public static byte[] Of(
        string? gasLabel,
        string? physicalChemicalLabel,
        bool withConclusions = true,
        IReadOnlyList<string>? gasParagraphs = null)
    {
        return Document.Create(document => document.Page(page =>
        {
            page.Size(PageSizes.A4);
            page.Margin(20);
            page.DefaultTextStyle(style => style.FontSize(8));
            page.Header().AlignRight().Column(header =>
            {
                header.Item().Text("LACTEC-09999/2026").Bold();
                header.Item().Text("LAUDO DE ANÁLISE").Bold();
            });
            page.Content().PaddingTop(20).Column(column =>
            {
                column.Spacing(12);

                if (gasLabel is not null) GasTable(column, gasLabel);
                if (physicalChemicalLabel is not null) PhysicalChemicalTable(column, physicalChemicalLabel);

                if (withConclusions)
                {
                    if (gasLabel is not null)
                        Conclusion(column, GasConclusionTitle, gasParagraphs ?? [GasParagraph, NewAnalysis]);
                    if (physicalChemicalLabel is not null)
                        Conclusion(column, PhysicalChemicalConclusionTitle, [PhysicalChemicalParagraph, NewAnalysis]);
                }

                column.Item().PaddingTop(20).Text("ND - Não Detectado NA - Não Aplicável");
                column.Item().PaddingTop(20).Text("OBSERVAÇÕES").Bold();
                column.Item().Text("Amostra coletada pelo cliente.");
            });
        })).GeneratePdf();
    }

    private static void GasTable(ColumnDescriptor column, string label)
    {
        column.Item().Table(table =>
        {
            table.ColumnsDefinition(columns =>
            {
                columns.ConstantColumn(200);
                columns.ConstantColumn(60);
                columns.ConstantColumn(60);
                columns.ConstantColumn(80);
                columns.RelativeColumn();
            });

            table.Cell().Text("ANÁLISE CROMATOGRÁFICA ABNT NBR 7070 - Gases Dissolvidos (ppm)").Bold();
            table.Cell().ColumnSpan(2).AlignCenter().Text("HISTÓRICO DE RESULTADOS").Bold();
            table.Cell().AlignCenter().Text("RESULTADO").Bold();
            table.Cell().AlignCenter().Text("TAXA DE CRESCIMENTO (% em relação a última análise)").Bold();

            table.Cell();
            table.Cell().ColumnSpan(2).AlignCenter().Text("Data Coleta:").Bold();
            table.Cell().Background(Colors.Red.Lighten2).AlignCenter().Text(label);
            table.Cell();

            table.Cell();
            table.Cell().AlignCenter().Text("28/07/2026");
            table.Cell().AlignCenter().Text("12/08/2026");
            table.Cell().AlignCenter().Text("24/09/2026");
            table.Cell();

            Row(table, "Hidrogênio", "8", "31", "36", "16,13");
            Row(table, "Acetileno", "ND", "ND", "ND", "-");
            Row(table, "Total de Gases", "97358", "81660", "82142", "0,59");
        });
    }

    private static void PhysicalChemicalTable(ColumnDescriptor column, string label)
    {
        column.Item().Table(table =>
        {
            table.ColumnsDefinition(columns =>
            {
                columns.ConstantColumn(150);
                columns.ConstantColumn(60);
                columns.ConstantColumn(90);
                columns.ConstantColumn(60);
                columns.ConstantColumn(60);
                columns.ConstantColumn(80);
            });

            table.Cell().Text("ANÁLISES FÍSICO-QUÍMICAS").Bold();
            table.Cell().Text("MÉTODO").Bold();
            table.Cell().Text("ABNT NBR 10576/17 Valores-limite para óleo de transformador em uso");
            table.Cell().ColumnSpan(2).AlignCenter().Text("HISTÓRICO DE RESULTADOS").Bold();
            table.Cell().AlignCenter().Text("RESULTADO").Bold();

            table.Cell().ColumnSpan(3);
            table.Cell().ColumnSpan(2).AlignCenter().Text("Data Coleta:").Bold();
            table.Cell().Background(Colors.Green.Lighten2).AlignCenter().Text(label);

            table.Cell().ColumnSpan(3);
            table.Cell().AlignCenter().Text("28/07/2026");
            table.Cell().AlignCenter().Text("16/09/2026");
            table.Cell().AlignCenter().Text("24/09/2026");

            table.Cell().Text("Índice de Neutralização (mg KOH/g óleo)");
            table.Cell().Text("NBR 14248");
            table.Cell().Text("máx. 0,2");
            table.Cell().AlignCenter().Text("0,01");
            table.Cell().AlignCenter().Text("0,01");
            table.Cell().AlignCenter().Text("0,01");
        });
    }

    private static void Row(TableDescriptor table, string name, params string[] values)
    {
        table.Cell().Text(name);

        foreach (string value in values)
            table.Cell().AlignCenter().Text(value);
    }

    private static void Conclusion(ColumnDescriptor column, string title, IReadOnlyList<string> paragraphs)
    {
        column.Item().PaddingTop(16).Column(block =>
        {
            block.Item().Text(title).Bold();

            foreach (string paragraph in paragraphs)
                block.Item().Text(paragraph);
        });
    }
}
