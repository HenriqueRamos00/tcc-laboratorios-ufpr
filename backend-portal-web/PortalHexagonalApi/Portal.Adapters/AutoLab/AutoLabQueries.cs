using Microsoft.EntityFrameworkCore;

using Portal.Application.CompanyRecords;
using Portal.Application.LabResults;

namespace Portal.Adapters.AutoLab;

public static class AutoLabQueries
{
    public const string OrdinalCollation = "Latin1_General_BIN2";

    public const string CaseInsensitiveCollation = "Latin1_General_CI_AS";

    public static IQueryable<ClienteRow> ClientsOfCnpjs(
        AutoLabDbContext database,
        List<string> cnpjs,
        int rowLimit)
    {
        return database.Clientes
            .Where(client => client.GCRecord == null
                && cnpjs.Contains(client.CadastroNacional!
                    .Replace(".", "")
                    .Replace("/", "")
                    .Replace("-", "")
                    .Replace(" ", "")))
            .OrderBy(client => client.Oid)
            .Select(client => new ClienteRow(client.Oid, client.CadastroNacional))
            .Take(rowLimit);
    }

    public static IQueryable<string> ProtocolsOfClients(
        AutoLabDbContext database,
        List<Guid?> clientIds,
        int rowLimit)
    {
        return database.SolicitacoesServico
            .Where(request => request.GCRecord == null && clientIds.Contains(request.Cliente))
            .Join(
                database.AmostrasObjeto.Where(sample => sample.GCRecord == null),
                request => (Guid?)request.Oid,
                sample => sample.SolicitacaoServico,
                (request, sample) => sample.ProtocoloAmostra)
            .Join(
                database.ProtocolosAmostra.Where(protocol => protocol.GCRecord == null
                    && protocol.Identificacao != null),
                protocolId => protocolId,
                protocol => (Guid?)protocol.Oid,
                (protocolId, protocol) => protocol.Identificacao!)
            .Distinct()
            .OrderBy(identification => identification)
            .Take(rowLimit);
    }

    public static IQueryable<ReportOfClientRow> ReportsOfClients(
        AutoLabDbContext database,
        List<Guid?> clientIds,
        int rowLimit)
    {
        return database.RelatorioEmissao
            .Where(report => report.GCRecord == null)
            .Join(
                database.SolicitacoesServico.Where(request => request.GCRecord == null
                    && clientIds.Contains(request.Cliente)),
                report => report.Solicitacao,
                request => (Guid?)request.Oid,
                (report, request) => new { Report = report, Request = request })
            .OrderBy(found => found.Report.Oid)
            .Take(rowLimit)
            .Select(found => new ReportOfClientRow(
                found.Request.Cliente,
                new CompanyReportRow(
                    found.Report.Oid,
                    found.Report.Identificacao,
                    found.Request.NumeroSolicitacao,
                    found.Request.OrcamentoCRM,
                    found.Report.DataEmissao,
                    found.Report.DataPublicacao,
                    found.Report.Revisao,
                    found.Report.RelatorioEnsaioSubstituto != null,
                    found.Report.PdfAssinado,
                    null)));
    }

    public static IQueryable<SignedReportRow> SignedReportsOfClients(
        AutoLabDbContext database,
        List<Guid?> clientIds,
        DateTime? fromIssueDate,
        DateTime? afterIssueDate,
        Guid? afterReportId,
        int rowLimit)
    {
        IQueryable<RelatorioEmissaoRecord> signed = database.RelatorioEmissao
            .Where(report => report.GCRecord == null
                && report.PdfAssinado != null
                && report.DataEmissao != null)
            .Join(
                database.SolicitacoesServico.Where(request => request.GCRecord == null
                    && clientIds.Contains(request.Cliente)),
                report => report.Solicitacao,
                request => (Guid?)request.Oid,
                (report, request) => report);

        if (fromIssueDate is DateTime from)
            signed = signed.Where(report => report.DataEmissao >= from);

        if (afterIssueDate is DateTime issued && afterReportId is Guid reportId)
            signed = signed.Where(report => report.DataEmissao > issued
                || (report.DataEmissao == issued && report.Oid.CompareTo(reportId) > 0));

        return signed
            .OrderBy(report => report.DataEmissao)
            .ThenBy(report => report.Oid)
            .Take(rowLimit)
            .Select(report => new SignedReportRow(
                report.Oid,
                report.Identificacao,
                report.DataEmissao!.Value,
                report.PdfAssinado!.Value));
    }

    public static IQueryable<ProtocolSummaryRow> ProtocolSummaries(
        AutoLabDbContext database,
        List<string> authorizedProtocols,
        int rowLimit)
    {
        return TestRowsOf(database, authorizedProtocols)
            .Select(row => new
            {
                row.ProtocoloLactec,
                row.Identificacao,
                row.NumSerie,
                row.EmpresaTrafo,
                row.DataEmissao,
                row.DataFinalAnalise
            })
            .Distinct()
            .OrderBy(row => row.ProtocoloLactec)
            .Take(rowLimit)
            .Select(row => new ProtocolSummaryRow(
                row.ProtocoloLactec,
                row.Identificacao,
                row.NumSerie,
                row.EmpresaTrafo,
                row.DataEmissao,
                row.DataFinalAnalise));
    }

    public static IQueryable<ProtocolReportRow> ReportsOfProtocols(
        AutoLabDbContext database,
        List<string> authorizedProtocols,
        int rowLimit)
    {
        return TestRowsOf(database, authorizedProtocols)
            .Where(row => row.Identificacao != null)
            .GroupBy(row => new { row.ProtocoloLactec, row.Identificacao })
            .Select(report => new
            {
                report.Key.ProtocoloLactec,
                report.Key.Identificacao,
                DataEmissao = report.Max(row => row.DataEmissao)
            })
            .OrderBy(report => report.ProtocoloLactec)
            .ThenBy(report => report.Identificacao)
            .Take(rowLimit)
            .Select(report => new ProtocolReportRow(
                report.ProtocoloLactec,
                report.Identificacao,
                report.DataEmissao));
    }

    public static IQueryable<string> QuoteCodesOfClients(
        AutoLabDbContext database,
        List<Guid?> clientIds,
        int rowLimit)
    {
        return database.SolicitacoesServico
            .Where(request => request.GCRecord == null
                && clientIds.Contains(request.Cliente)
                && request.OrcamentoCRM != null)
            .Select(request => request.OrcamentoCRM!)
            .Distinct()
            .OrderBy(code => code)
            .Take(rowLimit);
    }

    public static IQueryable<string> EquipmentKeys(
        AutoLabDbContext database,
        List<string> authorizedProtocols,
        EquipmentSearch? search)
    {
        IQueryable<MarkedTestRow> rows = search is null
            ? TestRowsOf(database, authorizedProtocols).Select(row => new MarkedTestRow { Row = row, Matches = 1 })
            : RowsMarkedBySearch(database, TestRowsOf(database, authorizedProtocols), LikePattern.Containing(search.Term));

        IQueryable<IGrouping<string, MarkedTestRow>> equipment = rows
            .GroupBy(marked => string.IsNullOrWhiteSpace(marked.Row.NumSerie)
                ? EF.Functions.Collate(marked.Row.ProtocoloLactec!.Trim(), OrdinalCollation)
                : EF.Functions.Collate(marked.Row.NumSerie!.Trim(), OrdinalCollation))
            .Where(found => found.Key != null);

        if (search is not null)
            equipment = equipment.Where(found => found.Max(marked => marked.Matches) == 1);

        return equipment
            .Select(found => new
            {
                Key = found.Key!,
                Latest = found.Max(marked => marked.Row.DataFinalAnalise)
            })
            .OrderByDescending(found => found.Latest)
            .ThenBy(found => found.Key)
            .Select(found => found.Key);
    }

    public static IQueryable<VwDadosEnsaiosRecord> RowsOfEquipmentKeys(
        AutoLabDbContext database,
        List<string> authorizedProtocols,
        List<string> equipmentKeys,
        int rowLimit)
    {
        return TestRowsOf(database, authorizedProtocols)
            .Where(row => ((equipmentKeys.Contains(row.NumSerie!) || row.NumSerie!.StartsWith(" "))
                    && equipmentKeys.Contains(EF.Functions.Collate(row.NumSerie!.Trim(), OrdinalCollation)))
                || ((equipmentKeys.Contains(row.ProtocoloLactec!) || row.ProtocoloLactec!.StartsWith(" "))
                    && equipmentKeys.Contains(EF.Functions.Collate(row.ProtocoloLactec!.Trim(), OrdinalCollation))))
            .OrderBy(row => row.ProtocoloLactec)
            .Take(rowLimit);
    }

    public static IQueryable<string> ProtocolsOfEquipment(
        AutoLabDbContext database,
        List<string> authorizedProtocols,
        string equipmentKey)
    {
        return TestRowsOf(database, authorizedProtocols)
            .Where(row => (((row.NumSerie == equipmentKey || row.NumSerie!.StartsWith(" "))
                        && EF.Functions.Collate(row.NumSerie!.Trim(), OrdinalCollation) == equipmentKey)
                    || ((row.ProtocoloLactec == equipmentKey || row.ProtocoloLactec!.StartsWith(" "))
                        && EF.Functions.Collate(row.ProtocoloLactec!.Trim(), OrdinalCollation) == equipmentKey))
                && row.ProtocoloLactec != null)
            .GroupBy(row => EF.Functions.Collate(row.ProtocoloLactec!.Trim(), OrdinalCollation))
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
        List<string> authorizedProtocols,
        List<string> protocols,
        int rowLimit)
    {
        return TestRowsOf(database, authorizedProtocols)
            .Where(row => (protocols.Contains(row.ProtocoloLactec!) || row.ProtocoloLactec!.StartsWith(" "))
                && protocols.Contains(EF.Functions.Collate(row.ProtocoloLactec!.Trim(), OrdinalCollation)))
            .OrderBy(row => row.ProtocoloLactec)
            .Take(rowLimit);
    }

    public static IQueryable<VwDadosEnsaiosRecord> RowsOfProtocol(
        AutoLabDbContext database,
        List<string> authorizedProtocols,
        string protocol,
        int rowLimit)
    {
        return TestRowsOf(database, authorizedProtocols)
            .Where(row => (row.ProtocoloLactec == protocol || row.ProtocoloLactec!.StartsWith(" "))
                && EF.Functions.Collate(row.ProtocoloLactec!.Trim(), OrdinalCollation) == protocol)
            .OrderBy(row => row.Name)
            .Take(rowLimit);
    }

    public static IQueryable<EquipamentosRecord> RegistryRowsOf(
        AutoLabDbContext database,
        List<string> serialNumbers,
        int rowLimit)
    {
        return database.Equipamentos
            .Where(row => (serialNumbers.Contains(row.NumSerie!) || row.NumSerie!.StartsWith(" "))
                && serialNumbers.Contains(EF.Functions.Collate(row.NumSerie!.Trim(), OrdinalCollation)))
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

    public static IQueryable<ReportViewRow> ReportViewRowOf(
        AutoLabDbContext database,
        Guid reportId)
    {
        return database.RelatoriosEnsaios
            .Where(report => report.Oid == reportId)
            .Select(report => new ReportViewRow(
                report.IDAmostra,
                report.Nome,
                report.ClienteSolicitante,
                report.DataFinalProgramadaCliente,
                report.SituacaoRelatorio))
            .Take(1);
    }

    public static IQueryable<Guid> LiveFilesAmong(
        AutoLabDbContext database,
        List<Guid> fileIds,
        int rowLimit)
    {
        return database.FileData
            .Where(file => file.GCRecord == null && fileIds.Contains(file.Oid))
            .Select(file => file.Oid)
            .OrderBy(fileId => fileId)
            .Take(rowLimit);
    }

    public static IQueryable<SignedDocumentRow> LiveFileOf(
        AutoLabDbContext database,
        Guid fileId)
    {
        return database.FileData
            .Where(file => file.GCRecord == null && file.Oid == fileId)
            .Select(file => new SignedDocumentRow(file.FileName, file.Content))
            .Take(1);
    }

    private static IQueryable<VwDadosEnsaiosRecord> TestRowsOf(
        AutoLabDbContext database,
        List<string> authorizedProtocols)
    {
        ArgumentNullException.ThrowIfNull(authorizedProtocols);

        return database.DadosEnsaios.Where(row => authorizedProtocols.Contains(row.ProtocoloLactec!));
    }

    private static IQueryable<MarkedTestRow> RowsMarkedBySearch(
        AutoLabDbContext database,
        IQueryable<VwDadosEnsaiosRecord> rows,
        string pattern)
    {
        IQueryable<string> serialNumbersWithMatchingTag = database.Equipamentos
            .Where(registered => !string.IsNullOrWhiteSpace(registered.NumSerie)
                && EF.Functions.Like(
                    EF.Functions.Collate(registered.Tag!, CaseInsensitiveCollation),
                    pattern,
                    LikePattern.EscapeCharacter))
            .Select(registered => EF.Functions.Collate(registered.NumSerie!.Trim(), OrdinalCollation))
            .Distinct();

        return rows
            .LeftJoin(
                serialNumbersWithMatchingTag,
                row => EF.Functions.Collate(row.NumSerie!.Trim(), OrdinalCollation),
                serialNumber => serialNumber,
                (row, serialNumber) => new { Row = row, SerialNumberWithMatchingTag = serialNumber })
            .Select(found => new MarkedTestRow
            {
                Row = found.Row,
                Matches = found.SerialNumberWithMatchingTag != null
                    || EF.Functions.Like(
                        EF.Functions.Collate(found.Row.NumSerie!, CaseInsensitiveCollation),
                        pattern,
                        LikePattern.EscapeCharacter)
                    || EF.Functions.Like(
                        EF.Functions.Collate(found.Row.Identificacao!, CaseInsensitiveCollation),
                        pattern,
                        LikePattern.EscapeCharacter)
                        ? 1
                        : 0
            });
    }

    private sealed class MarkedTestRow
    {
        public required VwDadosEnsaiosRecord Row { get; init; }

        public required int Matches { get; init; }
    }
}
