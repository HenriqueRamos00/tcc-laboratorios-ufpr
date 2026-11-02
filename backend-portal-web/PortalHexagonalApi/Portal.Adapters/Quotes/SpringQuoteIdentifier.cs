using System.Globalization;

namespace Portal.Adapters.Quotes;

internal static class SpringQuoteIdentifier
{
    public static bool IsValid(string value) =>
        long.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out long id) && id > 0;
}
