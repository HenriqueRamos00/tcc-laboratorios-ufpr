using Portal.Api.Pipeline;
using Portal.Domain.Quotes;

namespace Portal.Api.Quotes;

/// <summary>
/// O corpo de um orcamento, campo por campo igual ao que
/// <c>frontend/src/app/model/quote.ts</c> ja le, mais <see cref="LaboratoryStage"/>.
///
/// A rota servia a entidade de dominio direto. Agora ha um corpo declarado, porque a tela
/// passa a receber um campo que a entidade comercial nao tem e que nenhum dos tres
/// adaptadores de orcamento sabe preencher.
///
/// A mudanca e aditiva: os doze campos anteriores saem com o mesmo nome, o mesmo tipo e o
/// mesmo valor. <see cref="CreatedDate"/> continua <c>DateTimeOffset</c> e nao passa por
/// <c>WireDate</c>, que serve data sem hora: trocar o formato aqui quebraria a leitura que
/// a tela ja faz do ISO 8601 com deslocamento que o Salesforce manda.
///
/// <see cref="LaboratoryStage"/> e anulavel e viaja mesmo nula, porque o conversor da
/// aplicacao declara <c>JsonIgnoreCondition.Never</c>. Campo omitido e campo que a tela nao
/// consegue distinguir de campo que ela nao conhece.
/// </summary>
public sealed record QuoteResponse
{
    public required string Id { get; init; }

    public required string OpportunityId { get; init; }

    public required string Code { get; init; }

    public required string Name { get; init; }

    public required string Status { get; init; }

    /// <summary>
    /// A etapa como a fonte comercial escreveu, em texto livre. Continua no corpo porque e
    /// a unica coisa que explica um orcamento que o laboratorio nao conhece, e porque a
    /// tela ainda reconhece a metade comercial da progressao a partir dela.
    /// </summary>
    public required string Stage { get; init; }

    public required string Description { get; init; }

    public required string CompanyName { get; init; }

    public required string ExternalContactName { get; init; }

    public required string ExternalContactEmail { get; init; }

    public required decimal TotalPrice { get; init; }

    public DateTimeOffset? CreatedDate { get; init; }

    /// <summary>
    /// O degrau da progressao que o laboratorio alcancou, na chave que a tela usa. Nulo
    /// quando o AutoLAB nao tem nada a dizer sobre este orcamento, o que inclui o orcamento
    /// que nao entrou no laboratorio, o orcamento cujas amostras estao em pes diferentes e
    /// o deployment em que a fonte nao esta conectada.
    /// </summary>
    public string? LaboratoryStage { get; init; }

    public static QuoteResponse From(QuoteWithLaboratoryStage quote)
    {
        ArgumentNullException.ThrowIfNull(quote);

        return new QuoteResponse
        {
            Id = quote.Quote.Id,
            OpportunityId = quote.Quote.OpportunityId,
            Code = quote.Quote.Code,
            Name = quote.Quote.Name,
            Status = quote.Quote.Status,
            Stage = quote.Quote.Stage,
            Description = quote.Quote.Description,
            CompanyName = quote.Quote.CompanyName,
            ExternalContactName = quote.Quote.ExternalContactName,
            ExternalContactEmail = quote.Quote.ExternalContactEmail,
            TotalPrice = quote.Quote.TotalPrice,
            CreatedDate = quote.Quote.CreatedDate,
            LaboratoryStage = PipelineStageWireName.Of(quote.LaboratoryStage)
        };
    }
}
