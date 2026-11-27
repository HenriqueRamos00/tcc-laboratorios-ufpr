using System.Globalization;

using Portal.Application.ReportDocuments;

namespace Portal.Adapters.ReportDocuments;

public static class ResultColor
{
    private const double MinimumSaturation = 0.10;
    private const double MinimumBrightness = 0.35;

    public static Conformity? ConformityOf((double Red, double Green, double Blue) color)
    {
        double max = Math.Max(color.Red, Math.Max(color.Green, color.Blue));
        double min = Math.Min(color.Red, Math.Min(color.Green, color.Blue));
        double delta = max - min;

        if (max < MinimumBrightness || delta / max < MinimumSaturation) return null;

        double hue = HueOf(color, max, delta);

        return hue switch
        {
            < 15 or >= 345 => Conformity.NonConforming,
            >= 30 and < 70 => Conformity.Warning,
            >= 75 and < 165 => Conformity.Normal,
            _ => null
        };
    }

    public static string HexOf((double Red, double Green, double Blue) color)
    {
        return string.Create(
            CultureInfo.InvariantCulture,
            $"#{ByteOf(color.Red):X2}{ByteOf(color.Green):X2}{ByteOf(color.Blue):X2}");
    }

    private static double HueOf((double Red, double Green, double Blue) color, double max, double delta)
    {
        double sector = max == color.Red
            ? (color.Green - color.Blue) / delta
            : max == color.Green
                ? 2 + (color.Blue - color.Red) / delta
                : 4 + (color.Red - color.Green) / delta;

        double hue = sector * 60;

        return hue < 0 ? hue + 360 : hue;
    }

    private static int ByteOf(double component)
    {
        return (int)Math.Round(Math.Clamp(component, 0, 1) * 255);
    }
}
