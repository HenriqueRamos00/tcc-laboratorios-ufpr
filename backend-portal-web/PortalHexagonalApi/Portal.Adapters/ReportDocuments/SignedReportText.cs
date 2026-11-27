using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;

using Portal.Application.ReportDocuments;

using UglyToad.PdfPig;
using UglyToad.PdfPig.Content;
using UglyToad.PdfPig.Core;
using UglyToad.PdfPig.Graphics;
using UglyToad.PdfPig.Graphics.Colors;

namespace Portal.Adapters.ReportDocuments;

public static partial class SignedReportText
{
    private const double SameLineTolerance = 2.5;
    private const double ResultHeaderReach = 25;
    private const double ResultLabelReach = 40;
    private const double ResultColumnFallbackMargin = 40;
    private const double ParagraphBreakGap = 20;

    private const string ChromatographyTable = "ANALISE CROMATOGRAFICA";
    private const string PhysicalChemicalTable = "ANALISES FISICO-QUIMICAS";
    private const string ChromatographyConclusion = "LAUDO DA ANALISE CROMATOGRAFICA";
    private const string PhysicalChemicalConclusion = "LAUDO DAS ANALISES FISICO-QUIMICAS";
    private const string ConclusionPrefix = "LAUDO DA";
    private const string ResultHeader = "RESULTADO";

    private static readonly string[] ConclusionEnds = ["OBSERVACOES", "ND -", "NA -"];

    public static SignedReportFindings Read(byte[] pdf)
    {
        ArgumentNullException.ThrowIfNull(pdf);

        try
        {
            using PdfDocument document = PdfDocument.Open(pdf);

            return Read(document.GetPages().Select(page => new PageContent(LinesOf(page), FillsOf(page))).ToList());
        }
        catch (Exception exception) when (exception is not OutOfMemoryException)
        {
            return SignedReportFindings.Unreadable;
        }
    }

    private static SignedReportFindings Read(IReadOnlyList<PageContent> pages)
    {
        ResultCell? chromatographyLabel = null;
        ResultCell? physicalChemicalLabel = null;
        Conclusion? chromatography = null;
        Conclusion? physicalChemical = null;

        foreach (PageContent page in pages)
        {
            IReadOnlyList<Line> lines = page.Lines;

            for (int index = 0; index < lines.Count; index++)
            {
                Line line = lines[index];

                if (line.Key.StartsWith(ChromatographyConclusion, StringComparison.Ordinal))
                    chromatography ??= ConclusionAt(lines, index);
                else if (line.Key.StartsWith(PhysicalChemicalConclusion, StringComparison.Ordinal))
                    physicalChemical ??= ConclusionAt(lines, index);
                else if (line.Key.StartsWith(ConclusionPrefix, StringComparison.Ordinal))
                    continue;
                else if (line.Key.Contains(ChromatographyTable, StringComparison.Ordinal))
                    chromatographyLabel ??= ResultCellOf(page, line);
                else if (line.Key.Contains(PhysicalChemicalTable, StringComparison.Ordinal))
                    physicalChemicalLabel ??= ResultCellOf(page, line);
            }
        }

        return new SignedReportFindings(
            SectionOf(chromatographyLabel, chromatography),
            SectionOf(physicalChemicalLabel, physicalChemical));
    }

    private static ReportSection? SectionOf(ResultCell? cell, Conclusion? conclusion)
    {
        return cell is null && conclusion is null
            ? null
            : new ReportSection(
                cell?.Label,
                cell?.Color is null ? null : ResultColor.ConformityOf(cell.Color.Value),
                conclusion?.Title,
                conclusion?.Paragraphs ?? [])
            {
                ResultColor = cell?.Color is null ? null : ResultColor.HexOf(cell.Color.Value)
            };
    }

    private static ResultCell? ResultCellOf(PageContent page, Line tableHeader)
    {
        IReadOnlyList<Line> lines = page.Lines;

        (Line header, Word result)? found = lines
            .Where(line => line.Baseline >= tableHeader.Baseline - SameLineTolerance
                && line.Baseline <= tableHeader.Baseline + ResultHeaderReach)
            .OrderBy(line => Math.Abs(line.Baseline - tableHeader.Baseline))
            .SelectMany(line => line.Words
                .Where(word => KeyOf(word.Text) == ResultHeader)
                .Select(word => ((Line, Word)?)(line, word)))
            .FirstOrDefault();

        if (found is not (Line header, Word result)) return null;

        Word? before = header.Words.LastOrDefault(word => word.BoundingBox.Right <= result.BoundingBox.Left);
        Word? after = header.Words.FirstOrDefault(word => word.BoundingBox.Left >= result.BoundingBox.Right);

        double left = before?.BoundingBox.Right ?? result.BoundingBox.Left - ResultColumnFallbackMargin;
        double right = after?.BoundingBox.Left ?? double.MaxValue;

        List<Word> label = [];

        foreach (Line line in lines.Where(line => line.Baseline < header.Baseline - SameLineTolerance
            && line.Baseline >= header.Baseline - ResultLabelReach))
        {
            if (line.Words.Any(word => DateToken().IsMatch(word.Text))) break;

            label.AddRange(line.Words.Where(word => CenterOf(word) >= left && CenterOf(word) < right));
        }

        string text = string.Join(' ', label.Select(word => word.Text.Normalize(NormalizationForm.FormC))).Trim();

        return text.Length == 0 ? null : new ResultCell(text, FillBehind(page.Fills, label));
    }

    private static (double Red, double Green, double Blue)? FillBehind(IReadOnlyList<Fill> fills, List<Word> label)
    {
        double left = label.Min(word => word.BoundingBox.Left);
        double right = label.Max(word => word.BoundingBox.Right);
        double bottom = label.Min(word => word.BoundingBox.Bottom);
        double top = label.Max(word => word.BoundingBox.Top);
        double centerX = (left + right) / 2;
        double centerY = (bottom + top) / 2;
        double smallestSide = (top - bottom) / 2;

        return fills
            .Where(fill => fill.Left <= centerX && fill.Right >= centerX
                && fill.Bottom <= centerY && fill.Top >= centerY
                && fill.Right - fill.Left >= smallestSide
                && fill.Top - fill.Bottom >= smallestSide)
            .OrderBy(fill => (fill.Right - fill.Left) * (fill.Top - fill.Bottom))
            .Select(fill => ((double, double, double)?)fill.Color)
            .FirstOrDefault();
    }

    private static IReadOnlyList<Fill> FillsOf(Page page)
    {
        List<Fill> fills = [];

        foreach (PdfPath path in page.Paths)
        {
            if (!path.IsFilled || path.FillColor is not IColor color) continue;
            if (path.GetBoundingRectangle() is not PdfRectangle bounds) continue;

            (double Red, double Green, double Blue)? rgb = RgbOf(color);

            if (rgb is null) continue;

            fills.Add(new Fill(bounds.Left, bounds.Bottom, bounds.Right, bounds.Top, rgb.Value));
        }

        return fills;
    }

    private static (double Red, double Green, double Blue)? RgbOf(IColor color)
    {
        try
        {
            (double red, double green, double blue) = color.ToRGBValues();

            return (red, green, blue);
        }
        catch (Exception exception) when (exception is InvalidOperationException or NotSupportedException)
        {
            return null;
        }
    }

    private static Conclusion ConclusionAt(IReadOnlyList<Line> lines, int titleIndex)
    {
        List<StringBuilder> paragraphs = [];
        Line previous = lines[titleIndex];

        for (int index = titleIndex + 1; index < lines.Count; index++)
        {
            Line line = lines[index];

            if (previous.Baseline - line.Baseline > ParagraphBreakGap) break;
            if (EndsTheConclusion(line)) break;

            if (paragraphs.Count == 0 || StartsAParagraph(paragraphs[^1], line))
                paragraphs.Add(new StringBuilder(line.Text));
            else if (paragraphs[^1][^1] == '-')
                paragraphs[^1].Append(line.Text);
            else
                paragraphs[^1].Append(' ').Append(line.Text);

            previous = line;
        }

        return new Conclusion(
            lines[titleIndex].Text,
            [.. paragraphs.Select(paragraph => paragraph.ToString())]);
    }

    private static bool EndsTheConclusion(Line line)
    {
        return line.Key.StartsWith(ConclusionPrefix, StringComparison.Ordinal)
            || line.Key.Contains(ChromatographyTable, StringComparison.Ordinal)
            || line.Key.Contains(PhysicalChemicalTable, StringComparison.Ordinal)
            || ConclusionEnds.Any(end => line.Key.StartsWith(end, StringComparison.Ordinal));
    }

    private static bool StartsAParagraph(StringBuilder current, Line line)
    {
        char last = current[^1];
        char first = line.Text[0];

        return last is '.' or '!' or '?' or ':' && (char.IsUpper(first) || char.IsDigit(first));
    }

    private static IReadOnlyList<Line> LinesOf(Page page)
    {
        List<Line> lines = [];
        List<Word> current = [];
        double baseline = double.NaN;

        foreach (Word word in page.GetWords()
            .Where(word => !string.IsNullOrWhiteSpace(word.Text))
            .OrderByDescending(word => word.BoundingBox.Bottom))
        {
            if (current.Count > 0 && baseline - word.BoundingBox.Bottom > SameLineTolerance)
            {
                lines.Add(Line.Of(baseline, current));
                current = [];
            }

            if (current.Count == 0) baseline = word.BoundingBox.Bottom;

            current.Add(word);
        }

        if (current.Count > 0) lines.Add(Line.Of(baseline, current));

        return lines;
    }

    private static double CenterOf(Word word)
    {
        return (word.BoundingBox.Left + word.BoundingBox.Right) / 2;
    }

    private static string KeyOf(string text)
    {
        string decomposed = text.Normalize(NormalizationForm.FormD);
        StringBuilder key = new(decomposed.Length);

        foreach (char character in decomposed)
        {
            if (CharUnicodeInfo.GetUnicodeCategory(character) != UnicodeCategory.NonSpacingMark)
                key.Append(char.ToUpperInvariant(character));
        }

        return key.ToString();
    }

    [GeneratedRegex(@"^\d{1,2}/\d{1,2}/\d{2,4}$")]
    private static partial Regex DateToken();

    private sealed record Conclusion(string Title, IReadOnlyList<string> Paragraphs);

    private sealed record ResultCell(string Label, (double Red, double Green, double Blue)? Color);

    private sealed record Fill(
        double Left,
        double Bottom,
        double Right,
        double Top,
        (double Red, double Green, double Blue) Color);

    private sealed record PageContent(IReadOnlyList<Line> Lines, IReadOnlyList<Fill> Fills);

    private sealed record Line(double Baseline, IReadOnlyList<Word> Words, string Text, string Key)
    {
        public static Line Of(double baseline, IEnumerable<Word> words)
        {
            List<Word> ordered = [.. words.OrderBy(word => word.BoundingBox.Left)];
            string text = string.Join(' ', ordered.Select(word => word.Text.Normalize(NormalizationForm.FormC)));

            return new Line(baseline, ordered, text, KeyOf(text));
        }
    }
}
