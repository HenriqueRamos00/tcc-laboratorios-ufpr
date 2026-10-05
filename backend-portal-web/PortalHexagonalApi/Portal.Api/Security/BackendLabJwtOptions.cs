namespace Portal.Api.Security;

public sealed class BackendLabJwtOptions
{
    public const string SectionName = "BackendLabJwt";

    public string Secret { get; set; } = string.Empty;
    // Identifica quem emite o token interno.
    public string Issuer { get; set; } = string.Empty;
    // Identifica o destino previsto do token interno.
    public string Audience { get; set; } = string.Empty;
    // Tolerância entre relógios dos serviços, em segundos.
    public int ClockSkewSeconds { get; set; } = 30;

    public bool IsValid(out string error)
    {
        if (System.Text.Encoding.UTF8.GetByteCount(Secret) < 32)
        {
            error = "BackendLabJwt:Secret deve possuir pelo menos 32 bytes UTF-8.";
            return false;
        }

        if (string.IsNullOrWhiteSpace(Issuer) || string.IsNullOrWhiteSpace(Audience))
        {
            error = "BackendLabJwt:Issuer e BackendLabJwt:Audience são obrigatórios.";
            return false;
        }

        if (ClockSkewSeconds is < 0 or > 60)
        {
            error = "BackendLabJwt:ClockSkewSeconds deve estar entre 0 e 60.";
            return false;
        }

        error = string.Empty;
        return true;
    }
}
