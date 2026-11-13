using Portal.Domain.Nameplate.Exceptions;

namespace Portal.Domain.Nameplate;

/// <summary>
/// A regra que decide o que a placa afirma quando o cadastro tem mais de uma linha para a
/// mesma serie. E uma regra so, e ela resolve dois problemas que pareciam diferentes:
/// <b>um campo e conhecido quando as linhas daquela serie concordam num unico valor nao
/// vazio para ele.</b> Vazio por falta de preenchimento e vazio por contradicao produzem a
/// mesma ausencia, porque para quem le a tela sao a mesma coisa: o cadastro nao diz.
///
/// A serie nao e chave e nao ha como desempatar. Medido nas 20575 linhas: 17725 series
/// distintas, 58 series ambiguas cobrindo 118 linhas, e 49 ainda ambiguas depois de
/// descartar linha byte a byte identica. A tabela nao tem <c>id</c> e nao tem data, entao nao
/// existe criterio de "linha mais recente": escolher a primeira e escolher dado errado em
/// silencio, parte das vezes. Exemplos reais: a serie <c>045472</c> aparece com
/// <c>Potencia</c> <c>37,500</c> e <c>35.500</c>, que sao 37,5 e 35,5, numeros diferentes; a
/// serie <c>1.110.009/4</c> aparece com <c>Tensao</c> <c>525</c> e <c>13,8</c> na mesma TAG.
///
/// Descartar a serie ambigua inteira tambem seria caro, e nao e necessario. Medido nas 49: o
/// fabricante concorda em 44, o tipo de oleo em 46, o volume de oleo em 43, a unidade em 42,
/// o ano em 41, a potencia em 41, a tensao em 36, o tipo em 29 e a TAG em 17. Concordar campo
/// a campo recusa so o campo que a fonte contradiz, e e exatamente o que a tela pede, porque
/// a tela anuncia ausencia campo a campo.
///
/// Campo vazio numa linha nao contradiz: ele se abstem. Vazio quer dizer "o cadastro nao
/// diz", e nao "o cadastro diz outra coisa". Medido: a serie <c>257373</c> tem duas linhas,
/// uma com unidade, tipo de oleo e tipo de equipamento em branco e a outra com os tres
/// preenchidos; tratar branco como divergencia jogaria fora tres campos bons por nada.
///
/// A comparacao e textual, com <see cref="StringComparer.Ordinal"/>, e nao dobra caixa nem
/// normaliza numero. Dobrar caixa nao recupera campo nenhum nas 49, medido, e fazer
/// <c>111</c> concordar com <c>111,00</c> exigiria interpretar a potencia como decimal, que
/// as formas com barra proibem. O preco e conhecido e pequeno: algumas series perdem um campo
/// que duas linhas escreveram com grafia diferente, e nenhuma ganha um campo errado.
/// </summary>
public static class NameplateAgreement
{
    /// <summary>
    /// A placa de uma serie, ou nenhuma quando o cadastro nao tem linha para ela ou quando as
    /// linhas que tem nao sustentam nenhum campo.
    /// </summary>
    public static EquipmentNameplate? Of(
        string serialNumber,
        IEnumerable<EquipmentNameplate> candidates)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(serialNumber);
        ArgumentNullException.ThrowIfNull(candidates);

        string wanted = serialNumber.Trim();
        List<EquipmentNameplate> received = [.. candidates];

        if (received.Any(candidate => !string.Equals(
            candidate.SerialNumber, wanted, StringComparison.Ordinal)))
            throw new NameplateOfAnotherSerialNumberException();

        if (received.Count == 0) return null;

        EquipmentNameplate agreed = new(
            wanted,
            AgreedOn(received, candidate => candidate.Tag),
            AgreedOn(received, candidate => candidate.ManufacturingYear),
            AgreedPowerOf(received),
            AgreedOn(received, candidate => candidate.Voltage),
            AgreedOn(received, candidate => candidate.Manufacturer),
            AgreedOn(received, candidate => candidate.OilVolume),
            AgreedOn(received, candidate => candidate.OilType),
            AgreedOn(received, candidate => candidate.EquipmentType));

        return agreed.SaysNothing ? null : agreed;
    }

    /// <summary>
    /// Um valor quando as linhas que falam daquele campo falam a mesma coisa, e nada quando
    /// falam coisas diferentes. Linha que nao fala nao conta, e por isso a contagem e das
    /// afirmacoes distintas e nunca das linhas.
    /// </summary>
    private static string? AgreedOn(
        IReadOnlyList<EquipmentNameplate> candidates,
        Func<EquipmentNameplate, string?> readField)
    {
        List<string> stated = [.. candidates
            .Select(readField)
            .OfType<string>()
            .Distinct(StringComparer.Ordinal)];

        return stated.Count == 1 ? stated[0] : null;
    }

    /// <summary>
    /// A potencia concorda pelo valor, e a unidade acompanha o valor. Divergencia de unidade
    /// com valor concordante deixa a potencia sem unidade, em vez de derrubar a potencia:
    /// quanto o equipamento tem continua sendo um fato, e e a unidade que ficou em disputa.
    /// Unidade sozinha nunca sobrevive, porque <see cref="PowerRating"/> nao existe sem valor.
    /// </summary>
    private static PowerRating? AgreedPowerOf(IReadOnlyList<EquipmentNameplate> candidates)
    {
        string? value = AgreedOn(candidates, candidate => candidate.Power?.Value);

        return value is null
            ? null
            : new PowerRating(value, AgreedOn(candidates, candidate => candidate.Power?.Unit));
    }
}
