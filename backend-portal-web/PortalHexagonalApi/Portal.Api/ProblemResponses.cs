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

    public static IActionResult InvalidPage(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status400BadRequest,
            "invalid-page",
            "Pagina invalida.",
            "Os parametros page e pageSize precisam ser inteiros: page de 1 a 100000 e pageSize de 1 a 200.");
    }

    public static IActionResult ReportDocumentNotFound(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status404NotFound,
            "report-document-not-found",
            "Laudo assinado nao encontrado.",
            "Nenhum PDF assinado foi encontrado para este laudo.");
    }

    public static IActionResult ReportDocumentSourceUnavailable(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status502BadGateway,
            "report-document-source-unavailable",
            "Fonte de laudos assinados indisponivel.",
            "A fonte dos laudos assinados nao respondeu a esta consulta.");
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
    /// A falha de servico externo viaja com frase fixa: a mensagem dela vem da infraestrutura
    /// e pode carregar nome de servidor e endereco interno, que nao sao assunto de quem fez a
    /// requisicao. A mensagem continua inteira no log.
    /// </summary>
    public static IActionResult LabTestSourceUnavailable(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status502BadGateway,
            "lab-test-source-unavailable",
            "Fonte de ensaios indisponivel.",
            "A fonte de ensaios laboratoriais nao respondeu a esta consulta.");
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
    /// A fonte que informa a etapa do laboratorio e outra, e o corpo de erro diz qual caiu.
    /// Reaproveitar aqui o codigo da fonte de orcamentos mandaria quem atende olhar o
    /// Salesforce quando quem nao respondeu foi o AutoLAB, que e exatamente o que uma
    /// excecao unica para as duas fontes fazia antes.
    /// </summary>
    public static IActionResult LaboratoryProgressSourceUnavailable(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status502BadGateway,
            "laboratory-progress-source-unavailable",
            "Fonte de etapas do laboratorio indisponivel.",
            "A fonte que informa a etapa do laboratorio nao respondeu a esta consulta.");
    }

    public static IActionResult QuoteAuthenticationRequired(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status401Unauthorized,
            "quote-authentication-required",
            "Autenticação necessária.",
            "Entre novamente para consultar os orçamentos.");
    }

    public static IActionResult QuoteAccessForbidden(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status403Forbidden,
            "quote-access-forbidden",
            "Acesso ao orçamento proibido.",
            "O usuário autenticado não tem acesso a este orçamento.");
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
