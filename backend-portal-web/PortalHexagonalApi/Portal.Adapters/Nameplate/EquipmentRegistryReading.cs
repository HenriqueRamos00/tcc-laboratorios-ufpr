using System.Diagnostics.CodeAnalysis;

using Portal.Domain.Nameplate;

namespace Portal.Adapters.Nameplate;

/// <summary>
/// O unico lugar do sistema que conhece como o cadastro do AutoLAB escreve. Traduzir a
/// escrita da fonte para o vocabulario do portal e trabalho de adaptador: "um hifen na coluna
/// de potencia quer dizer nada" e um fato sobre o preenchimento daquela tabela, e se ele
/// morasse no dominio o dominio passaria a saber que existe um banco do outro lado.
///
/// Quem decide o que a placa afirma nao e este arquivo: e <see cref="NameplateAgreement"/>,
/// no dominio. Aqui so se le o que cada coluna escreveu. O corte e esse de proposito: a
/// concordancia campo a campo e regra de negocio e e testavel sem fonte nenhuma; a lista de
/// marcadores e escrita da fonte e muda quando a fonte muda. E o mesmo corte de
/// <c>SituaProcessReading</c> com <c>ReachedStage</c>, e por isso e o padrao da casa.
///
/// O casamento e pela serie inteira, aparada nas pontas e com distincao de caixa, nunca por
/// prefixo nem por parte. Existe <c>5233</c> e existe <c>52330</c>, existe <c>111.594/1</c> e
/// existe <c>111.594/10</c>: casar por prefixo fundiria equipamentos distintos, e aqui isso
/// significaria publicar a placa de um transformador na linha de outro.
/// </summary>
public static class EquipmentRegistryReading
{
    /// <summary>
    /// O que a fonte escreve quando nao tem o dado. Vazio e nulo sao os casos obvios; os
    /// marcadores sao medidos. Na coluna de serie: <c>-</c> em 9 linhas, <c>S/N</c> em 3,
    /// <c>*</c> em 1 e <c>#</c> em 1, alem de 2733 em branco. Em <c>Potencia</c>: <c>-</c> em
    /// 57 linhas e <c>NA</c> em 2. Em <c>Tensao</c>: <c>-</c> em 60. Em <c>VolumeOleo</c> e em
    /// <c>AnoFabricacao</c>: <c>-</c> em 66 e em 166.
    ///
    /// Um marcador publicado seria pior que a ausencia: a tela imprimiria "-" na coluna de
    /// potencia, que parece um valor, em vez de "Nao informado", que e a verdade. E, na coluna
    /// de serie, um marcador casaria todas as linhas que o usam num equipamento so.
    /// </summary>
    private static readonly string[] MarkersThatMeanNothing = ["-", "S/N", "#", "*", "NA"];

    /// <summary>
    /// A placa das series pedidas, uma por serie. Linha que nao foi pedida nao e lida, e serie
    /// pedida que nenhuma linha carrega nao aparece na resposta.
    /// </summary>
    public static IReadOnlyList<EquipmentNameplate> NameplatesOf(
        IEnumerable<EquipmentRow> rows,
        IReadOnlyCollection<string> serialNumbers)
    {
        ArgumentNullException.ThrowIfNull(rows);
        ArgumentNullException.ThrowIfNull(serialNumbers);

        HashSet<string> wanted = new(
            serialNumbers
                .Where(serialNumber => !MeansNothing(serialNumber))
                .Select(serialNumber => serialNumber.Trim()),
            StringComparer.Ordinal);

        if (wanted.Count == 0) return [];

        return [.. rows
            .Where(row => wanted.Contains(SerialNumberOf(row)))
            .GroupBy(SerialNumberOf, StringComparer.Ordinal)
            .Select(rowsOfOneSerialNumber => NameplateAgreement.Of(
                rowsOfOneSerialNumber.Key,
                [.. rowsOfOneSerialNumber.Select(NameplateWrittenIn)]))
            .OfType<EquipmentNameplate>()];
    }

    /// <summary>
    /// O que uma linha escreveu, sem conciliar com ninguem. Exposta porque a leitura das
    /// colunas e a parte desta fatia que mais precisa de teste proprio: cada coluna tem a
    /// classe do valor escrito, a classe do vazio e a classe do marcador, e e nas duas
    /// ultimas que o erro mora.
    /// </summary>
    public static EquipmentNameplate NameplateWrittenIn(EquipmentRow row)
    {
        ArgumentNullException.ThrowIfNull(row);

        string? power = Stated(row.Potencia);

        return new EquipmentNameplate(
            SerialNumberOf(row),
            Stated(row.Tag),
            Stated(row.AnoFabricacao),
            power is null ? null : new PowerRating(power, Stated(row.UnidadePotencia)),
            Stated(row.Tensao),
            Stated(row.Fabricante),
            Stated(row.VolumeOleo),
            Stated(row.TipoDeOleo),
            Stated(row.TipoEquipamento));
    }

    private static string SerialNumberOf(EquipmentRow row)
    {
        return Stated(row.NumSerie) ?? string.Empty;
    }

    private static string? Stated(string? writtenInTheSource)
    {
        return MeansNothing(writtenInTheSource) ? null : writtenInTheSource.Trim();
    }

    /// <summary>
    /// A anotacao de fluxo nao e decoracao: ela e o que deixa <see cref="Stated"/> aparar as
    /// pontas sem operador de supressao. Falso aqui quer dizer que a celula tem conteudo, e o
    /// compilador passa a saber disso.
    /// </summary>
    private static bool MeansNothing([NotNullWhen(false)] string? writtenInTheSource)
    {
        return string.IsNullOrWhiteSpace(writtenInTheSource)
            || MarkersThatMeanNothing.Contains(
                writtenInTheSource.Trim(),
                StringComparer.OrdinalIgnoreCase);
    }
}
