using Portal.Domain.LabResults.Exceptions;

namespace Portal.Domain.LabResults;

/// <summary>
/// Uma analise de amostra do AutoLAB, identificada pelo protocolo Lactec. A identidade e o
/// protocolo porque foi a unica chave presente, nao nula e unica em todo o dado observado:
/// o par numero de serie mais data tem os dois componentes indefinidos na amostra real.
///
/// O numero de serie e opcional de proposito. Existe laudo com a coluna em branco, e
/// transformar branco em chave de agrupamento funde transformadores diferentes, o que pode
/// misturar clientes.
/// </summary>
public sealed class SampleAnalysis
{
    public SampleAnalysis(
        string protocoloLactec,
        string identification,
        string? serialNumber,
        string company,
        DateTime issueDate,
        DateTime analysisEndDate,
        IEnumerable<Measurement> measurements)
    {
        if (string.IsNullOrWhiteSpace(protocoloLactec)) throw new AnalysisWithoutProtocolException();
        if (string.IsNullOrWhiteSpace(identification)) throw new AnalysisWithoutIdentificationException();
        if (string.IsNullOrWhiteSpace(company)) throw new AnalysisWithoutCompanyException();

        ProtocoloLactec = protocoloLactec.Trim();
        Identification = identification.Trim();
        SerialNumber = Blank(serialNumber) ? null : serialNumber!.Trim();
        Company = company.Trim();
        EquipmentKey = SerialNumber ?? ProtocoloLactec;
        IssueDate = issueDate;
        AnalysisEndDate = analysisEndDate;
        Measurements = Accept(measurements);
    }

    /// <summary>
    /// A lista de medicoes entra inteira ou nao entra. As tres recusas sao distintas e cada
    /// uma tem nome proprio, porque cada uma manda procurar num lugar diferente: lista vazia
    /// e erro de leitura da fonte, buraco no meio e erro de quem montou a lista, e item
    /// repetido e divergencia entre a fonte e a tabela do laudo.
    /// </summary>
    private static IReadOnlyList<Measurement> Accept(IEnumerable<Measurement> measurements)
    {
        ArgumentNullException.ThrowIfNull(measurements);

        List<Measurement> received = [.. measurements];

        if (received.Count == 0) throw new AnalysisWithoutMeasurementsException();
        if (received.Any(measurement => measurement is null)) throw new NullMeasurementException();

        string? repeated = FirstRepeatedItemCode(received);

        if (repeated is not null) throw new RepeatedMeasuredItemException(repeated);

        return received;
    }

    private static string? FirstRepeatedItemCode(IEnumerable<Measurement> measurements)
    {
        return measurements
            .GroupBy(measurement => measurement.MeasuredItem.Code, StringComparer.Ordinal)
            .Where(group => group.Count() > 1)
            .Select(group => group.Key)
            .FirstOrDefault();
    }

    private static bool Blank(string? value)
    {
        return string.IsNullOrWhiteSpace(value);
    }

    public string ProtocoloLactec { get; }

    public string Identification { get; }

    /// <summary>
    /// Nulo quando a fonte veio em branco. Nao e numerico: existe "111.594/1" na amostra.
    /// </summary>
    public string? SerialNumber { get; }

    public string Company { get; }

    public DateTime IssueDate { get; }

    public DateTime AnalysisEndDate { get; }

    public IReadOnlyList<Measurement> Measurements { get; }

    /// <summary>
    /// Verdadeiro quando a analise chegou sem numero de serie. O diagrama da fonte manda
    /// considerar o numero sempre preenchido, e a grade real tem laudo com a coluna em
    /// branco: enquanto o cadastro do equipamento nao existe, a tela precisa poder dizer
    /// "cadastro pendente" em vez de exibir o protocolo como se fosse a placa.
    /// </summary>
    public bool SerialNumberIsPending => SerialNumber is null;

    /// <summary>
    /// Por onde a tela endereca o equipamento, e a unica coisa nesta classe que nunca pode
    /// faltar. E resolvido no construtor, e nao a cada leitura, porque e invariante: toda
    /// analise construida ja sai com um endereco, e nao ha caminho em que ele apareca vazio.
    ///
    /// E o numero de serie quando a fonte trouxe um, e o protocolo quando nao trouxe. Sem
    /// essa queda para o protocolo o laudo sem numero de serie fica inalcancavel: a rota
    /// carrega uma chave, o repositorio compara essa chave com o numero de serie, e nenhuma
    /// chave e igual a nulo. O laudo existiria no banco, apareceria na listagem e devolveria
    /// 404 ao ser aberto. Cair no protocolo mantem a identidade separada, porque o protocolo
    /// e unico, em vez de juntar num grupo so todos os laudos sem serie, que e o que agrupar
    /// por branco faria.
    ///
    /// O protocolo ja foi recusado em branco la em cima, entao esta propriedade herda a
    /// garantia dele: se um dia alguem afrouxar aquela validacao, e o teste do endereco que
    /// fica vermelho.
    /// </summary>
    public string EquipmentKey { get; }

    public bool HasDissolvedGas =>
        Measurements.Any(measurement => measurement.MeasuredItem.Role == MeasuredItemKind.DissolvedGas);

    public bool HasPhysicalChemicalProperty =>
        Measurements.Any(measurement => measurement.MeasuredItem.Role == MeasuredItemKind.PhysicalChemicalProperty);

    public MeasuredValue ValueOf(string code)
    {
        Measurement? found = Measurements
            .FirstOrDefault(measurement => string.Equals(measurement.MeasuredItem.Code, code, StringComparison.Ordinal));

        return found?.Value ?? MeasuredValue.TestNotPerformed.Instance;
    }

    /// <summary>
    /// Confere cada totalizador publicado contra a soma aritmetica dos gases, com nao
    /// detectado valendo zero.
    ///
    /// Quem reconhece o totalizador e <see cref="MeasuredItemRecognition"/>, antes de a
    /// analise existir, e la a soma vale mais que o nome. Aqui dentro o papel do item medido ja
    /// esta decidido, e a conferencia compara o valor publicado com a soma dos gases do
    /// escopo correspondente. A conferencia fecha exata nos tres protocolos de cromatografia
    /// da amostra real.
    ///
    /// Gas com ensaio nao realizado nao entra na soma e nao pode ser tratado como zero: a
    /// soma deixa de ser comparavel com o publicado, e o desfecho e
    /// <see cref="TotalizerCheckResult.IncompleteSum"/>.
    /// </summary>
    public IReadOnlyList<TotalizerCheck> CheckTotalizers()
    {
        List<TotalizerCheck> checks = [];

        foreach (Measurement totalizer in Measurements
            .Where(measurement => measurement.MeasuredItem.Role == MeasuredItemKind.GasTotalizer))
        {
            if (totalizer.Value is not MeasuredValue.Quantity published) continue;

            bool combustiblesOnly = totalizer.MeasuredItem.Code == KnownMeasuredItems.CombustibleGasesTotalCode;

            decimal sum = SumGases(combustiblesOnly);
            int gasesWithoutTest = CountGasesWithoutTest(combustiblesOnly);

            checks.Add(new TotalizerCheck(
                totalizer.MeasuredItem.Code,
                published.NumericValue,
                sum,
                gasesWithoutTest,
                Judge(published.NumericValue, sum, gasesWithoutTest)));
        }

        return checks;
    }

    private static TotalizerCheckResult Judge(decimal publishedValue, decimal sum, int gasesWithoutTest)
    {
        if (gasesWithoutTest > 0) return TotalizerCheckResult.IncompleteSum;

        return sum == publishedValue
            ? TotalizerCheckResult.Matches
            : TotalizerCheckResult.Diverges;
    }

    /// <summary>
    /// So quantidade entra na soma. Nao detectado vale zero porque o laboratorio mediu e nao
    /// achou, e e assim que o totalizador publicado fecha na amostra real. Ensaio nao
    /// realizado e texto nao interpretado tambem nao somam nada, mas por falta de parcela, e
    /// nao por valer zero: e <see cref="CountGasesWithoutTest"/> que carrega essa diferenca
    /// para o veredito, porque aqui ela desapareceria dentro do numero.
    /// </summary>
    private decimal SumGases(bool combustiblesOnly)
    {
        return GasesInScope(combustiblesOnly)
            .Sum(measurement => measurement.Value is MeasuredValue.Quantity quantity
                ? quantity.NumericValue
                : 0m);
    }

    /// <summary>
    /// Texto nao interpretado conta junto com ensaio nao realizado: nos dois casos nao ha
    /// numero para entrar na soma, e o que a conferencia precisa saber e se faltou parcela.
    /// </summary>
    private int CountGasesWithoutTest(bool combustiblesOnly)
    {
        return GasesInScope(combustiblesOnly)
            .Count(measurement => measurement.Value
                is MeasuredValue.TestNotPerformed
                or MeasuredValue.UninterpretedText);
    }

    private IEnumerable<Measurement> GasesInScope(bool combustiblesOnly)
    {
        return Measurements
            .Where(measurement => measurement.MeasuredItem.Role == MeasuredItemKind.DissolvedGas)
            .Where(measurement => !combustiblesOnly || measurement.MeasuredItem.IsCombustible);
    }
}
