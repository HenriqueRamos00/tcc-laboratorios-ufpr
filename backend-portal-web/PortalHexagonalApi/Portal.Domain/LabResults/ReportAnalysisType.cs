namespace Portal.Domain.LabResults;

/// <summary>
/// O que o laudo mediu, do ponto de vista de quem vai abrir a tela de indicadores. Nao e
/// uma coluna do AutoLAB: a view de dados traz itens medidos, e o tipo sai de quais itens medidos o
/// protocolo trouxe. <see cref="Other"/> e o laudo de um ensaio que a tela de indicadores
/// nao mostra, como PCB ou furfuraldeido: o cliente baixa o documento, e so.
/// </summary>
public enum ReportAnalysisType
{
    PhysicalChemical,
    Chromatography,
    Other
}
