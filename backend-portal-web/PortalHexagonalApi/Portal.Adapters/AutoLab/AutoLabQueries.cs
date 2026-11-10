using Microsoft.EntityFrameworkCore;

using Portal.Application.Visibility;

namespace Portal.Adapters.AutoLab;

public static class AutoLabQueries
{
    /// <summary>
    /// A collation que o portal exige nas comparacoes que decidem identidade de equipamento,
    /// de protocolo, de serie e de relatorio. O banco do AutoLAB nasce em
    /// SQL_Latin1_General_CP1_CI_AS, que nao distingue caixa, e a regra do portal e ordinal.
    /// Sem COLLATE explicito o GROUP BY do servidor funde duas series que diferem so na caixa,
    /// e o filtro ordinal que vem depois, em C#, descarta as linhas da caixa que nao casou.
    /// Medido com ENSAIO-TESTE e ensaio-teste na mesma empresa: tres linhas gravadas, duas
    /// chaves devolvidas, um equipamento sumido da listagem.
    ///
    /// A chamada fica escrita linha por linha, e nao atras de um metodo auxiliar, porque
    /// EF Core traduz a arvore de expressao e nao entra em metodo nosso: um atalho como
    /// Ordinal(row.NumSerie) derruba a consulta com
    /// "Translation of method ... failed", medido.
    ///
    /// Branco nas pontas tambem decide identidade, e por isso a coluna entra aparada em toda
    /// comparacao que decide chave de equipamento ou de protocolo. A aplicacao compara aparado:
    /// <c>SampleAnalysis</c> apara serie e protocolo no construtor, e e dali que sai a chave
    /// que a rota carrega. Sem aparar aqui, a listagem agrupa por <c>" 571289"</c>, o
    /// adaptador apara para <c>"571289"</c> ao conferir a pagina, a tela recebe
    /// <c>"571289"</c> e a consulta do detalhe procura <c>NumSerie = '571289'</c>, que nenhuma
    /// linha atende: o equipamento aparece na lista e responde 404 ao ser aberto. Medido com
    /// <c>" 571289"</c>, <c>"571289 "</c> e <c>" 571289 "</c>.
    ///
    /// Branco a esquerda conta nas duas pontas, no SQL Server e no SQLite. Branco a direita o
    /// SQL Server ignora na igualdade, por completar com branco a menor das duas cadeias, e o
    /// SQLite nao ignora: aparar nas duas pontas vale nos dois.
    /// </summary>
    public const string OrdinalCollation = "Latin1_General_BIN2";

    public static IQueryable<string> EquipmentKeys(
        AutoLabDbContext database,
        VisibilityScope scope)
    {
        return TestRowsIn(database, scope)
            .GroupBy(row => string.IsNullOrWhiteSpace(row.NumSerie)
                ? EF.Functions.Collate(row.ProtocoloLactec!.Trim(), OrdinalCollation)
                : EF.Functions.Collate(row.NumSerie!.Trim(), OrdinalCollation))
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
            .Where(row => equipmentKeys.Contains(EF.Functions.Collate(row.NumSerie!.Trim(), OrdinalCollation))
                || equipmentKeys.Contains(EF.Functions.Collate(row.ProtocoloLactec!.Trim(), OrdinalCollation)))
            .OrderBy(row => row.ProtocoloLactec)
            .Take(rowLimit);
    }

    public static IQueryable<string> ProtocolsOfEquipment(
        AutoLabDbContext database,
        VisibilityScope scope,
        string equipmentKey)
    {
        return TestRowsIn(database, scope)
            .Where(row => (EF.Functions.Collate(row.NumSerie!.Trim(), OrdinalCollation) == equipmentKey
                    || EF.Functions.Collate(row.ProtocoloLactec!.Trim(), OrdinalCollation) == equipmentKey)
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
        VisibilityScope scope,
        List<string> protocols,
        int rowLimit)
    {
        return TestRowsIn(database, scope)
            .Where(row => protocols.Contains(EF.Functions.Collate(row.ProtocoloLactec!.Trim(), OrdinalCollation)))
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
            .Where(row => EF.Functions.Collate(row.ProtocoloLactec!.Trim(), OrdinalCollation) == protocol)
            .OrderBy(row => row.Name)
            .Take(rowLimit);
    }

    public static IQueryable<EquipamentosRecord> RegistryRowsOf(
        AutoLabDbContext database,
        List<string> serialNumbers,
        int rowLimit)
    {
        return database.Equipamentos
            .Where(row => serialNumbers.Contains(EF.Functions.Collate(row.NumSerie!.Trim(), OrdinalCollation)))
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
