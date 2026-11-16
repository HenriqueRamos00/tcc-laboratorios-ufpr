namespace Portal.Adapters.AutoLab;

public static class LikePattern
{
    public const string EscapeCharacter = "\\";

    public static string Containing(string literalText)
    {
        ArgumentNullException.ThrowIfNull(literalText);

        string escaped = literalText
            .Replace(EscapeCharacter, EscapeCharacter + EscapeCharacter, StringComparison.Ordinal)
            .Replace("%", EscapeCharacter + "%", StringComparison.Ordinal)
            .Replace("_", EscapeCharacter + "_", StringComparison.Ordinal)
            .Replace("[", EscapeCharacter + "[", StringComparison.Ordinal);

        return "%" + escaped + "%";
    }
}
