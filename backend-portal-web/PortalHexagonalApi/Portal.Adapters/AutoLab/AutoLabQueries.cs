using Portal.Application.Visibility;

namespace Portal.Adapters.AutoLab;

public static class AutoLabQueries
{
    public static IQueryable<string> EquipmentKeys(
        AutoLabDbContext database,
        VisibilityScope scope)
    {
        return TestRowsIn(database, scope)
            .GroupBy(row => string.IsNullOrWhiteSpace(row.NumSerie) ? row.ProtocoloLactec : row.NumSerie)
            .Where(equipment => equipment.Key != null)
            .Select(equipment => new
            {
                Key = equipment.Key!,
                Latest = equipment.Max(row => row.DataFinalAnalise)
            })
            .OrderByDescending(equipment => equipment.Latest)
            .ThenBy(equipment => equipment.Key)
            .Select(equipment => equipment.Key);
    }

    public static IQueryable<VwDadosEnsaiosRecord> RowsOfEquipmentKeys(
        AutoLabDbContext database,
        VisibilityScope scope,
        List<string> equipmentKeys,
        int rowLimit)
    {
        return TestRowsIn(database, scope)
            .Where(row => equipmentKeys.Contains(row.NumSerie!) || equipmentKeys.Contains(row.ProtocoloLactec!))
            .OrderBy(row => row.ProtocoloLactec)
            .Take(rowLimit);
    }

    public static IQueryable<string> ProtocolsOfEquipment(
        AutoLabDbContext database,
        VisibilityScope scope,
        string equipmentKey)
    {
        return TestRowsIn(database, scope)
            .Where(row => (row.NumSerie == equipmentKey || row.ProtocoloLactec == equipmentKey)
                && row.ProtocoloLactec != null)
            .GroupBy(row => row.ProtocoloLactec)
            .Select(report => new
            {
                Protocol = report.Key!,
                Latest = report.Max(row => row.DataFinalAnalise)
            })
            .OrderByDescending(report => report.Latest)
            .ThenBy(report => report.Protocol)
            .Select(report => report.Protocol);
    }

    public static IQueryable<VwDadosEnsaiosRecord> RowsOfProtocols(
        AutoLabDbContext database,
        VisibilityScope scope,
        List<string> protocols,
        int rowLimit)
    {
        return TestRowsIn(database, scope)
            .Where(row => protocols.Contains(row.ProtocoloLactec!))
            .OrderBy(row => row.ProtocoloLactec)
            .Take(rowLimit);
    }

    public static IQueryable<VwDadosEnsaiosRecord> RowsOfProtocol(
        AutoLabDbContext database,
        VisibilityScope scope,
        string protocol,
        int rowLimit)
    {
        return TestRowsIn(database, scope)
            .Where(row => row.ProtocoloLactec == protocol)
            .OrderBy(row => row.Name)
            .Take(rowLimit);
    }

    public static IQueryable<EquipamentosRecord> RegistryRowsOf(
        AutoLabDbContext database,
        List<string> serialNumbers,
        int rowLimit)
    {
        return database.Equipamentos
            .Where(row => serialNumbers.Contains(row.NumSerie!))
            .OrderBy(row => row.NumSerie)
            .Take(rowLimit);
    }

    public static IQueryable<SituaProcessRecord> ProgressRowsOf(
        AutoLabDbContext database,
        List<string> quoteCodes,
        int rowLimit)
    {
        return database.SituaProcess
            .Where(row => quoteCodes.Contains(row.OrcamentoCRM!))
            .OrderBy(row => row.OrcamentoCRM)
            .Take(rowLimit);
    }

    public static IQueryable<SignedDocumentRow> SignedDocumentOf(
        AutoLabDbContext database,
        string reportIdentification)
    {
        IQueryable<Guid?> currentSignedPdf = SignedIssues(database)
            .Where(report => report.Identificacao == reportIdentification)
            .OrderBy(report => report.RelatorioEnsaioSubstituto != null)
            .ThenByDescending(report => report.Revisao)
            .ThenByDescending(report => report.DataPublicacao)
            .ThenByDescending(report => report.DataEmissao)
            .ThenBy(report => report.Oid)
            .Select(report => report.PdfAssinado)
            .Take(1);

        return LiveFilesAmong(database, currentSignedPdf);
    }

    public static IQueryable<string> IdentificationsWithSignedDocument(
        AutoLabDbContext database,
        List<string> reportIdentifications,
        int rowLimit)
    {
        return SignedIssues(database)
            .Where(report => reportIdentifications.Contains(report.Identificacao!))
            .Select(report => report.Identificacao!)
            .Distinct()
            .OrderBy(identification => identification)
            .Take(rowLimit);
    }

    public static IQueryable<ReportIssueRow> CurrentIssueOf(
        AutoLabDbContext database,
        string reportIdentification)
    {
        return database.RelatorioEmissao
            .Where(report => report.Identificacao == reportIdentification && report.GCRecord == null)
            .OrderBy(report => report.RelatorioEnsaioSubstituto != null)
            .ThenByDescending(report => report.Revisao)
            .ThenByDescending(report => report.DataPublicacao)
            .ThenByDescending(report => report.DataEmissao)
            .ThenBy(report => report.Oid)
            .Select(report => new ReportIssueRow(report.TituloRelatorio, report.Conclusao))
            .Take(1);
    }

    public static IQueryable<VwRelatoriosEnsaiosRecord> Reports(
        AutoLabDbContext database,
        VisibilityScope scope,
        string? quoteCode)
    {
        IQueryable<VwRelatoriosEnsaiosRecord> reports = database.RelatoriosEnsaios;

        if (scope.Company is string company)
            reports = reports.Where(report => report.ClienteSolicitante == company);

        if (quoteCode is not null)
            reports = reports.Where(report => report.OrcamentoCRM == quoteCode);

        return reports
            .OrderByDescending(report => report.DataPublicacao)
            .ThenByDescending(report => report.DataEmissao)
            .ThenBy(report => report.Oid);
    }

    public static IQueryable<SignedDocumentRow> SignedDocumentOfReport(
        AutoLabDbContext database,
        VisibilityScope scope,
        Guid reportId)
    {
        IQueryable<VwRelatoriosEnsaiosRecord> reports = database.RelatoriosEnsaios
            .Where(report => report.Oid == reportId);

        if (scope.Company is string company)
            reports = reports.Where(report => report.ClienteSolicitante == company);

        return LiveFilesAmong(
            database,
            reports.Where(report => report.PdfAssinado != null).Select(report => report.PdfAssinado).Take(1));
    }

    private static IQueryable<VwDadosEnsaiosRecord> TestRowsIn(AutoLabDbContext database, VisibilityScope scope)
    {
        ArgumentNullException.ThrowIfNull(scope);

        return scope.Company is string company
            ? database.DadosEnsaios.Where(row => row.EmpresaTrafo == company)
            : database.DadosEnsaios;
    }

    private static IQueryable<RelatorioEmissaoRecord> SignedIssues(AutoLabDbContext database)
    {
        return database.RelatorioEmissao
            .Where(report => report.GCRecord == null
                && report.PdfAssinado != null
                && database.FileData.Any(file => file.GCRecord == null && (Guid?)file.Oid == report.PdfAssinado));
    }

    private static IQueryable<SignedDocumentRow> LiveFilesAmong(
        AutoLabDbContext database,
        IQueryable<Guid?> fileIds)
    {
        return database.FileData
            .Where(file => file.GCRecord == null && fileIds.Contains((Guid?)file.Oid))
            .Select(file => new SignedDocumentRow(file.FileName, file.Content))
            .Take(1);
    }
}
