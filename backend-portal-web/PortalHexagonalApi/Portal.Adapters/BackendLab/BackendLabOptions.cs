namespace Portal.Adapters.BackendLab;

public sealed class BackendLabOptions
{
    public const string SectionName = "BackendLab";

    public string BaseUrl { get; set; } = "http://localhost:8080";
    public int TimeoutSeconds { get; set; } = 10;

    public bool IsValid(bool requireHttps, out string error)
    {
        if (!Uri.TryCreate(BaseUrl, UriKind.Absolute, out Uri? uri) ||
            (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps) ||
            !string.IsNullOrEmpty(uri.UserInfo) || uri.AbsolutePath != "/" ||
            !string.IsNullOrEmpty(uri.Query) || !string.IsNullOrEmpty(uri.Fragment) ||
            (requireHttps && uri.Scheme != Uri.UriSchemeHttps))
        {
            error = requireHttps
                ? "BackendLab:BaseUrl deve ser uma URL HTTPS absoluta sem credenciais, caminho, query ou fragmento em produção."
                : "BackendLab:BaseUrl deve ser uma URL HTTP(S) absoluta sem credenciais, caminho, query ou fragmento.";
            return false;
        }

        if (TimeoutSeconds is < 1 or > 60)
        {
            error = "BackendLab:TimeoutSeconds deve estar entre 1 e 60.";
            return false;
        }

        error = string.Empty;
        return true;
    }
}
