using Portal.Application.Nameplate;
using Portal.Domain.Nameplate;

namespace Portal.Adapters.Nameplate;

/// <summary>
/// Adaptador conduzido que serve uma amostra do cadastro de equipamentos guardada em memoria.
/// O nome diz a tecnologia que ele de fato usa: nao ha conexao, nao ha transacao, nao ha
/// latencia e ele nao imita nada disso. Chamar um adaptador assim de "fake SQL Server"
/// prometeria uma fidelidade que ele nao entrega e ocuparia a vaga do adaptador real.
///
/// A amostra inteira foi escrita a mao, e nenhuma linha dela foi copiada do cadastro. Isso
/// nao e zelo decorativo: dez linhas reais carregam nome de cliente dentro de <c>NumSerie</c>
/// e de <c>Tag</c>, e o nome do fabricante junto com a potencia identifica o parque do
/// cliente. O que ficou aqui e a FORMA medida nas 20575 linhas, com TAG e fabricante
/// genericos. As combinacoes sao as do censo, uma por classe, e nao a contagem delas.
///
/// Os quatro primeiros numeros de serie sao os de <c>LabTestSample.jsonl</c>, para a
/// demonstracao mostrar a juncao funcionando de ponta a ponta. As series <c>884120</c> e
/// <c>884121</c> estao ausentes de proposito: sao as duas que este projeto inventou porque a
/// grade real trazia a coluna em branco, e no cadastro de verdade elas tambem nao existem. A
/// tela precisa saber dizer "o cadastro nao conhece esta serie", e e esse o caminho que elas
/// exercitam.
///
/// As series <c>9000...</c> nao pertencem a laudo nenhum da amostra e existem para a suite
/// exercitar as classes que nenhum equipamento da demonstracao tem: a serie ambigua, a
/// abstencao do campo vazio, a potencia com barra, o marcador, a linha que so tem serie, a
/// unidade orfa, a linha sem serie e a serie escrita com marcador.
/// </summary>
public sealed class InMemoryEquipmentRegistryRepository : IEquipmentNameplateRepository
{
    private static readonly IReadOnlyList<EquipmentRow> Rows =
    [
        // Equipamentos da amostra de ensaios: a placa completa, como a listagem quer mostrar.
        new EquipmentRow
        {
            NumSerie = "571289", Tag = "TR-00001", AnoFabricacao = "1979",
            Potencia = "50", UnidadePotencia = "MVA", Tensao = "220",
            Fabricante = "FABRICANTE DE TRANSFORMADORES", VolumeOleo = "20570",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "TRANSFORMADOR"
        },
        new EquipmentRow
        {
            NumSerie = "111.594/1", Tag = "TR-00002", AnoFabricacao = "2011",
            Potencia = "45,33", UnidadePotencia = "MVA", Tensao = "500",
            Fabricante = "FABRICANTE DE TRANSFORMADORES", VolumeOleo = "16950",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "TRANSFORMADOR DE POTÊNCIA"
        },
        // A classe das 100 linhas com potencia e sem unidade, mais a TAG vazia das 9205.
        new EquipmentRow
        {
            NumSerie = "36170301", Tag = "", AnoFabricacao = "",
            Potencia = "7", UnidadePotencia = "", Tensao = "34,5",
            Fabricante = "MONTADORA DE EQUIPAMENTOS", VolumeOleo = "3750",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "TRANSFORMADOR"
        },
        // Equipamento de outro inquilino. Existe para a suite provar que o cadastro nao e
        // caminho de travessia: a placa dele esta aqui e continua inalcancavel.
        new EquipmentRow
        {
            NumSerie = "5233", Tag = "TR-00004", AnoFabricacao = "1998",
            Potencia = "4,20", UnidadePotencia = "MVA", Tensao = "34,5",
            Fabricante = "MONTADORA DE EQUIPAMENTOS", VolumeOleo = "2500",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "TRANSFORMADOR"
        },
        // A forma das 49 series ambiguas: duas linhas que discordam em tensao e em potencia e
        // concordam em fabricante, ano e tipo de oleo. E a forma medida de 1.110.009/4, que
        // aparece com tensao 525 e 13,8 na mesma TAG.
        new EquipmentRow
        {
            NumSerie = "9000001", Tag = "TR-00011", AnoFabricacao = "2014",
            Potencia = "111", UnidadePotencia = "MVA", Tensao = "525",
            Fabricante = "FABRICANTE DE REATORES", VolumeOleo = "23600",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "REATOR"
        },
        new EquipmentRow
        {
            NumSerie = "9000001", Tag = "TR-00011", AnoFabricacao = "2014",
            Potencia = "111,00", UnidadePotencia = "MVA", Tensao = "13,8",
            Fabricante = "FABRICANTE DE REATORES", VolumeOleo = "27960",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "REATOR"
        },
        // A forma medida de 257373: uma das duas linhas tem tres colunas em branco. Branco se
        // abstem, entao a placa fica com o que a outra linha escreveu.
        new EquipmentRow
        {
            NumSerie = "9000002", Tag = "TR-00012", AnoFabricacao = "2007",
            Potencia = "500", UnidadePotencia = "", Tensao = "15",
            Fabricante = "FABRICANTE DE TRANSFORMADORES", VolumeOleo = "275",
            TipoDeOleo = "", TipoEquipamento = ""
        },
        new EquipmentRow
        {
            NumSerie = "9000002", Tag = "TR-00012", AnoFabricacao = "2007",
            Potencia = "500", UnidadePotencia = "kVA", Tensao = "15",
            Fabricante = "FABRICANTE DE TRANSFORMADORES", VolumeOleo = "275",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "TRANSFORMADOR"
        },
        // Enrolamentos multiplos, a classe das 14 linhas com barra na potencia, com ano em
        // mes e ano e tensao de primario e secundario. Tipar a potencia como decimal perderia
        // 2500 e publicaria so 2000.
        new EquipmentRow
        {
            NumSerie = "9000003", Tag = "TR-00013", AnoFabricacao = "05/25",
            Potencia = "2000/2500", UnidadePotencia = "kVA", Tensao = "13,800/220",
            Fabricante = "MONTADORA DE EQUIPAMENTOS", VolumeOleo = "1560",
            TipoDeOleo = "Óleo Vegetal Isolante", TipoEquipamento = "TRANSFORMADOR"
        },
        // Marcadores no lugar do valor, a classe das 57 linhas com hifen na potencia e das 166
        // com hifen no ano. Publicar "-" na coluna parece um valor.
        new EquipmentRow
        {
            NumSerie = "9000004", Tag = "TR-00014", AnoFabricacao = "-",
            Potencia = "-", UnidadePotencia = "kVA", Tensao = "-",
            Fabricante = "FABRICANTE DE TRANSFORMADORES", VolumeOleo = "-",
            TipoDeOleo = "Óleo Mineral Lubrificante", TipoEquipamento = "TAMBOR"
        },
        // A classe das 12 linhas que trazem a serie e mais nada.
        new EquipmentRow
        {
            NumSerie = "9000005", Tag = "", AnoFabricacao = "",
            Potencia = "", UnidadePotencia = "", Tensao = "",
            Fabricante = "", VolumeOleo = "",
            TipoDeOleo = "", TipoEquipamento = ""
        },
        // A classe das 7 linhas com unidade e sem potencia. A unidade orfa nao vira potencia.
        new EquipmentRow
        {
            NumSerie = "9000006", Tag = "TR-00016", AnoFabricacao = "2001",
            Potencia = "", UnidadePotencia = "KVAr", Tensao = "525",
            Fabricante = "FABRICANTE DE REATORES", VolumeOleo = "15350",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "REATOR"
        },
        // A forma medida de 111.659/10: tres linhas, duas com tipos quase iguais e uma quase
        // vazia que ainda assim afirma um terceiro tipo. Tres afirmacoes distintas derrubam so
        // o tipo, e o resto da placa fica de pe.
        new EquipmentRow
        {
            NumSerie = "9000007", Tag = "TR-00017", AnoFabricacao = "2014",
            Potencia = "55", UnidadePotencia = "MVAr", Tensao = "288,67",
            Fabricante = "FABRICANTE DE REATORES", VolumeOleo = "14700",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "REATOR"
        },
        new EquipmentRow
        {
            NumSerie = "9000007", Tag = "TR-00017", AnoFabricacao = "2014",
            Potencia = "55", UnidadePotencia = "MVAr", Tensao = "288,67",
            Fabricante = "FABRICANTE DE REATORES", VolumeOleo = "14700",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "REATOR MONOFÁSICO"
        },
        new EquipmentRow
        {
            NumSerie = "9000007", Tag = "", AnoFabricacao = "",
            Potencia = "", UnidadePotencia = "", Tensao = "",
            Fabricante = "", VolumeOleo = "",
            TipoDeOleo = "", TipoEquipamento = "REATOR DE DERIVAÇÃO"
        },
        // Linha sem numero de serie, a classe das 2733. Ela nao casa com equipamento nenhum, e
        // casar por vazio publicaria esta placa em todo equipamento enderecado pelo protocolo.
        new EquipmentRow
        {
            NumSerie = "", Tag = "TR-00018", AnoFabricacao = "1981",
            Potencia = "4000", UnidadePotencia = "kVA", Tensao = "13,8",
            Fabricante = "FABRICANTE DE TRANSFORMADORES", VolumeOleo = "1560",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "TRANSFORMADOR"
        },
        // Serie escrita com marcador, a classe das 14 linhas com '-', 'S/N', '*' e '#'.
        new EquipmentRow
        {
            NumSerie = "S/N", Tag = "TR-00019", AnoFabricacao = "1990",
            Potencia = "300", UnidadePotencia = "kVA", Tensao = "13,8",
            Fabricante = "MONTADORA DE EQUIPAMENTOS", VolumeOleo = "400",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "TRANSFORMADOR"
        }
    ];

    public Task<IReadOnlyList<EquipmentNameplate>> GetNameplatesOfAsync(
        IReadOnlyCollection<string> serialNumbers,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(serialNumbers);

        return Task.FromResult(EquipmentRegistryReading.NameplatesOf(Rows, serialNumbers));
    }

    public Task<EquipmentNameplate?> GetNameplateOfAsync(
        string serialNumber,
        CancellationToken cancellationToken)
    {
        EquipmentNameplate? found = EquipmentRegistryReading
            .NameplatesOf(Rows, new[] { serialNumber })
            .FirstOrDefault();

        return Task.FromResult(found);
    }

    /// <summary>
    /// Expoe a amostra para a suite poder afirmar sobre as combinacoes escritas sem repetir a
    /// tabela dentro do teste.
    /// </summary>
    public static IReadOnlyList<EquipmentRow> ReadWrittenSample()
    {
        return Rows;
    }
}
