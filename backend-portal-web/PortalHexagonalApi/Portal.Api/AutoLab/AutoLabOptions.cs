using Microsoft.Data.SqlClient;

using Portal.Adapters.AutoLab;

namespace Portal.Api.AutoLab;

public sealed class AutoLabOptions
{
    public const string SectionName = "AutoLab";

    public string? Server { get; set; }

    public string? Database { get; set; }

    public string? User { get; set; }

    public string? Password { get; set; }

    public bool Encrypt { get; set; }

    public bool TrustServerCertificate { get; set; } = true;

    public bool ReadOnlyIntent { get; set; } = true;

    public string ApplicationName { get; set; } = "portal-api";

    public int CommandTimeoutSeconds { get; set; } = 10;

    public int ConnectTimeoutSeconds { get; set; } = 10;

    public int MaxPoolSize { get; set; } = 10;

    public int MaxRowsPerQuery { get; set; } = AutoLabQueryLimits.DefaultMaxRowsPerQuery;

    public int MaxRetryCount { get; set; } = 3;

    public int MaxRetryDelaySeconds { get; set; } = 2;

    public IEnumerable<string> Problems()
    {
        if (string.IsNullOrWhiteSpace(Server)) yield return $"{SectionName}:Server";
        if (string.IsNullOrWhiteSpace(Database)) yield return $"{SectionName}:Database";
        if (string.IsNullOrWhiteSpace(User)) yield return $"{SectionName}:User";
        if (string.IsNullOrEmpty(Password)) yield return $"{SectionName}:Password";
        if (CommandTimeoutSeconds is < 1 or > 300) yield return $"{SectionName}:CommandTimeoutSeconds (1 a 300)";
        if (ConnectTimeoutSeconds is < 1 or > 120) yield return $"{SectionName}:ConnectTimeoutSeconds (1 a 120)";
        if (MaxPoolSize is < 1 or > 100) yield return $"{SectionName}:MaxPoolSize (1 a 100)";
        if (MaxRowsPerQuery is < 1 or > 100_000) yield return $"{SectionName}:MaxRowsPerQuery (1 a 100000)";
        if (MaxRetryCount is < 0 or > 10) yield return $"{SectionName}:MaxRetryCount (0 a 10)";
        if (MaxRetryDelaySeconds is < 1 or > 60) yield return $"{SectionName}:MaxRetryDelaySeconds (1 a 60)";
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
            ApplicationIntent = ReadOnlyIntent ? ApplicationIntent.ReadOnly : ApplicationIntent.ReadWrite,
            ApplicationName = ApplicationName,
            CommandTimeout = CommandTimeoutSeconds,
            ConnectTimeout = ConnectTimeoutSeconds,
            Pooling = true,
            MaxPoolSize = MaxPoolSize,
            PersistSecurityInfo = false,
            MultipleActiveResultSets = false
        };

        return builder.ConnectionString;
    }
}
