using Portal.Domain.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class ResultInterpretationTests
{
    [Fact]
    public void EmptyResultDoesNotBecomeZero()
    {
        MeasuredValue value = MeasuredValue.InterpretResult(string.Empty);

        Assert.IsType<MeasuredValue.TestNotPerformed>(value);
        Assert.IsNotType<MeasuredValue.Quantity>(value);
    }

    [Fact]
    public void NullResultInTheDatabaseDoesNotBreakTheReading()
    {
        MeasuredValue value = MeasuredValue.InterpretResult(null);

        Assert.IsType<MeasuredValue.TestNotPerformed>(value);
    }

    [Fact]
    public void NotDetectedDoesNotBecomeTestNotPerformed()
    {
        MeasuredValue value = MeasuredValue.InterpretResult("ND");

        Assert.IsType<MeasuredValue.NotDetected>(value);
        Assert.IsNotType<MeasuredValue.TestNotPerformed>(value);
    }

    [Theory]
    [InlineData("ND")]
    [InlineData("nd")]
    [InlineData(" ND ")]
    public void NotDetectedIsRecognizedWithSpacesAndLowercase(string resultFromSource)
    {
        Assert.IsType<MeasuredValue.NotDetected>(MeasuredValue.InterpretResult(resultFromSource));
    }

    [Theory]
    [InlineData("2617", 2617)]
    [InlineData("0,006", 0.006)]
    [InlineData("1,934", 1.934)]
    [InlineData("-5", -5)]
    [InlineData("0", 0)]
    public void NumberIsReadWithTheBrazilianDecimalSeparator(string resultFromSource, double expected)
    {
        MeasuredValue value = MeasuredValue.InterpretResult(resultFromSource);

        MeasuredValue.Quantity quantity = Assert.IsType<MeasuredValue.Quantity>(value);
        Assert.Equal((decimal)expected, quantity.NumericValue);
    }

    /// <summary>
    /// Em pt-BR o ponto e separador de milhar. Aceita-lo faria "1.934" virar mil novecentos
    /// e trinta e quatro, mil vezes o valor que o laboratorio escreveu.
    /// </summary>
    [Fact]
    public void DotIsNotReadAsADecimalSeparator()
    {
        MeasuredValue value = MeasuredValue.InterpretResult("1.934");

        Assert.IsType<MeasuredValue.UninterpretedText>(value);
    }

    [Theory]
    [InlineData("0,01%")]
    [InlineData("< 1")]
    [InlineData("traço")]
    public void TextThatIsNeitherNumberNorNotDetectedIsKeptAsText(string resultFromSource)
    {
        MeasuredValue value = MeasuredValue.InterpretResult(resultFromSource);

        MeasuredValue.UninterpretedText uninterpreted =
            Assert.IsType<MeasuredValue.UninterpretedText>(value);
        Assert.Equal(resultFromSource.Trim(), uninterpreted.OriginalText);
    }

    [Fact]
    public void UninterpretedTextDoesNotBecomeAnAbsentTest()
    {
        MeasuredValue value = MeasuredValue.InterpretResult("0,01%");

        Assert.IsNotType<MeasuredValue.TestNotPerformed>(value);
        Assert.IsNotType<MeasuredValue.Quantity>(value);
    }
}
