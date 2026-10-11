using Portal.Domain.LabResults.Exceptions;
using System.Reflection;

using Portal.Domain.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class SampleAnalysisTests
{
    [Fact]
    public void AnAnalysisWithoutAProtocolIsNotBuilt()
    {
        Assert.Throws<AnalysisWithoutProtocolException>(() => AnalysisBuilder.Valid(protocoloLactec: "   "));
    }

    [Fact]
    public void AnAnalysisWithoutAnIdentificationIsNotBuilt()
    {
        Assert.Throws<AnalysisWithoutIdentificationException>(() => AnalysisBuilder.Valid(identification: string.Empty));
    }

    /// <summary>
    /// A empresa e a unica coluna da view que diz de quem e o transformador. Analise sem
    /// empresa nao tem como ser filtrada por inquilino, e serviria o laudo de um cliente
    /// para qualquer um.
    /// </summary>
    [Fact]
    public void AnAnalysisWithoutACompanyIsNotBuilt()
    {
        Assert.Throws<AnalysisWithoutCompanyException>(() => AnalysisBuilder.Valid(company: " "));
    }

    [Fact]
    public void AnAnalysisWithoutAnyMeasurementIsNotBuilt()
    {
        Assert.Throws<AnalysisWithoutMeasurementsException>(() => AnalysisBuilder.Valid(measurements: []));
    }

    [Fact]
    public void AnAnalysisWithNullMeasurementsIsNotBuilt()
    {
        Assert.Throws<ArgumentNullException>(() => new SampleAnalysis(
            "01417/2026-001",
            "LACTEC-02272/2026",
            "571289",
            AnalysisBuilder.SampleCompany,
            new DateTime(2026, 6, 24),
            new DateTime(2026, 6, 23),
            null!));
    }

    /// <summary>
    /// O endereco do equipamento e a unica coisa que nao pode faltar: e com ele que a rota
    /// chega de volta neste laudo. Sem numero de serie ele cai no protocolo, que a validacao
    /// do construtor ja garantiu nao vazio; afrouxar aquela validacao deixa este caso vermelho.
    /// </summary>
    [Theory]
    [InlineData("571289", "571289")]
    [InlineData(null, "01439/2026-004")]
    [InlineData("   ", "01439/2026-004")]
    public void EveryAnalysisIsBornWithAnEquipmentAddress(string? serialNumber, string expected)
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(
            protocoloLactec: "01439/2026-004",
            serialNumber: serialNumber);

        Assert.Equal(expected, analysis.EquipmentKey);
        Assert.False(string.IsNullOrWhiteSpace(analysis.EquipmentKey));
    }

    [Fact]
    public void ARepeatedItemInTheSameAnalysisIsNotBuilt()
    {
        Assert.Throws<RepeatedMeasuredItemException>(() => AnalysisBuilder.Valid(measurements:
        [
            AnalysisBuilder.MeasurementOf("Hidrogênio", "60"),
            AnalysisBuilder.MeasurementOf("Hidrogênio", "61")
        ]));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    public void ABlankSerialNumberBecomesUnassigned(string? serialNumberFromSource)
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(serialNumber: serialNumberFromSource);

        Assert.Null(analysis.SerialNumber);
        Assert.True(analysis.SerialNumberIsPending);
    }

    [Fact]
    public void ANonNumericSerialNumberIsPreserved()
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(serialNumber: "111.594/1");

        Assert.Equal("111.594/1", analysis.SerialNumber);
    }

    [Fact]
    public void AnAbsentItemDoesNotBecomeZero()
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid();

        Assert.IsType<MeasuredValue.TestNotPerformed>(analysis.ValueOf("c2h2"));
    }

    /// <summary>
    /// Por reflexao, e nao contra uma assinatura escrita a mao: se alguem adicionar uma
    /// sobrecarga que delega para a base com validacao mais fraca, este teste acusa que o
    /// caso passou a existir, em vez de continuar verde sobre o construtor antigo.
    /// </summary>
    [Fact]
    public void EveryPublicConstructorRefusesAnEmptyProtocol()
    {
        ConstructorInfo[] constructors = typeof(SampleAnalysis).GetConstructors();

        Assert.NotEmpty(constructors);

        foreach (ConstructorInfo constructor in constructors)
        {
            ParameterInfo[] parameters = constructor.GetParameters();
            Assert.Contains(parameters, parameter => parameter.Name == "protocoloLactec");

            object?[] arguments = [.. parameters.Select(ValidArgument)];
            arguments[Array.FindIndex(parameters, parameter => parameter.Name == "protocoloLactec")] = string.Empty;

            TargetInvocationException failure = Assert.Throws<TargetInvocationException>(
                () => constructor.Invoke(arguments));

            Assert.IsType<AnalysisWithoutProtocolException>(failure.InnerException);
        }
    }

    private static object? ValidArgument(ParameterInfo parameter)
    {
        if (parameter.ParameterType == typeof(DateTime)) return new DateTime(2026, 6, 23);

        if (parameter.ParameterType == typeof(IEnumerable<Measurement>))
            return new[] { AnalysisBuilder.MeasurementOf("Hidrogênio", "60") };

        if (parameter.ParameterType == typeof(string)) return "filled-value";

        return parameter.ParameterType.IsValueType
            ? Activator.CreateInstance(parameter.ParameterType)
            : null;
    }
}
