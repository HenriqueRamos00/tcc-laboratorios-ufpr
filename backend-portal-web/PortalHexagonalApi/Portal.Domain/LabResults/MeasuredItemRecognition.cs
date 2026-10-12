using Portal.Domain.LabResults.Exceptions;

namespace Portal.Domain.LabResults;

/// <summary>
/// Identifica os itens medidos de um protocolo. A lista resolve o nome que ela conhece, e o
/// que sobra passa pela aritmetica antes de ser recusado.
///
/// O motivo e que o nome do totalizador e a parte mais fragil da fonte. "Total de Gases" e
/// rotulo de relatorio, escolhido pelo laboratorio, e nada no AutoLAB impede que amanha
/// chegue "Soma dos Gases" ou "Gases Totais". Se o reconhecimento dependesse so do nome, um
/// rotulo novo derrubaria o laudo inteiro por item medido desconhecido, ou pior, entraria na
/// tabela como se fosse mais um gas medido e dobraria a soma do grafico. A soma, por outro
/// lado, nao depende de rotulo: se o valor publicado e exatamente a soma dos gases do
/// protocolo, aquela linha e um totalizador, qualquer que seja o nome dela.
///
/// Reconhecimento por aritmetica exige igualdade exata. Aproximar por tolerancia faria um
/// gas qualquer de valor parecido com a soma ser promovido a totalizador e desaparecer da
/// tabela de medicoes.
/// </summary>
public static class MeasuredItemRecognition
{
    public static IReadOnlyList<Measurement> Identify(IEnumerable<ResultBeforeRecognition> results)
    {
        ArgumentNullException.ThrowIfNull(results);

        List<ResultBeforeRecognition> received = [.. results];
        List<Measurement> byName = [.. received.Where(IsInTheKnownList).Select(AsMeasurement)];
        List<ResultBeforeRecognition> leftOver = [.. received.Where(result => !IsInTheKnownList(result))];

        return leftOver.Count == 0
            ? byName
            : [.. byName, .. RecognizeByArithmetic(leftOver, byName)];
    }

    private static bool IsInTheKnownList(ResultBeforeRecognition result)
    {
        return KnownMeasuredItems.Knows(result.SourceName);
    }

    private static Measurement AsMeasurement(ResultBeforeRecognition result)
    {
        return new Measurement(KnownMeasuredItems.Resolve(result.SourceName), result.Value);
    }

    /// <summary>
    /// O que sobrou do nome so pode entrar como totalizador, e so se o valor publicado for
    /// exatamente uma das duas somas. As somas sao calculadas uma vez, antes do laco: elas
    /// dependem dos gases ja reconhecidos pelo nome, e nenhum nome novo entra nesse grupo.
    ///
    /// Os codigos ja usados viajam junto porque um protocolo tem no maximo um total de gases
    /// e um total de combustiveis: sem essa conta, duas linhas de mesmo valor virariam o
    /// mesmo totalizador duas vezes.
    /// </summary>
    private static List<Measurement> RecognizeByArithmetic(
        IEnumerable<ResultBeforeRecognition> leftOver,
        IReadOnlyList<Measurement> byName)
    {
        decimal everyGas = SumKnownGases(byName, combustiblesOnly: false);
        decimal combustibles = SumKnownGases(byName, combustiblesOnly: true);
        HashSet<string> taken = [.. byName.Select(measurement => measurement.MeasuredItem.Code)];
        List<Measurement> recognized = [];

        foreach (ResultBeforeRecognition result in leftOver)
        {
            MeasuredItem totalizer =
                RecognizeTotalizerBySum(result, everyGas, combustibles, taken)
                ?? throw new ResultNameOutsideKnownListException(result.SourceName);

            taken.Add(totalizer.Code);
            recognized.Add(new Measurement(totalizer, result.Value));
        }

        return recognized;
    }


    /// <summary>
    /// A soma de todos os gases vem antes da soma dos combustiveis quando as duas batem,
    /// porque e a mais abrangente e porque o laudo sempre publica as duas: deixar a mais
    /// restrita ganhar faria o totalizador geral cair na vaga do de combustiveis e a vaga
    /// do geral ficar vazia.
    /// </summary>
    private static MeasuredItem? RecognizeTotalizerBySum(
        ResultBeforeRecognition result,
        decimal sumOfEveryGas,
        decimal sumOfCombustibles,
        HashSet<string> codesAlreadyTaken)
    {
        if (result.Value is not MeasuredValue.Quantity published) return null;

        bool overallSlotFree = !codesAlreadyTaken.Contains(KnownMeasuredItems.TotalGasesCode);
        bool combustiblesSlotFree = !codesAlreadyTaken.Contains(KnownMeasuredItems.CombustibleGasesTotalCode);

        if (overallSlotFree && published.NumericValue == sumOfEveryGas)
            return RecognizedTotalizer(KnownMeasuredItems.TotalGasesCode, result.SourceName);

        if (combustiblesSlotFree && published.NumericValue == sumOfCombustibles)
            return RecognizedTotalizer(KnownMeasuredItems.CombustibleGasesTotalCode, result.SourceName);

        return null;
    }

    /// <summary>
    /// O rotulo e o nome que veio da fonte, e nao o da lista de itens conhecidos: a tela deve
    /// mostrar o que o laboratorio escreveu no laudo, nao o nome que o portal esperava ler.
    /// </summary>
    private static MeasuredItem RecognizedTotalizer(string code, string sourceName)
    {
        return new MeasuredItem(
            code,
            sourceName.Trim(),
            string.Empty,
            "ppm",
            MeasuredItemKind.GasTotalizer,
            false,
            string.Empty,
            string.Empty);
    }

    private static decimal SumKnownGases(
        IEnumerable<Measurement> measurements,
        bool combustiblesOnly)
    {
        return measurements
            .Where(measurement => measurement.MeasuredItem.Role == MeasuredItemKind.DissolvedGas)
            .Where(measurement => !combustiblesOnly || measurement.MeasuredItem.IsCombustible)
            .Sum(measurement => measurement.Value is MeasuredValue.Quantity quantity
                ? quantity.NumericValue
                : 0m);
    }
}
