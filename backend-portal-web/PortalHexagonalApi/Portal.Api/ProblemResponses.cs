using Microsoft.AspNetCore.Mvc;

using Portal.Application.LabResults;

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

    public static IActionResult InvalidEquipmentSearch(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status400BadRequest,
            "invalid-search",
            "Busca invalida.",
            $"O parametro search aceita no maximo {EquipmentSearch.MaxLength} caracteres.");
    }

    public static IActionResult ReportDocumentSourceUnavailable(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status502BadGateway,
            "report-document-source-unavailable",
            "Fonte de laudos assinados indisponivel.",
            "A fonte dos laudos assinados nao respondeu a esta consulta.");
    }

    public static IActionResult ReportArchiveTooLarge(this ControllerBase controller, int reportCount, int maxReports)
    {
        return controller.AsProblem(
            StatusCodes.Status422UnprocessableEntity,
            "report-archive-too-large",
            "Laudos demais para um arquivo so.",
            $"Este equipamento tem {reportCount} laudos e o arquivo compactado aceita no maximo {maxReports}. Baixe os laudos pelo historico.");
    }

    public static IActionResult TenantNotIdentified(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status401Unauthorized,
            "tenant-not-identified",
            "Sessao nao identificada.",
            "A requisicao nao traz a sessao do cliente que diz por quais empresas se pergunta.");
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
    public static IActionResult LabIntegrationPending(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status501NotImplemented,
            "lab-integration-pending",
            "Consulta de equipamentos e indicadores indisponível.",
            "A consulta de equipamentos e indicadores ainda não está disponível neste ambiente.");
    }

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

    public static IActionResult QuoteRequestRefused(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status400BadRequest,
            "quote-request-refused",
            "Consulta de orcamento recusada.",
            "A consulta de orcamento nao pode ser atendida como foi pedida.");
    }

    public static IActionResult InvalidClientDecision(this ControllerBase controller, string detail)
    {
        return controller.AsProblem(
            StatusCodes.Status400BadRequest,
            "invalid-client-decision",
            "Decisao invalida.",
            detail);
    }

    public static IActionResult ClientDecisionNotAllowed(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status409Conflict,
            "client-decision-not-allowed",
            "Decisao nao permitida.",
            "A etapa atual do orcamento nao aceita esta decisao, ou ela ja foi registrada.");
    }

    public static IActionResult ClientDecisionsNotAvailable(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status501NotImplemented,
            "client-decisions-not-available",
            "Registro de decisoes indisponivel.",
            "Esta forma de decisao nao esta disponivel neste ambiente.");
    }

    public static IActionResult ClientDecisionStoreUnavailable(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status502BadGateway,
            "client-decision-store-unavailable",
            "Banco do portal indisponivel.",
            "Nao foi possivel registrar a decisao agora. Tente novamente em instantes.");
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

    public static IActionResult ReportAuthenticationRequired(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status401Unauthorized,
            "report-authentication-required",
            "Autenticação necessária.",
            "Entre novamente para consultar os relatórios publicados.");
    }

    public static IActionResult ReportAccessForbidden(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status403Forbidden,
            "report-access-forbidden",
            "Acesso ao relatório proibido.",
            "O usuário autenticado não tem acesso a este relatório.");
    }

    public static IActionResult ClientLabRequestRejected(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status400BadRequest,
            "client-lab-request-rejected",
            "Consulta recusada pela fonte de resultados.",
            "Os parâmetros da consulta não são aceitos pela fonte de resultados.");
    }

    public static IActionResult ReportDocumentNotFound(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status404NotFound,
            "report-document-not-found",
            "Relatório não encontrado.",
            "Nenhum relatório publicado com este identificador.");
    }

    public static IActionResult ReportSourceUnavailable(this ControllerBase controller)
    {
        return controller.AsProblem(
            StatusCodes.Status502BadGateway,
            "report-source-unavailable",
            "Fonte de relatórios indisponível.",
            "A fonte de relatórios não respondeu à consulta.");
    }
}
