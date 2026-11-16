using Microsoft.Data.SqlClient;

namespace Portal.Api.PortalStore;

public sealed class PortalStoreOptions
{
    public const string SectionName = "PortalStore";
    public const string NotConnectedSource = "not-connected";
    public const string SqlServerSource = "sql-server";

    public string Source { get; set; } = NotConnectedSource;

    public string? Server { get; set; }

    public string? Database { get; set; }

    public string? User { get; set; }

    public string? Password { get; set; }

    public bool Encrypt { get; set; }

    public bool TrustServerCertificate { get; set; } = true;

    public int CommandTimeoutSeconds { get; set; } = 15;

    public int ConnectTimeoutSeconds { get; set; } = 10;

    public int MigrationAttempts { get; set; } = 10;

    public IEnumerable<string> Problems()
    {
        if (string.IsNullOrWhiteSpace(Server)) yield return $"{SectionName}:Server";
        if (string.IsNullOrWhiteSpace(Database)) yield return $"{SectionName}:Database";
        if (string.IsNullOrWhiteSpace(User)) yield return $"{SectionName}:User";
        if (string.IsNullOrEmpty(Password)) yield return $"{SectionName}:Password";
        if (CommandTimeoutSeconds is < 1 or > 300) yield return $"{SectionName}:CommandTimeoutSeconds (1 a 300)";
        if (ConnectTimeoutSeconds is < 1 or > 120) yield return $"{SectionName}:ConnectTimeoutSeconds (1 a 120)";
        if (MigrationAttempts is < 1 or > 60) yield return $"{SectionName}:MigrationAttempts (1 a 60)";
    }

    public string BuildConnectionString()
    {
        SqlConnectionStringBuilder builder = new()
        {
            DataSource = Server,
            InitialCatalog = Database,
            UserID = User,
            Password = Password,
            Encrypt = Encrypt,
            TrustServerCertificate = TrustServerCertificate,
            ApplicationName = "portal-api",
            CommandTimeout = CommandTimeoutSeconds,
            ConnectTimeout = ConnectTimeoutSeconds,
            Pooling = true,
            PersistSecurityInfo = false,
            MultipleActiveResultSets = false
        };

        return builder.ConnectionString;
    }
}
