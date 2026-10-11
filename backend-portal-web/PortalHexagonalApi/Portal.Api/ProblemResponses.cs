using Microsoft.AspNetCore.Mvc;

namespace Portal.Api;

/// <summary>
/// O unico lugar que monta corpo de erro, para a API inteira: ensaios e orcamentos. As treze linhas que montavam um
/// <see cref="ProblemDetails"/> estavam copiadas byte a byte nos dois controllers, e
/// copia de corpo de erro diverge em silencio: basta um dos dois ganhar um campo novo.
///
/// O titulo e o detalhe sao campos distintos na RFC 9457 e deixaram de ser a mesma frase:
/// o titulo nomeia a classe do problema e e estavel, o detalhe fala daquela requisicao. O
/// <c>type</c> da a cada codigo um endereco proprio, e o <c>traceId</c> liga a resposta a
/// linha de log, que e o que falta quando o cliente reclama de um 502 sem saber quando.
/// </summary>
internal static class ProblemResponses
{
    private const string ProblemTypePrefix = "https://portal.lactec.org.br/problemas/";

    public static IActionResult AsProblem(
        this ControllerBase controller,
        int status,
        string code,
        string title,
        string detail)
    {
        ProblemDetails problem = new()
        {
            Status = status,
            Type = ProblemTypePrefix + code,
            Title = title,
            Detail = detail
        };

        problem.Extensions["code"] = code;
        // `HttpContext` e nulo num controller construido fora do pipeline, que e como um
        // teste de unidade o constroi. Sem a interrogacao, montar um corpo de erro ali morre
        // com NullReferenceException e esconde a falha que estava sendo medida.
        string? trace = controller.HttpContext?.TraceIdentifier;

        if (trace is not null) problem.Extensions["traceId"] = trace;

        return controller.StatusCode(status, problem);
    }

    public static IActionResult UnreadableEquipmentIdentifier(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status400BadRequest,
            "invalid-identifier",
            "Identificador ilegivel.",
            "O identificador do equipamento no endereco nao pode ser lido.");
    }

    public static IActionResult UnreadableReportIdentifier(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status400BadRequest,
            "invalid-identifier",
            "Identificador ilegivel.",
            "O identificador do laudo no endereco nao pode ser lido.");
    }

    public static IActionResult TenantNotIdentified(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status401Unauthorized,
            "tenant-not-identified",
            "Inquilino nao identificado.",
            "A requisicao nao diz por qual empresa se pergunta.");
    }

    /// <summary>
    /// Um equipamento inexistente e um equipamento de outro cliente respondem a mesma coisa.
    /// Distinguir os dois casos contaria a quem perguntou que aquele numero de serie existe
    /// na base de alguem.
    /// </summary>
    public static IActionResult EquipmentWithoutAnalysis(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status404NotFound,
            "equipment-without-analysis",
            "Equipamento sem analise.",
            "Nenhuma analise foi encontrada para este equipamento.");
    }

    /// <summary>
    /// O item medido fora da lista de itens conhecidos viaja com a mensagem da excecao porque
    /// ela e vocabulario de laboratorio: nomeia o item medido que o laudo trouxe e a lista
    /// nao conhece, e e disso que quem atende precisa. A falha de servico externo viaja com
    /// frase fixa: a mensagem dela vem da infraestrutura e pode carregar nome de servidor e
    /// endereco interno, que nao sao assunto de quem fez a requisicao. A mensagem continua
    /// inteira no log.
    /// </summary>
    public static IActionResult LabTestSourceUnavailable(this ControllerBase controller, Exception failure)
    {
        bool outsideTheKnownList = failure is Portal.Domain.LabResults.Exceptions.ResultNameOutsideKnownListException;

        return controller.AsProblem(
            StatusCodes.Status502BadGateway,
            outsideTheKnownList ? "measuredItem-outside-catalog" : "lab-test-source-unavailable",
            outsideTheKnownList ? "Item medido fora da lista de itens conhecidos." : "Fonte de ensaios indisponivel.",
            outsideTheKnownList
                ? failure.Message
                : "A fonte de ensaios laboratoriais nao respondeu a esta consulta.");
    }

    public static IActionResult QuoteSourceUnavailable(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status502BadGateway,
            "quote-source-unavailable",
            "Fonte de orcamentos indisponivel.",
            "A fonte de orcamentos nao respondeu a esta consulta.");
    }

    /// <summary>
    /// Separada da fonte indisponivel porque manda procurar em outro lugar: aqui a consulta
    /// respondeu e foi o arquivo do documento que nao pode ser lido.
    /// </summary>
    public static IActionResult QuoteDocumentUnavailable(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status502BadGateway,
            "quote-document-unavailable",
            "Documento do orcamento indisponivel.",
            "O orcamento existe, mas o documento dele nao pode ser lido.");
    }

    public static IActionResult QuoteRequestRefused(this ControllerBase controller, string detail)
    {
        return controller.AsProblem(
            StatusCodes.Status400BadRequest,
            "quote-request-refused",
            "Consulta de orcamento recusada.",
            detail);
    }

    public static IActionResult QuoteNotFound(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status404NotFound,
            "quote-not-found",
            "Orcamento nao encontrado.",
            "Nenhum orcamento com este identificador.");
    }

    public static IActionResult QuoteDocumentNotFound(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status404NotFound,
            "quote-document-not-found",
            "Documento nao encontrado.",
            "Este orcamento nao tem documento publicado.");
    }
}
