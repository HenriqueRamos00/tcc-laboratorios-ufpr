using System.Text;

namespace Portal.Api.LabResults;

/// <summary>
/// Nem a chave do equipamento nem o protocolo Lactec sobrevivem como segmento de rota:
/// existe "111.594/1" e "01417/2026-001" na amostra real, e a barra divide o caminho. A rota
/// carrega um identificador opaco em base 64 segura para URL, e a traducao mora na borda
/// porque e problema de endereco, nao de negocio.
/// </summary>
public static class OpaqueIdentifier
{
    public static string Encode(string originalValue)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(originalValue);

        return Convert.ToBase64String(Encoding.UTF8.GetBytes(originalValue))
            .TrimEnd('=')
            .Replace('+', '-')
            .Replace('/', '_');
    }

    // A leitura precisa recusar byte que nao forma UTF-8. O `Encoding.UTF8` padrao
    // troca byte invalido por um caractere de substituicao e devolve texto, e ai um
    // endereco de lixo passaria por chave de equipamento: a resposta viraria "nao
    // encontrei esse equipamento" em vez de "esse endereco e ilegivel".
    private static readonly UTF8Encoding StrictUtf8 = new(
        encoderShouldEmitUTF8Identifier: false,
        throwOnInvalidBytes: true);

    public static bool TryDecode(string? identifier, out string originalValue)
    {
        originalValue = string.Empty;

        if (string.IsNullOrWhiteSpace(identifier)) return false;

        string withPadding = identifier.Replace('-', '+').Replace('_', '/');
        withPadding = withPadding.PadRight(withPadding.Length + ((4 - (withPadding.Length % 4)) % 4), '=');

        Span<byte> destination = new byte[withPadding.Length];

        if (!Convert.TryFromBase64String(withPadding, destination, out int written)) return false;

        string decoded;

        try
        {
            decoded = StrictUtf8.GetString(destination[..written]);
        }
        catch (DecoderFallbackException)
        {
            return false;
        }

        if (string.IsNullOrWhiteSpace(decoded)) return false;

        originalValue = decoded;
        return true;
    }
}
