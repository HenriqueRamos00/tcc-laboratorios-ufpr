namespace Portal.Domain.LabResults;

/// <summary>
/// O que o laudo mediu, do ponto de vista de quem vai abrir a tela de indicadores. Nao e
/// uma coluna do AutoLAB: a view de dados traz itens medidos, e o tipo sai de quais itens medidos o
/// protocolo trouxe.
/// </summary>
public enum ReportAnalysisType
{
    PhysicalChemical,
    Chromatography
}
