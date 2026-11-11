namespace Portal.Api.Nameplate;

/// <summary>
/// Configuracao da fonte do cadastro de equipamentos.
///
/// <see cref="Source"/> ausente ou vazia vale <see cref="NotConnectedSource"/>, como em
/// <c>LaboratoryProgress:Source</c> e diferente de <c>LabResults:Source</c>, que derruba a
/// inicializacao quando falta. Os dois casos nao tem o mesmo risco. Sem fonte de ensaios a
/// tela de indicadores nao tem dado nenhum, e subir assim e servir uma tela vazia como se
/// fosse um laudo. Sem cadastro, quatro campos da listagem voltam a ser o que eram antes
/// desta fatia: "Nao informado". O padrao e de falha fechada, e o pior que ele produz e
/// nenhuma placa.
///
/// Valor escrito errado continua derrubando a inicializacao. Um padrao que absorvesse tambem
/// o erro de digitacao esconderia a fatia desligada num deployment que pensa que a ligou.
/// </summary>
public sealed class EquipmentRegistryOptions
{
    public const string SectionName = "EquipmentRegistry";

    public const string NotConnectedSource = "not-connected";
    public const string SqlServerSource = "sql-server";

    public string? Source { get; init; }
}
