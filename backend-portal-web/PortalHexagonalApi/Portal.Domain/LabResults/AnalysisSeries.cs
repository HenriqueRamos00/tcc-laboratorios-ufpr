namespace Portal.Domain.LabResults;

/// <summary>
/// A travessia de estreito para largo: a fonte entrega uma linha por item medido e a serie
/// entrega uma leitura por analise, em ordem cronologica de data final da analise.
///
/// Nao existe coluna de data de coleta no AutoLAB. A ordenacao e por data final da analise,
/// e e esse o nome usado em todo o caminho, para a tela nao prometer uma data que o
/// laboratorio nunca registrou.
/// </summary>
public sealed class AnalysisSeries
{
    private AnalysisSeries(
        string equipmentKey,
        IReadOnlyList<SeriesReading> readings,
        IReadOnlyList<MeasuredItem> itemsPresent)
    {
        EquipmentKey = equipmentKey;
        Readings = readings;
        ItemsPresent = itemsPresent;
    }

    public string EquipmentKey { get; }

    public IReadOnlyList<SeriesReading> Readings { get; }

    /// <summary>
    /// Os itens medidos que de fato aparecem em alguma leitura da serie, em ordem de
    /// publicacao. Quem monta a tabela percorre esta lista, e nao a lista de itens
    /// conhecidos: assim o rotulo exibido e o do item medido que veio na fonte, inclusive o
    /// totalizador reconhecido pela soma, cujo nome a lista nao conhece.
    /// </summary>
    public IReadOnlyList<MeasuredItem> ItemsPresent { get; }

    public bool IsEmpty => Readings.Count == 0;

    /// <summary>
    /// Os itens medidos de um papel, na mesma ordem de publicacao. Quem monta a tabela pergunta
    /// aqui em vez de filtrar <see cref="ItemsPresent"/> por conta propria: o que entra
    /// na tabela e decisao do laudo, e decisao de laudo mora no dominio.
    ///
    /// Ja esteve na borda, e com escopo errado: o adaptador removia da tabela todo item medido
    /// que tivesse qualquer leitura ilegivel, de modo que um "0,01%" num laudo apagava as
    /// medicoes boas daquele item medido em todos os outros laudos do equipamento. A celula
    /// ilegivel degrada a propria celula, e <see cref="MeasuredValue.UninterpretedText"/>
    /// e quem a carrega ate o fio.
    /// </summary>
    public IEnumerable<MeasuredItem> ItemsOf(MeasuredItemKind role)
    {
        return ItemsPresent.Where(measuredItem => measuredItem.Role == role);
    }

    /// <summary>
    /// Monta a serie de um equipamento. A chave e a de
    /// <see cref="SampleAnalysis.EquipmentKey"/>, que nunca e nula, e toda analise
    /// recebida precisa ter exatamente essa chave.
    ///
    /// Era por numero de serie antes, e numero de serie pode vir em branco. Com dois laudos
    /// de transformadores diferentes os dois sem serie, a comparacao de nulo com nulo dava
    /// igual e os dois entravam na mesma serie: duas figuras de equipamentos distintos
    /// empilhadas num grafico so, podendo ser de clientes distintos. A chave fecha esse
    /// caminho por construcao, em vez de por checagem de contagem.
    /// </summary>
    public static AnalysisSeries Build(string equipmentKey, IEnumerable<SampleAnalysis> analyses)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(equipmentKey);
        ArgumentNullException.ThrowIfNull(analyses);

        string normalizedKey = equipmentKey.Trim();
        List<SampleAnalysis> received = [.. analyses];

        SampleAnalysis? stranger = received.FirstOrDefault(analysis =>
            !string.Equals(analysis.EquipmentKey, normalizedKey, StringComparison.Ordinal));

        if (stranger is not null)
            throw new ArgumentException(
                $"Analise '{stranger.ProtocoloLactec}' pertence a outro equipamento que nao '{normalizedKey}'.",
                nameof(analyses));

        List<SampleAnalysis> inOrder = [.. received
            .OrderBy(analysis => analysis.AnalysisEndDate)
            .ThenBy(analysis => analysis.ProtocoloLactec, StringComparer.Ordinal)];

        return new AnalysisSeries(
            normalizedKey,
            [.. inOrder.Select(TransposeToReading)],
            GatherItemsPresent(inOrder));
    }

    /// <summary>
    /// A ordem e a da lista de itens conhecidos, para a tabela nao trocar de ordem de coluna
    /// quando a fonte devolve as linhas em outra sequencia. Item medido reconhecido pela soma
    /// nao esta no lista de itens conhecidos e vai para o fim do seu grupo.
    /// </summary>
    private static IReadOnlyList<MeasuredItem> GatherItemsPresent(IEnumerable<SampleAnalysis> analyses)
    {
        Dictionary<string, MeasuredItem> byCode = new(StringComparer.Ordinal);

        foreach (Measurement measurement in analyses.SelectMany(analysis => analysis.Measurements))
            byCode.TryAdd(measurement.MeasuredItem.Code, measurement.MeasuredItem);

        return [.. byCode.Values.OrderBy(measuredItem => KnownMeasuredItems.PublicationOrder(measuredItem.Code))];
    }

    private static SeriesReading TransposeToReading(SampleAnalysis analysis)
    {
        Dictionary<string, MeasuredValue> values = new(StringComparer.Ordinal);

        foreach (Measurement measurement in analysis.Measurements)
            values[measurement.MeasuredItem.Code] = measurement.Value;

        return new SeriesReading(
            analysis.ProtocoloLactec,
            analysis.Identification,
            analysis.AnalysisEndDate,
            values);
    }
}
