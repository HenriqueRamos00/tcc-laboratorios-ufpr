using System.Globalization;

using Portal.Domain.LabResults;

namespace Portal.Application.LabResults;

/// <summary>Converte uma medição já normalizada para os valores usados nas séries do Portal.</summary>
public static class ClientMeasurementMapper
{
    public static Measurement Map(ClientMeasurement measurement)
    {
        ArgumentNullException.ThrowIfNull(measurement);

        MeasuredItem item = ResolveItem(measurement);
        MeasuredValue value = measurement.Result switch
        {
            ClientMeasurementResult.Quantity quantity => new MeasuredValue.Quantity(quantity.Value),
            ClientMeasurementResult.NotDetected => MeasuredValue.NotDetected.Instance,
            ClientMeasurementResult.NotPerformed => MeasuredValue.TestNotPerformed.Instance,
            ClientMeasurementResult.Text text => new MeasuredValue.UninterpretedText(text.Value),
            _ => throw new InvalidOperationException("A medição contém um tipo de resultado desconhecido.")
        };

        return new Measurement(item, value);
    }

    public static string DecimalText(decimal value) => value.ToString(CultureInfo.InvariantCulture);

    private static MeasuredItem ResolveItem(ClientMeasurement measurement)
    {
        MeasuredItem? known = measurement.IndicatorCode is null
            ? null
            : KnownMeasuredItems.All.FirstOrDefault(item =>
                string.Equals(item.Code, measurement.IndicatorCode, StringComparison.Ordinal));

        if (known is not null)
            return known with
            {
                Label = measurement.Name,
                Unit = measurement.Unit,
                TestMethod = measurement.Method,
                RegulatoryLimit = measurement.ReferenceLimit,
                SourceCode = measurement.Code,
                LimitPublishedByLab = true
            };

        // Uma medição sem chave reconhecida não entra em cálculo nem desaparece do contrato.
        return new MeasuredItem(
            measurement.Code,
            measurement.Name,
            string.Empty,
            measurement.Unit,
            MeasuredItemKind.Other,
            false,
            measurement.Method,
            measurement.ReferenceLimit,
            measurement.Code,
            LimitPublishedByLab: true);
    }
}
