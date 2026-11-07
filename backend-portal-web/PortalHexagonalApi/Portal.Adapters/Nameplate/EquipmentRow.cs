namespace Portal.Adapters.Nameplate;

/// <summary>
/// Uma linha de <c>select * from Equipamentos</c>, com os nomes das colunas da consulta.
/// Mora no adaptador porque e vocabulario da fonte, nao do dominio, e os nomes ficam como o
/// AutoLAB escreve porque traduzir nome de coluna faz perder o rastro ate o banco.
///
/// Sao dez colunas e nada mais. Nao existe <c>id</c>, nao existe data e nao existe coluna de
/// empresa, e as tres ausencias tem consequencia: sem <c>id</c> e sem data nao ha criterio de
/// linha mais recente para desempatar serie repetida, e sem empresa esta consulta nao sabe
/// filtrar por inquilino, o que e o motivo de a porta dela nao receber inquilino.
///
/// As dez sao texto anulavel, inclusive as que parecem numero. <c>Potencia</c> tem 14 linhas
/// com barra, que sao enrolamentos multiplos, e 57 com <c>-</c>; <c>Tensao</c> tem 643 com
/// barra, que sao primario e secundario; <c>AnoFabricacao</c> tem 1531 linhas fora do formato
/// de quatro digitos; <c>VolumeOleo</c> tem <c>1L</c> e <c>6 KG</c> no meio dos numeros.
/// Tipar qualquer uma como numero descarta a linha ou, pior, guarda metade dela.
/// </summary>
public sealed record EquipmentRow
{
    public string? NumSerie { get; init; }

    public string? Tag { get; init; }

    public string? AnoFabricacao { get; init; }

    public string? Potencia { get; init; }

    public string? Tensao { get; init; }

    public string? Fabricante { get; init; }

    public string? VolumeOleo { get; init; }

    public string? UnidadePotencia { get; init; }

    public string? TipoDeOleo { get; init; }

    public string? TipoEquipamento { get; init; }
}
