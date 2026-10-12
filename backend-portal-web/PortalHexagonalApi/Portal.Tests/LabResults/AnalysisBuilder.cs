using Portal.Domain.LabResults;

namespace Portal.Tests.LabResults;

/// <summary>
/// Monta analises validas para o teste poder dizer apenas o que esta variando.
/// </summary>
internal static class AnalysisBuilder
{
    public const string SampleCompany = "EMPRESA DE ENERGIA";

    public static SampleAnalysis Valid(
        string protocoloLactec = "01417/2026-001",
        string identification = "LACTEC-02272/2026",
        string? serialNumber = "571289",
        string company = SampleCompany,
        DateTime? analysisEndDate = null,
        IEnumerable<Measurement>? measurements = null)
    {
        return new SampleAnalysis(
            protocoloLactec,
            identification,
            serialNumber,
            company,
            new DateTime(2026, 6, 24),
            analysisEndDate ?? new DateTime(2026, 6, 23),
            measurements ?? [MeasurementOf("Hidrogênio", "60")]);
    }

    public static Measurement MeasurementOf(string sourceName, string? result)
    {
        return new Measurement(
            KnownMeasuredItems.Resolve(sourceName),
            MeasuredValue.InterpretResult(result));
    }
}
