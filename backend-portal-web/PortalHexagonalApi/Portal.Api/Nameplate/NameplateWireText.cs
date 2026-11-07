using Portal.Domain.Nameplate;

namespace Portal.Api.Nameplate;

/// <summary>
/// O que a placa vira no corpo HTTP. Mora no adaptador condutor porque o corpo e assunto
/// dele, e nao do dominio: o dominio nao sabe que existe JSON.
///
/// Oito dos nove campos atravessam como o cadastro escreveu, sem traducao e sem unidade
/// acrescentada, e por isso nao tem funcao aqui: mapeamento trivial nao merece uma camada. So
/// a potencia e montada, porque ela e duas colunas e uma frase.
///
/// A tensao em particular sai como texto cru, sem unidade, e isso e decisao e nao esquecimento.
/// O cadastro nao tem coluna de unidade de tensao, e 643 linhas trazem duas tensoes numa so
/// (<c>13,800/220</c>), que sao o primario e o secundario. Acrescentar "kV" seria o portal
/// escolhendo a unidade, e a propria tabela mostra que ninguem ali e cuidadoso com unidade:
/// <c>kV</c> aparece 5 vezes dentro de <c>UnidadePotencia</c>, que e coluna de potencia.
/// </summary>
public static class NameplateWireText
{
    /// <summary>
    /// A potencia com a unidade que o cadastro escreveu, e so o numero quando ele nao escreveu
    /// unidade. Sao 100 linhas com potencia e sem unidade, e completar com "kVA", que e a
    /// unidade mais comum com 12257 linhas, faria o portal afirmar uma grandeza que ninguem
    /// registrou: a mesma tabela tem MVA em 2167 linhas, e a diferenca entre as duas e de mil
    /// vezes.
    /// </summary>
    public static string? PowerOf(EquipmentNameplate? nameplate)
    {
        PowerRating? power = nameplate?.Power;

        if (power is null) return null;

        return power.Unit is null ? power.Value : $"{power.Value} {power.Unit}";
    }
}
