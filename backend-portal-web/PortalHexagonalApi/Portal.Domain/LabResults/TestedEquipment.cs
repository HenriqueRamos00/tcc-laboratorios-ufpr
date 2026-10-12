namespace Portal.Domain.LabResults;

/// <summary>
/// Um equipamento visto pelos laudos que ele tem. O AutoLAB nao tem cadastro de
/// equipamento: a view de dados de ensaio e a unica fonte, e cada linha dela fala de um
/// item medido, nao de um transformador. Entao o equipamento aqui e uma reuniao de analises que
/// compartilham a mesma chave, e nao um registro lido de uma tabela.
///
/// Por isso tambem nao existem TAG, tipo, localizacao, tensao nem potencia: nenhuma dessas
/// colunas aparece na fonte. Esta classe carrega o que o dado garante e nada mais, e quem
/// monta a tela decide como anunciar a ausencia.
/// </summary>
public sealed class TestedEquipment
{
    private TestedEquipment(
        string equipmentKey,
        string? serialNumber,
        string company,
        IReadOnlyList<EquipmentReport> reports)
    {
        EquipmentKey = equipmentKey;
        SerialNumber = serialNumber;
        Company = company;
        Reports = reports;
    }

    public string EquipmentKey { get; }

    /// <summary>
    /// Nulo quando todos os laudos do equipamento vieram com a coluna em branco.
    /// </summary>
    public string? SerialNumber { get; }

    public string Company { get; }

    /// <summary>
    /// Do laudo mais recente para o mais antigo. A tela pagina a partir daqui, e inverter
    /// essa ordem mostraria o laudo mais velho como primeira linha da primeira pagina.
    /// </summary>
    public IReadOnlyList<EquipmentReport> Reports { get; }

    public EquipmentReport MostRecentReport => Reports[0];

    /// <summary>
    /// O que a listagem imprime na coluna de identificacao do equipamento. O numero de
    /// serie quando existe, e a identificacao do laudo mais recente quando nao existe, que e
    /// a mesma regra que a tela de indicadores ja usa: assim a linha da lista e o titulo da
    /// tela de dentro dizem a mesma coisa sobre o mesmo equipamento.
    /// </summary>
    public string DisplayLabel => SerialNumber ?? MostRecentReport.Identification;

    /// <summary>
    /// Agrupa as analises de um inquilino nos equipamentos que elas descrevem.
    ///
    /// O agrupamento e pela chave do equipamento, que cai no protocolo quando o numero de
    /// serie esta em branco. Agrupar pelo numero de serie cru juntaria num equipamento so
    /// todos os laudos sem serie, de transformadores diferentes e possivelmente de clientes
    /// diferentes.
    /// </summary>
    public static IReadOnlyList<TestedEquipment> Gather(IEnumerable<SampleAnalysis> analyses)
    {
        ArgumentNullException.ThrowIfNull(analyses);

        return [.. analyses
            .GroupBy(analysis => analysis.EquipmentKey, StringComparer.Ordinal)
            .Select(Build)
            .OrderByDescending(equipment => equipment.MostRecentReport.AnalysisEndDate)
            .ThenBy(equipment => equipment.DisplayLabel, StringComparer.Ordinal)];
    }

    private static TestedEquipment Build(IGrouping<string, SampleAnalysis> analysesOfOneEquipment)
    {
        List<SampleAnalysis> fromMostRecent = [.. analysesOfOneEquipment
            .OrderByDescending(analysis => analysis.AnalysisEndDate)
            .ThenBy(analysis => analysis.ProtocoloLactec, StringComparer.Ordinal)];

        return new TestedEquipment(
            analysesOfOneEquipment.Key,
            fromMostRecent.Select(analysis => analysis.SerialNumber).FirstOrDefault(serial => serial is not null),
            fromMostRecent[0].Company,
            [.. fromMostRecent.Select(BuildReport)]);
    }

    /// <summary>
    /// Laudo que traz gas dissolvido e anunciado como cromatografia mesmo quando tambem traz
    /// grandeza fisico-quimica. A escolha e deliberada: o tipo existe para a linha do
    /// historico abrir a aba certa, e a aba de gases e a que tem diagnostico, totalizador e
    /// grafico. Abrir a fisico-quimica num laudo que tem as duas esconderia a parte mais
    /// informativa atras de um clique.
    /// </summary>
    private static EquipmentReport BuildReport(SampleAnalysis analysis)
    {
        return new EquipmentReport(
            analysis.ProtocoloLactec,
            analysis.Identification,
            analysis.AnalysisEndDate,
            analysis.IssueDate,
            analysis.HasDissolvedGas
                ? ReportAnalysisType.Chromatography
                : ReportAnalysisType.PhysicalChemical);
    }
}
