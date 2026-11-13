using Portal.Application.Visibility;

namespace Portal.Application.LabResults;

/// <summary>
/// O que a tela de indicadores de saude pede. O protocolo selecionado e opcional porque a
/// visao geral nao e feita de uma analise so.
/// </summary>
public sealed record HealthIndicatorsQuery(
    VisibilityScope Scope,
    string EquipmentKey,
    string? SelectedProtocoloLactec);
