using System.Data.Common;
using System.Text.Json;

using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Diagnostics;

using Portal.Adapters.AutoLab;

namespace Portal.Tests.AutoLab;

internal sealed class AutoLabTestDatabase : IDisposable
{
    private const string Schema = """
        ATTACH DATABASE ':memory:' AS dbo;

        CREATE TABLE dbo.vwDadosEnsaios (
            Identificacao TEXT COLLATE NOCASE NULL,
            ProtocoloLactec TEXT COLLATE NOCASE NULL,
            NumSerie TEXT COLLATE NOCASE NULL,
            Name TEXT NULL,
            Resultado TEXT NULL,
            EmpresaTrafo TEXT COLLATE NOCASE NULL,
            DataEmissao TEXT NULL,
            DataFinalAnalise TEXT NULL);

        CREATE TABLE dbo.Equipamentos (
            NumSerie TEXT COLLATE NOCASE NULL,
            Tag TEXT NULL,
            AnoFabricacao TEXT NULL,
            Potencia TEXT NULL,
            Tensao TEXT NULL,
            Fabricante TEXT NULL,
            VolumeOleo TEXT NULL,
            UnidadePotencia TEXT NULL,
            TipoDeOleo TEXT NULL,
            TipoEquipamento TEXT NULL);

        CREATE TABLE dbo.SituaProcess (
            OrcamentoCRM TEXT COLLATE NOCASE NULL,
            Recebido INTEGER NULL,
            Protocolado INTEGER NULL,
            SituacaoEnsaios INTEGER NULL,
            SolicitacaoRelatorio INTEGER NOT NULL);

        CREATE TABLE dbo.RelatorioEmissao (
            Oid TEXT NOT NULL PRIMARY KEY,
            Identificacao TEXT COLLATE NOCASE NULL,
            PdfAssinado TEXT NULL,
            PdfLiberado BLOB NULL,
            GCRecord INTEGER NULL,
            Revisao INTEGER NULL,
            RelatorioEnsaioSubstituto TEXT NULL,
            DataEmissao TEXT NULL,
            DataPublicacao TEXT NULL,
            TituloRelatorio TEXT NULL,
            ObjetivoRelatorio TEXT NULL,
            Conclusao TEXT NULL,
            Observacoes TEXT NULL,
            Solicitacao TEXT NULL,
            OptimisticLockField INTEGER NULL);

        CREATE TABLE dbo.vwRelatoriosEnsaios (
            NumeroSolicitacao TEXT NULL,
            Oid TEXT NOT NULL,
            OrcamentoCRM TEXT COLLATE NOCASE NULL,
            IDAmostra TEXT NULL,
            Nome TEXT NULL,
            PdfAssinado TEXT NULL,
            ClienteSolicitante TEXT COLLATE NOCASE NULL,
            Identificacao TEXT COLLATE NOCASE NULL,
            DataFinalProgramadaCliente TEXT NULL,
            SituacaoRelatorio TEXT NULL,
            DataEmissao TEXT NULL,
            DataPublicacao TEXT NULL,
            DiasIntervalo INTEGER NULL);

        CREATE TABLE dbo.dCliente (
            Oid TEXT NOT NULL,
            Sigla TEXT NULL,
            Nome TEXT NULL,
            RazaoSocial TEXT NULL,
            Cnpj_Cpf TEXT NULL);

        CREATE TABLE dbo.FileData (
            Oid TEXT NOT NULL PRIMARY KEY,
            size INTEGER NULL,
            FileName TEXT NULL,
            Content BLOB NULL,
            OptimisticLockField INTEGER NULL,
            GCRecord INTEGER NULL);
        """;

    private static readonly string[] BinaryColumns = ["Content", "PdfLiberado"];

    private readonly SqliteConnection _connection;

    public AutoLabTestDatabase()
    {
        _connection = new SqliteConnection("Data Source=:memory:");
        _connection.Open();

        using SqliteCommand command = _connection.CreateCommand();
        command.CommandText = Schema;
        command.ExecuteNonQuery();
    }

    public List<string> Commands { get; } = [];

    public static AutoLabTestDatabase WithSample()
    {
        AutoLabTestDatabase database = new();
        database.LoadSample();
        return database;
    }

    public void LoadSample()
    {
        using JsonDocument sample = JsonDocument.Parse(File.ReadAllText(
            Path.Combine(AppContext.BaseDirectory, "AutoLab", "autolab-sample.json")));

        foreach (JsonProperty table in sample.RootElement.EnumerateObject())
        {
            foreach (JsonElement row in table.Value.EnumerateArray())
            {
                Insert(table.Name, [.. row.EnumerateObject()
                    .Select(column => (column.Name, ValueOf(column)))]);
            }
        }
    }

    public AutoLabDbContext NewContext()
    {
        DbContextOptionsBuilder<AutoLabDbContext> options = new();
        Configure(options);

        return new AutoLabDbContext(options.Options);
    }

    public void Configure(DbContextOptionsBuilder options)
    {
        options
            .UseSqlite(_connection)
            .UseQueryTrackingBehavior(QueryTrackingBehavior.NoTracking)
            .AddInterceptors(new CommandRecorder(Commands));
    }

    public void AddTestRow(
        string protocol,
        string? serialNumber,
        string company,
        DateTime analysisEnd,
        string identification = "LACTEC-00001/2026",
        string name = "Hidrogênio",
        string? result = "60")
    {
        Insert(
            "dbo.vwDadosEnsaios",
            ("Identificacao", identification),
            ("ProtocoloLactec", protocol),
            ("NumSerie", serialNumber),
            ("Name", name),
            ("Resultado", result),
            ("EmpresaTrafo", company),
            ("DataEmissao", analysisEnd.AddDays(1)),
            ("DataFinalAnalise", analysisEnd));
    }

    public void AddEquipment(string? serialNumber, string? tag, string? voltage, string? manufacturer = "FABRICANTE")
    {
        Insert(
            "dbo.Equipamentos",
            ("NumSerie", serialNumber),
            ("Tag", tag),
            ("AnoFabricacao", "2001"),
            ("Potencia", "50"),
            ("Tensao", voltage),
            ("Fabricante", manufacturer),
            ("VolumeOleo", "1000"),
            ("UnidadePotencia", "MVA"),
            ("TipoDeOleo", "Óleo Mineral Isolante"),
            ("TipoEquipamento", "TRANSFORMADOR"));
    }

    public void AddSituaProcess(string? quoteCode, bool? received, bool? registered, int? tests, int report)
    {
        Insert(
            "dbo.SituaProcess",
            ("OrcamentoCRM", quoteCode),
            ("Recebido", received),
            ("Protocolado", registered),
            ("SituacaoEnsaios", tests),
            ("SolicitacaoRelatorio", report));
    }

    public Guid AddReportIssue(
        string identification,
        Guid? signedPdf,
        int? revision = 0,
        int? deleted = null,
        Guid? replacedBy = null,
        DateTime? published = null)
    {
        Guid oid = Guid.NewGuid();

        Insert(
            "dbo.RelatorioEmissao",
            ("Oid", Text(oid)),
            ("Identificacao", identification),
            ("PdfAssinado", signedPdf is null ? null : Text(signedPdf.Value)),
            ("PdfLiberado", new byte[] { 0x99, 0x99 }),
            ("GCRecord", deleted),
            ("Revisao", revision),
            ("RelatorioEnsaioSubstituto", replacedBy is null ? null : Text(replacedBy.Value)),
            ("DataEmissao", published),
            ("DataPublicacao", published));

        return oid;
    }

    public Guid AddFile(string? fileName, byte[]? content, int? deleted = null)
    {
        Guid oid = Guid.NewGuid();

        Insert(
            "dbo.FileData",
            ("Oid", Text(oid)),
            ("size", content?.Length),
            ("FileName", fileName),
            ("Content", content),
            ("OptimisticLockField", 0),
            ("GCRecord", deleted));

        return oid;
    }

    public void Dispose()
    {
        _connection.Dispose();
    }

    private static object? ValueOf(JsonProperty column)
    {
        JsonElement value = column.Value;

        return value.ValueKind switch
        {
            JsonValueKind.Null => null,
            JsonValueKind.True => true,
            JsonValueKind.False => false,
            JsonValueKind.Number => value.GetInt64(),
            JsonValueKind.String when BinaryColumns.Contains(column.Name) => Convert.FromBase64String(value.GetString()!),
            _ => value.GetString()
        };
    }

    private static string Text(Guid value)
    {
        return value.ToString().ToUpperInvariant();
    }

    private void Insert(string table, params (string Column, object? Value)[] values)
    {
        using SqliteCommand command = _connection.CreateCommand();

        command.CommandText =
            $"INSERT INTO {table} ({string.Join(", ", values.Select(value => value.Column))}) "
            + $"VALUES ({string.Join(", ", values.Select((_, index) => "$p" + index))})";

        for (int index = 0; index < values.Length; index++)
            command.Parameters.AddWithValue("$p" + index, values[index].Value ?? DBNull.Value);

        command.ExecuteNonQuery();
    }

    private sealed class CommandRecorder : DbCommandInterceptor
    {
        private readonly List<string> _commands;

        public CommandRecorder(List<string> commands)
        {
            _commands = commands;
        }

        public override InterceptionResult<DbDataReader> ReaderExecuting(
            DbCommand command,
            CommandEventData eventData,
            InterceptionResult<DbDataReader> result)
        {
            _commands.Add(command.CommandText);
            return result;
        }

        public override ValueTask<InterceptionResult<DbDataReader>> ReaderExecutingAsync(
            DbCommand command,
            CommandEventData eventData,
            InterceptionResult<DbDataReader> result,
            CancellationToken cancellationToken = default)
        {
            _commands.Add(command.CommandText);
            return ValueTask.FromResult(result);
        }
    }
}
