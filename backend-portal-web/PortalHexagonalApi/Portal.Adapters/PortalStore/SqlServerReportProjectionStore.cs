using System.Data.Common;
using System.Text.Encodings.Web;
using System.Text.Json;

using Microsoft.EntityFrameworkCore;

using Portal.Adapters.AutoLab;
using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportProjection;
using Portal.Application.ReportProjection.Exceptions;

namespace Portal.Adapters.PortalStore;

public sealed class SqlServerReportProjectionStore : IReportProjectionStore
{
    private const int KeysPerQuery = 500;

    private static readonly JsonSerializerOptions ParagraphsFormat = new()
    {
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping
    };

    private readonly PortalStoreDbContext _database;

    public SqlServerReportProjectionStore(PortalStoreDbContext database)
    {
        _database = database;
    }

    public bool IsConnected => true;

    public Task<IReadOnlyList<LoadScope>> GetActiveScopesAsync(CancellationToken cancellationToken)
    {
        return GuardAsync<IReadOnlyList<LoadScope>>(async () =>
        [
            .. (await _database.LoadScopes
                    .AsNoTracking()
                    .Where(row => row.Active)
                    .OrderBy(row => row.Cnpj)
                    .ToListAsync(cancellationToken))
                .Select(row => new LoadScope(
                    row.Cnpj,
                    row.Active,
                    row.ProcessedUntilIssueDate,
                    row.ProcessedUntilReportId,
                    row.LastRunAt))
        ]);
    }

    public Task SaveProgressAsync(
        string cnpj,
        DateTime processedUntilIssueDate,
        Guid processedUntilReportId,
        CancellationToken cancellationToken)
    {
        return GuardAsync(() => _database.LoadScopes
            .Where(row => row.Cnpj == cnpj)
            .ExecuteUpdateAsync(
                setters => setters
                    .SetProperty(row => row.ProcessedUntilIssueDate, (DateTime?)processedUntilIssueDate)
                    .SetProperty(row => row.ProcessedUntilReportId, (Guid?)processedUntilReportId),
                cancellationToken));
    }

    public Task SaveRunAsync(string cnpj, DateTime ranAt, CancellationToken cancellationToken)
    {
        return GuardAsync(() => _database.LoadScopes
            .Where(row => row.Cnpj == cnpj)
            .ExecuteUpdateAsync(
                setters => setters.SetProperty(row => row.LastRunAt, (DateTime?)ranAt),
                cancellationToken));
    }

    public Task<IReadOnlyList<ReadingControl>> GetControlsAsync(
        IReadOnlyCollection<Guid> signedFileIds,
        CancellationToken cancellationToken)
    {
        return GuardAsync<IReadOnlyList<ReadingControl>>(async () =>
        {
            List<ReadingControl> controls = [];

            foreach (Guid[] chunk in signedFileIds.Distinct().Chunk(KeysPerQuery))
            {
                List<Guid> wanted = [.. chunk];

                controls.AddRange((await _database.ReportReadings
                        .AsNoTracking()
                        .Where(row => wanted.Contains(row.SignedFileId))
                        .Select(row => new
                        {
                            row.SignedFileId,
                            row.Outcome,
                            row.ExtractorVersion,
                            row.ReadAt,
                            row.Attempts
                        })
                        .ToListAsync(cancellationToken))
                    .Select(row => new ReadingControl(
                        row.SignedFileId,
                        ReportReadOutcomeText.Parse(row.Outcome),
                        row.ExtractorVersion,
                        row.ReadAt,
                        row.Attempts)));
            }

            return controls;
        });
    }

    public Task<IReadOnlyList<StoredReading>> GetUnreadableReadingsAsync(
        string cnpj,
        CancellationToken cancellationToken)
    {
        return GuardAsync<IReadOnlyList<StoredReading>>(async () =>
        [
            .. (await _database.ReportReadings
                    .AsNoTracking()
                    .Where(row => row.Cnpj == cnpj
                        && row.Outcome == ReportReadOutcomeText.Unreadable
                        && row.Attempts < ReadingPolicy.MaxAttempts)
                    .OrderBy(row => row.ReadAt)
                    .ToListAsync(cancellationToken))
                .Select(ToReading)
        ]);
    }

    public Task<IReadOnlyList<StoredReading>> GetReadingsAsync(
        IReadOnlyCollection<string> cnpjs,
        IReadOnlyCollection<string> identifications,
        CancellationToken cancellationToken)
    {
        return GuardAsync<IReadOnlyList<StoredReading>>(async () =>
        {
            List<string> companies = [.. cnpjs];
            List<StoredReading> readings = [];

            foreach (string[] chunk in identifications.Chunk(KeysPerQuery))
            {
                List<string> wanted = [.. chunk];

                readings.AddRange((await _database.ReportReadings
                        .AsNoTracking()
                        .Include(row => row.Sections)
                        .Where(row => companies.Contains(row.Cnpj) && wanted.Contains(row.Identification))
                        .ToListAsync(cancellationToken))
                    .Select(ToReading));
            }

            return readings;
        });
    }

    public Task<IReadOnlyList<ReadSummary>> GetReadSummariesAsync(
        string cnpj,
        IReadOnlyCollection<string>? identifications,
        CancellationToken cancellationToken)
    {
        return GuardAsync<IReadOnlyList<ReadSummary>>(async () =>
        {
            if (identifications is null)
                return await SummariesOfAsync(_database.ReportReadings.Where(row => row.Cnpj == cnpj), cancellationToken);

            List<ReadSummary> summaries = [];

            foreach (string[] chunk in identifications.Chunk(KeysPerQuery))
            {
                List<string> wanted = [.. chunk];

                summaries.AddRange(await SummariesOfAsync(
                    _database.ReportReadings.Where(row => row.Cnpj == cnpj && wanted.Contains(row.Identification)),
                    cancellationToken));
            }

            return summaries;
        });
    }

    public Task SaveReadingAsync(StoredReading reading, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(reading);

        return GuardAsync(async () =>
        {
            ReportReadingRow? row = await _database.ReportReadings
                .Include(existing => existing.Sections)
                .FirstOrDefaultAsync(existing => existing.SignedFileId == reading.SignedFileId, cancellationToken);

            if (row is null)
            {
                row = new ReportReadingRow { SignedFileId = reading.SignedFileId };
                _database.ReportReadings.Add(row);
            }

            row.ReportId = reading.ReportId;
            row.Identification = Fit(reading.Identification, PortalStoreDbContext.IdentificationLength)!;
            row.Cnpj = reading.Cnpj;
            row.EquipmentKey = Fit(reading.EquipmentKey, PortalStoreDbContext.EquipmentKeyLength);
            row.IssueDate = reading.IssueDate;
            row.Outcome = ReportReadOutcomeText.Of(reading.Outcome);
            row.ExtractorVersion = reading.ExtractorVersion;
            row.ReadAt = reading.ReadAt;
            row.Attempts = reading.Attempts;

            SetSection(row, ReportSectionResultRow.Chromatography, reading.Chromatography);
            SetSection(row, ReportSectionResultRow.PhysicalChemical, reading.PhysicalChemical);

            await SaveAsync(cancellationToken);
            return true;
        });
    }

    public Task SaveStatusesAsync(
        string cnpj,
        IReadOnlyCollection<EquipmentStatus> statuses,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(statuses);

        return GuardAsync(async () =>
        {
            foreach (EquipmentStatus[] chunk in statuses.Chunk(KeysPerQuery))
            {
                List<string> keys = [.. chunk.Select(status => status.Equipment.EquipmentKey)];

                Dictionary<string, EquipmentStatusRow> existing = (await _database.EquipmentStatuses
                        .Where(row => row.Cnpj == cnpj && keys.Contains(row.EquipmentKey))
                        .ToListAsync(cancellationToken))
                    .GroupBy(row => row.EquipmentKey, StringComparer.Ordinal)
                    .ToDictionary(found => found.Key, found => found.First(), StringComparer.Ordinal);

                foreach (EquipmentStatus status in chunk)
                {
                    if (!existing.TryGetValue(status.Equipment.EquipmentKey, out EquipmentStatusRow? row))
                    {
                        row = new EquipmentStatusRow { Cnpj = cnpj, EquipmentKey = status.Equipment.EquipmentKey };
                        _database.EquipmentStatuses.Add(row);
                        existing[row.EquipmentKey] = row;
                    }

                    Fill(row, status);
                }

                await SaveAsync(cancellationToken);
            }

            return true;
        });
    }

    public Task<IReadOnlyList<EquipmentStatus>> GetStatusesAsync(
        IReadOnlyCollection<string> cnpjs,
        IReadOnlyCollection<string> equipmentKeys,
        CancellationToken cancellationToken)
    {
        return GuardAsync<IReadOnlyList<EquipmentStatus>>(async () =>
        {
            List<string> companies = [.. cnpjs];
            HashSet<string> exact = new(equipmentKeys, StringComparer.Ordinal);
            List<EquipmentStatus> statuses = [];

            foreach (string[] chunk in equipmentKeys.Chunk(KeysPerQuery))
            {
                List<string> wanted = [.. chunk];

                statuses.AddRange((await _database.EquipmentStatuses
                        .AsNoTracking()
                        .Where(row => companies.Contains(row.Cnpj) && wanted.Contains(row.EquipmentKey))
                        .ToListAsync(cancellationToken))
                    .Where(row => exact.Contains(row.EquipmentKey))
                    .Select(ToStatus));
            }

            return statuses;
        });
    }

    public Task<Page<EquipmentStatus>> ListStatusesAsync(
        IReadOnlyCollection<string> cnpjs,
        OverallStatus status,
        EquipmentSearch? search,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(page);

        return GuardAsync(async () =>
        {
            List<string> companies = [.. cnpjs];
            string wanted = OverallStatusText.Of(status);

            IQueryable<EquipmentStatusRow> rows = _database.EquipmentStatuses
                .AsNoTracking()
                .Where(row => companies.Contains(row.Cnpj) && row.OverallStatus == wanted);

            if (search is not null)
            {
                string term = search.Term;

                rows = rows.Where(row => row.SerialNumber!.Contains(term)
                    || row.Tag!.Contains(term)
                    || row.DisplayLabel.Contains(term)
                    || row.Identification.Contains(term));
            }

            Page<EquipmentStatusRow> found = await rows
                .OrderByDescending(row => row.LastReportDate)
                .ThenByDescending(row => row.MostRecentReportDate)
                .ThenBy(row => row.EquipmentKey)
                .ThenBy(row => row.Cnpj)
                .ToPageAsync(page, cancellationToken);

            return found.WithItems<EquipmentStatus>([.. found.Items.Select(ToStatus)]);
        });
    }

    private async Task SaveAsync(CancellationToken cancellationToken)
    {
        try
        {
            await _database.SaveChangesAsync(cancellationToken);
        }
        finally
        {
            _database.ChangeTracker.Clear();
        }
    }

    private static async Task<List<ReadSummary>> SummariesOfAsync(
        IQueryable<ReportReadingRow> readings,
        CancellationToken cancellationToken)
    {
        var rows = await readings
            .AsNoTracking()
            .Select(row => new
            {
                row.Identification,
                row.IssueDate,
                Sections = row.Sections
                    .Select(section => new { section.Section, section.Conformity, section.ResultLabel })
                    .ToList()
            })
            .ToListAsync(cancellationToken);

        return [.. rows.Select(row => new ReadSummary(
            row.Identification,
            row.IssueDate,
            row.Sections
                .Where(section => section.Section == ReportSectionResultRow.Chromatography)
                .Select(section => new SectionStatus(ConformityText.Parse(section.Conformity), section.ResultLabel))
                .FirstOrDefault(),
            row.Sections
                .Where(section => section.Section == ReportSectionResultRow.PhysicalChemical)
                .Select(section => new SectionStatus(ConformityText.Parse(section.Conformity), section.ResultLabel))
                .FirstOrDefault()))];
    }

    private static void SetSection(ReportReadingRow row, string name, ReportSection? section)
    {
        ReportSectionResultRow? stored = row.Sections.FirstOrDefault(found => found.Section == name);

        if (section is null)
        {
            if (stored is not null) row.Sections.Remove(stored);

            return;
        }

        if (stored is null)
        {
            stored = new ReportSectionResultRow { SignedFileId = row.SignedFileId, Section = name };
            row.Sections.Add(stored);
        }

        stored.ResultLabel = Fit(section.ResultLabel, PortalStoreDbContext.LabelLength);
        stored.Conformity = ConformityText.Of(section.Conformity);
        stored.ResultColor = section.ResultColor;
        stored.Title = Fit(section.Title, PortalStoreDbContext.LabelLength);
        stored.Paragraphs = JsonSerializer.Serialize(section.Paragraphs, ParagraphsFormat);
    }

    private static StoredReading ToReading(ReportReadingRow row)
    {
        return new StoredReading(
            row.SignedFileId,
            row.ReportId,
            row.Identification,
            row.Cnpj,
            row.EquipmentKey,
            row.IssueDate,
            ReportReadOutcomeText.Parse(row.Outcome),
            row.ExtractorVersion,
            row.ReadAt,
            row.Attempts,
            SectionOf(row, ReportSectionResultRow.Chromatography),
            SectionOf(row, ReportSectionResultRow.PhysicalChemical));
    }

    private static ReportSection? SectionOf(ReportReadingRow row, string name)
    {
        ReportSectionResultRow? section = row.Sections.FirstOrDefault(found => found.Section == name);

        return section is null
            ? null
            : new ReportSection(
                section.ResultLabel,
                ConformityText.Parse(section.Conformity),
                section.Title,
                JsonSerializer.Deserialize<List<string>>(section.Paragraphs) ?? [])
            {
                ResultColor = section.ResultColor
            };
    }

    private static void Fill(EquipmentStatusRow row, EquipmentStatus status)
    {
        ProjectedEquipment equipment = status.Equipment;

        row.SerialNumber = Fit(equipment.SerialNumber, PortalStoreDbContext.EquipmentKeyLength);
        row.DisplayLabel = Fit(equipment.DisplayLabel, PortalStoreDbContext.EquipmentKeyLength)!;
        row.Tag = Fit(equipment.EquipmentTag, PortalStoreDbContext.IdentificationLength);
        row.EquipmentType = Fit(equipment.EquipmentType, PortalStoreDbContext.IdentificationLength);
        row.NameplateKnown = equipment.NameplateKnown;
        row.Company = Fit(equipment.Company, PortalStoreDbContext.EquipmentKeyLength)!;
        row.Identification = Fit(equipment.Identification, PortalStoreDbContext.IdentificationLength)!;
        row.ReportCount = equipment.ReportCount;
        row.MostRecentReportDate = equipment.MostRecentReportDate;
        row.LastReportDate = status.LastReportDate;
        row.HasChromatography = status.Chromatography is not null;
        row.ChromatographyConformity = ConformityText.Of(status.Chromatography?.Conformity);
        row.ChromatographyLabel = Fit(status.Chromatography?.ResultLabel, PortalStoreDbContext.LabelLength);
        row.HasPhysicalChemical = status.PhysicalChemical is not null;
        row.PhysicalChemicalConformity = ConformityText.Of(status.PhysicalChemical?.Conformity);
        row.PhysicalChemicalLabel = Fit(status.PhysicalChemical?.ResultLabel, PortalStoreDbContext.LabelLength);
        row.OverallStatus = OverallStatusText.Of(status.Overall);
        row.UpdatedAt = status.UpdatedAt;
    }

    private static EquipmentStatus ToStatus(EquipmentStatusRow row)
    {
        OverallStatusText.TryParse(row.OverallStatus, out OverallStatus overall);

        return new EquipmentStatus(
            row.Cnpj,
            new ProjectedEquipment(
                row.EquipmentKey,
                row.SerialNumber,
                row.DisplayLabel,
                row.Tag,
                row.EquipmentType,
                row.NameplateKnown,
                row.Company,
                row.Identification,
                row.ReportCount,
                row.MostRecentReportDate,
                []),
            row.LastReportDate,
            row.HasChromatography
                ? new SectionStatus(ConformityText.Parse(row.ChromatographyConformity), row.ChromatographyLabel)
                : null,
            row.HasPhysicalChemical
                ? new SectionStatus(ConformityText.Parse(row.PhysicalChemicalConformity), row.PhysicalChemicalLabel)
                : null,
            overall,
            row.UpdatedAt);
    }

    private static string? Fit(string? value, int maxLength)
    {
        return value is null || value.Length <= maxLength ? value : value[..maxLength];
    }

    private static async Task<T> GuardAsync<T>(Func<Task<T>> work)
    {
        try
        {
            return await work();
        }
        catch (Exception failure) when (failure is DbException
            or DbUpdateException
            or InvalidOperationException
            or TimeoutException)
        {
            throw new ReportProjectionUnavailableException("O banco do portal não respondeu.", failure);
        }
    }
}
