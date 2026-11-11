using Portal.Adapters.AutoLab;
using Portal.Adapters.LabResults;
using Portal.Adapters.ReportDocuments;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;

using Portal.Application.Visibility;

using Xunit;

namespace Portal.Tests.AutoLab;

public sealed class AutoLabSignedReportDocumentTests : IDisposable
{
    private const string Tenant = "EMPRESA DE ENERGIA";
    private const string OtherTenant = "OUTRA EMPRESA S.A.";
    private const string Identification = "LACTEC-02272/2026";

    private static readonly byte[] CurrentPdf = [0x25, 0x50, 0x44, 0x46, 0x01];
    private static readonly byte[] OldPdf = [0x25, 0x50, 0x44, 0x46, 0x02];

    private readonly AutoLabTestDatabase _database = new();

    public void Dispose()
    {
        _database.Dispose();
    }

    [Fact]
    public async Task TheCurrentVersionIsChosenAndOnlyItsFileIsRead()
    {
        Guid oldFile = _database.AddFile("laudo-r0.pdf", OldPdf);
        Guid currentFile = _database.AddFile("laudo-r1.pdf", CurrentPdf);
        Guid replacement = Guid.NewGuid();

        _database.AddReportIssue(Identification, oldFile, revision: 0, published: new DateTime(2026, 1, 1));
        _database.AddReportIssue(Identification, oldFile, revision: 5, replacedBy: replacement, published: new DateTime(2026, 3, 1));
        _database.AddReportIssue(Identification, currentFile, revision: 1, published: new DateTime(2026, 2, 1));

        SignedReportDocument? document = await Repository()
            .GetSignedDocumentOfAsync(Identification, CancellationToken.None);

        Assert.NotNull(document);
        Assert.Equal("laudo-r1.pdf", document.FileName);
        Assert.Equal(CurrentPdf, document.Content);

        string sql = Assert.Single(_database.Commands);
        Assert.DoesNotContain("PdfLiberado", sql, StringComparison.Ordinal);
        Assert.Contains("LIMIT 1", sql, StringComparison.Ordinal);
    }

    [Fact]
    public async Task AFileCompressedByTheAutoLabIsDeliveredAsThePlainPdf()
    {
        byte[] pdf = [.. "%PDF-1.4\n%%EOF"u8];
        Guid file = _database.AddFile("laudo.pdf", CompressedAsAutoLab(pdf));
        _database.AddReportIssue(Identification, file, revision: 0);

        SignedReportDocument? document = await Repository()
            .GetSignedDocumentOfAsync(Identification, CancellationToken.None);

        Assert.Equal(pdf, document?.Content);
    }

    [Fact]
    public async Task ACorruptCompressedFileIsReportedAsUnavailable()
    {
        byte[] corrupt = CompressedAsAutoLab([.. "%PDF-1.4"u8]);
        Array.Fill(corrupt, (byte)0xFF, 27, corrupt.Length - 27);
        Guid file = _database.AddFile("laudo.pdf", corrupt);
        _database.AddReportIssue(Identification, file, revision: 0);

        await Assert.ThrowsAsync<ReportDocumentSourceUnavailableException>(() => Repository()
            .GetSignedDocumentOfAsync(Identification, CancellationToken.None));
    }

    [Fact]
    public void AFileWithoutTheAutoLabSignatureIsKeptAsItIs()
    {
        byte[] gzipWithoutSignature = Gzip(CurrentPdf);

        Assert.Same(CurrentPdf, FileDataContent.Decode(CurrentPdf));
        Assert.Same(gzipWithoutSignature, FileDataContent.Decode(gzipWithoutSignature));
    }

    [Fact]
    public void AFileThatExpandsBeyondTheLimitIsRefused()
    {
        byte[] bomb = CompressedAsAutoLab(new byte[FileDataContent.MaxDecodedBytes + 1]);

        Assert.Throws<InvalidDataException>(() => FileDataContent.Decode(bomb));
    }

    [Fact]
    public async Task ALogicallyDeletedReportIsIgnored()
    {
        Guid deletedFile = _database.AddFile("apagado.pdf", OldPdf);
        Guid liveFile = _database.AddFile("vigente.pdf", CurrentPdf);

        _database.AddReportIssue(Identification, deletedFile, revision: 9, deleted: 12345);
        _database.AddReportIssue(Identification, liveFile, revision: 1);

        SignedReportDocument? document = await Repository()
            .GetSignedDocumentOfAsync(Identification, CancellationToken.None);

        Assert.Equal("vigente.pdf", document?.FileName);
    }

    [Fact]
    public async Task ADeletedFileMeansNoDocument()
    {
        Guid deletedFile = _database.AddFile("apagado.pdf", CurrentPdf, deleted: 7);
        _database.AddReportIssue(Identification, deletedFile);

        Assert.Null(await Repository().GetSignedDocumentOfAsync(Identification, CancellationToken.None));
    }

    [Fact]
    public async Task AReportWithoutSignedPdfMeansNoDocument()
    {
        _database.AddReportIssue(Identification, signedPdf: null);

        Assert.Null(await Repository().GetSignedDocumentOfAsync(Identification, CancellationToken.None));
    }

    [Fact]
    public async Task TheDownloadReturnsTheSignedPdfOfTheTenantsReport()
    {
        SeedReportOf(Tenant, "01417/2026-001", "571289");

        SignedReportDocument? document = await Download()
            .GetSignedReportAsync(VisibilityScope.OfCompany(Tenant), "571289", "01417/2026-001", CancellationToken.None);

        Assert.Equal(CurrentPdf, document?.Content);
    }

    [Fact]
    public async Task TheReportOfAnotherTenantIsNotDownloaded()
    {
        SeedReportOf(OtherTenant, "01417/2026-001", "571289");

        Assert.Null(await Download()
            .GetSignedReportAsync(VisibilityScope.OfCompany(Tenant), "571289", "01417/2026-001", CancellationToken.None));
    }

    [Fact]
    public async Task AReportOfAnotherEquipmentOfTheSameTenantIsNotDownloadedThroughThisOne()
    {
        SeedReportOf(Tenant, "01417/2026-001", "571289");

        Assert.Null(await Download()
            .GetSignedReportAsync(VisibilityScope.OfCompany(Tenant), "111.594/1", "01417/2026-001", CancellationToken.None));
    }

    [Fact]
    public async Task TheDocumentSourceIsNotAskedWhenTheReportIsNotTheTenants()
    {
        SeedReportOf(OtherTenant, "01417/2026-001", "571289");

        await Download().GetSignedReportAsync(VisibilityScope.OfCompany(Tenant), "571289", "01417/2026-001", CancellationToken.None);

        Assert.DoesNotContain(_database.Commands, sql => sql.Contains("FileData", StringComparison.Ordinal));
    }

    private void SeedReportOf(string company, string protocol, string serialNumber)
    {
        _database.AddTestRow(protocol, serialNumber, company, new DateTime(2026, 6, 23), identification: Identification);
        Guid file = _database.AddFile("laudo.pdf", CurrentPdf);
        _database.AddReportIssue(Identification, file, revision: 0);
    }

    private SignedReportDownload Download()
    {
        AutoLabDbContext context = _database.NewContext();

        return new SignedReportDownload(
            new AutoLabLabTestRepository(context, new AutoLabQueryLimits()),
            new AutoLabReportIssueRepository(context, new AutoLabQueryLimits()));
    }

    private static byte[] CompressedAsAutoLab(byte[] content)
    {
        return [.. Convert.FromHexString("128B08DA41663B41BBFC2829752DCF962B"), .. Gzip(content)];
    }

    private static byte[] Gzip(byte[] content)
    {
        using MemoryStream output = new();

        using (System.IO.Compression.GZipStream gzip = new(output, System.IO.Compression.CompressionLevel.Optimal))
            gzip.Write(content);

        return output.ToArray();
    }

    private AutoLabReportIssueRepository Repository()
    {
        return new AutoLabReportIssueRepository(_database.NewContext(), new AutoLabQueryLimits());
    }
}
