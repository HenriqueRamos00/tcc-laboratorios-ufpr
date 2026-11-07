using Portal.Domain.Nameplate.Exceptions;

namespace Portal.Domain.Nameplate;

/// <summary>
/// A placa de um equipamento, como o cadastro do AutoLAB a conhece. Nove campos, todos
/// opcionais, e nenhum nome de coluna da fonte: quem traduz
/// <c>select * from Equipamentos</c> para isto e o adaptador, e por isso o dominio nao sabe
/// que do outro lado existe um SQL Server.
///
/// Todo campo e anulavel porque o cadastro medido nao preenche nenhum deles sempre: nas
/// 20575 linhas, <c>NumSerie</c> vem em 86%, <c>Tag</c> em 55%, <c>AnoFabricacao</c> em
/// 62%, <c>Potencia</c> em 72%, <c>Tensao</c> em 76%, <c>Fabricante</c> em 77%,
/// <c>VolumeOleo</c> em 61%, <c>UnidadePotencia</c> em 72%, <c>TipoDeOleo</c> em 97% e
/// <c>TipoEquipamento</c> em 96%. A unidade de ausencia e o campo, nunca o equipamento: uma
/// placa que so existisse inteira apagaria o fabricante de um equipamento por falta do ano
/// de fabricacao.
///
/// Nada aqui e numero, e nao e descuido. <c>Potencia</c> tem 14 linhas com barra, que sao
/// enrolamentos multiplos (<c>2000/2500</c>, <c>3/3/2,5</c>), e tipar como decimal perde o
/// valor inteiro; <c>Tensao</c> tem 643 linhas com barra (<c>13,800/220</c>), que sao
/// primario e secundario; <c>AnoFabricacao</c> tem 1531 linhas que nao sao quatro digitos
/// (<c>05/25</c>, <c>01/03/2025</c>). Texto e a unica forma que carrega o que a fonte
/// escreveu sem escolher uma metade.
///
/// <see cref="EquipmentType"/> nao e enumeracao e nao passa por tabela de traducao. O
/// cadastro tem 73 valores distintos, entre eles pares quase iguais (<c>TRANSFORMADOR</c>
/// com 14526 linhas e <c>TRANSFORMADOR DE POTENCIA</c> com 490) e itens que nem sao
/// transformador (<c>TAMBOR</c> com 802, <c>MANCAL</c>, <c>TANQUE</c>). Fundir os pares
/// faria o portal afirmar uma classificacao que o laboratorio nao fez.
/// </summary>
public sealed class EquipmentNameplate
{
    public EquipmentNameplate(
        string serialNumber,
        string? tag,
        string? manufacturingYear,
        PowerRating? power,
        string? voltage,
        string? manufacturer,
        string? oilVolume,
        string? oilType,
        string? equipmentType)
    {
        if (string.IsNullOrWhiteSpace(serialNumber)) throw new NameplateWithoutSerialNumberException();

        SerialNumber = serialNumber.Trim();
        Tag = tag;
        ManufacturingYear = manufacturingYear;
        Power = power;
        Voltage = voltage;
        Manufacturer = manufacturer;
        OilVolume = oilVolume;
        OilType = oilType;
        EquipmentType = equipmentType;
    }

    /// <summary>
    /// A unica coisa que nunca falta, porque e por ela que a placa encontra o equipamento.
    /// O casamento e com <c>TestedEquipment.SerialNumber</c>, nunca com
    /// <c>TestedEquipment.EquipmentKey</c>: a chave cai no protocolo Lactec quando a serie
    /// vem em branco, e protocolo nao e numero de serie. Medido nas seis series da amostra
    /// embutida contra as 20575 linhas do cadastro: as quatro series verdadeiras casam, e as
    /// duas que nao casam sao exatamente as duas que este projeto inventou.
    /// </summary>
    public string SerialNumber { get; }

    /// <summary>
    /// A TAG de placa, que e o identificador que o cliente pintou no equipamento. E dado do
    /// proprio cliente e pode carregar nome de empresa: dez linhas do cadastro trazem nome de
    /// cliente real dentro de <c>Tag</c> e de <c>NumSerie</c>. Em producao isso e o dado dele
    /// e aparece na tela dele; em amostra e em mensagem de excecao, nunca.
    /// </summary>
    public string? Tag { get; }

    public string? ManufacturingYear { get; }

    public PowerRating? Power { get; }

    public string? Voltage { get; }

    public string? Manufacturer { get; }

    public string? OilVolume { get; }

    public string? OilType { get; }

    public string? EquipmentType { get; }

    /// <summary>
    /// Verdadeiro quando o cadastro conhece a serie e nao diz mais nada sobre ela. Existe
    /// porque o caso e real: 12 linhas trazem a serie e as nove outras colunas vazias. Uma
    /// placa assim nao vira placa, e quem pergunta recebe ausencia, para a tela nao anunciar
    /// "cadastro encontrado" e em seguida imprimir "Nao informado" sete vezes.
    /// </summary>
    public bool SaysNothing =>
        Tag is null
        && ManufacturingYear is null
        && Power is null
        && Voltage is null
        && Manufacturer is null
        && OilVolume is null
        && OilType is null
        && EquipmentType is null;
}
