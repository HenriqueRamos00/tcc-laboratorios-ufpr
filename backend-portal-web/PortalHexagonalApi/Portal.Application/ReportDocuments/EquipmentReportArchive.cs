using System.Globalization;
using System.Runtime.CompilerServices;
using System.Text;

using Portal.Domain.LabResults;

namespace Portal.Application.ReportDocuments;

public sealed record ReportArchiveCandidate(
    string SourceKey,
    ReportAnalysisType AnalysisType,
    DateOnly Date,
    string? ReportNumber);

public sealed record ReportArchiveEntry(string SourceKey, string Name);

public sealed record ArchivedReportFile(string Name, Stream Content);

public sealed class EquipmentReportArchive
{
    public const int MaxReports = 200;

    private const int MaxSegmentLength = 80;

    private readonly Func<string, CancellationToken, Task<Stream?>> _read;

    public EquipmentReportArchive(
        string equipmentLabel,
        IEnumerable<ReportArchiveCandidate> candidates,
        Func<string, CancellationToken, Task<Stream?>> read)
    {
        ArgumentNullException.ThrowIfNull(candidates);
        ArgumentNullException.ThrowIfNull(read);

        FileName = $"laudos-{SafeSegment(equipmentLabel, "equipamento")}.zip";
        Entries = Lay(candidates);
        _read = read;
    }

    public string FileName { get; }

    public IReadOnlyList<ReportArchiveEntry> Entries { get; }

    public async IAsyncEnumerable<ArchivedReportFile> ReadFilesAsync(
        [EnumeratorCancellation] CancellationToken cancellationToken)
    {
        foreach (ReportArchiveEntry entry in Entries)
        {
            cancellationToken.ThrowIfCancellationRequested();

            Stream? content = await _read(entry.SourceKey, cancellationToken);

            if (content is null) continue;

            yield return new ArchivedReportFile(entry.Name, content);
        }
    }

    public static string FolderOf(ReportAnalysisType analysisType) => analysisType switch
    {
        ReportAnalysisType.Chromatography => "Cromatografia",
        ReportAnalysisType.PhysicalChemical => "Fisico-Quimica",
        _ => "Outros-Ensaios"
    };

    public static string SafeSegment(string? text, string fallback)
    {
        if (string.IsNullOrWhiteSpace(text)) return fallback;

        StringBuilder safe = new();

        foreach (char character in text.Trim().Normalize(NormalizationForm.FormD))
        {
            if (CharUnicodeInfo.GetUnicodeCategory(character) == UnicodeCategory.NonSpacingMark) continue;

            if (char.IsAsciiLetterOrDigit(character))
                safe.Append(character);
            else if (safe.Length > 0 && safe[^1] != '-')
                safe.Append('-');
        }

        string result = safe.ToString().Trim('-');

        if (result.Length > MaxSegmentLength) result = result[..MaxSegmentLength].TrimEnd('-');

        return result.Length == 0 ? fallback : result;
    }

    private static IReadOnlyList<ReportArchiveEntry> Lay(IEnumerable<ReportArchiveCandidate> candidates)
    {
        HashSet<string> seenSources = new(StringComparer.Ordinal);
        HashSet<string> usedNames = new(StringComparer.OrdinalIgnoreCase);
        List<ReportArchiveEntry> entries = [];

        foreach (ReportArchiveCandidate candidate in candidates)
        {
            if (!seenSources.Add(candidate.SourceKey)) continue;

            string stem = string.Create(
                CultureInfo.InvariantCulture,
                $"{FolderOf(candidate.AnalysisType)}/{candidate.Date:yyyy-MM-dd}_{SafeSegment(candidate.ReportNumber, "laudo")}");

            string name = stem + ".pdf";

            for (int suffix = 2; !usedNames.Add(name); suffix++)
                name = string.Create(CultureInfo.InvariantCulture, $"{stem}-{suffix}.pdf");

            entries.Add(new ReportArchiveEntry(candidate.SourceKey, name));
        }

        return entries;
    }
}
