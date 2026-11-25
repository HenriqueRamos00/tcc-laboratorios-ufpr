using System.Globalization;
using System.Text;

namespace Portal.Domain.LabResults;

public enum InsulatingOilKind
{
    Mineral,
    NaturalEster,
    Silicone,
    NotInformed,
    Unrecognized
}

public enum PhysicalChemicalLimitAbsence
{
    OilTypeWithoutCriterion,
    OilTypeNotInformed
}

public sealed record PhysicalChemicalLimit(string? Value, PhysicalChemicalLimitAbsence? Absence);

public sealed record InsulatingOil(InsulatingOilKind Kind, string? Reported)
{
    public static InsulatingOil Recognize(string? reported)
    {
        if (string.IsNullOrWhiteSpace(reported)) return new InsulatingOil(InsulatingOilKind.NotInformed, null);

        string stated = reported.Trim();
        string text = Plain(stated);

        InsulatingOilKind kind = text switch
        {
            _ when text.Contains("silicon", StringComparison.Ordinal) => InsulatingOilKind.Silicone,
            _ when text.Contains("sintetic", StringComparison.Ordinal)
                || text.Contains("synthetic", StringComparison.Ordinal) => InsulatingOilKind.Unrecognized,
            _ when text.Contains("vegetal", StringComparison.Ordinal)
                || text.Contains("ester", StringComparison.Ordinal) => InsulatingOilKind.NaturalEster,
            _ when text.Contains("mineral", StringComparison.Ordinal) => InsulatingOilKind.Mineral,
            _ => InsulatingOilKind.Unrecognized
        };

        return new InsulatingOil(kind, stated);
    }

    public PhysicalChemicalLimit LimitOf(MeasuredItem measuredItem)
    {
        ArgumentNullException.ThrowIfNull(measuredItem);

        if (measuredItem.LimitPublishedByLab) return new PhysicalChemicalLimit(measuredItem.RegulatoryLimit, null);

        return Kind switch
        {
            InsulatingOilKind.Mineral => new PhysicalChemicalLimit(measuredItem.RegulatoryLimit, null),
            InsulatingOilKind.NaturalEster or InsulatingOilKind.Silicone =>
                new PhysicalChemicalLimit(null, PhysicalChemicalLimitAbsence.OilTypeWithoutCriterion),
            _ => new PhysicalChemicalLimit(null, PhysicalChemicalLimitAbsence.OilTypeNotInformed)
        };
    }

    private static string Plain(string text)
    {
        StringBuilder plain = new(text.Length);

        foreach (char character in text.Normalize(NormalizationForm.FormD))
        {
            if (CharUnicodeInfo.GetUnicodeCategory(character) != UnicodeCategory.NonSpacingMark)
                plain.Append(char.ToLowerInvariant(character));
        }

        return plain.ToString();
    }
}
