using Portal.Domain.Pipeline;

namespace Portal.Domain.Quotes;

/// <summary>
/// Um orcamento e a etapa que o laboratorio alcancou nele, quando alcancou.
///
/// <see cref="LaboratoryStage"/> nula nao quer dizer inicio da progressao: quer dizer que
/// esta fonte nao tem nada a dizer sobre este orcamento, e que a etapa a mostrar e a do
/// lado comercial. Os dois lados viajam juntos de proposito, em vez de o laboratorio
/// sobrescrever <see cref="Quote.Stage"/>: a etapa que a fonte comercial escreveu continua
/// sendo a unica coisa que explica um orcamento que o laboratorio nao conhece.
/// </summary>
public sealed record QuoteWithLaboratoryStage(Quote Quote, PipelineStage? LaboratoryStage);
