using System.Text.Json;
using System.Text.Json.Serialization;

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Portal.Api.Authentication;
using Portal.Api.Configuration;
using Portal.Application.Authentication;

namespace Portal.Api.Controllers;

[ApiController]
[Route("api/profile")]
[NoStore]
public sealed class ProfileController(
    IClientProfileOperations profiles,
    PortalOptions source,
    ILogger<ProfileController> logger) : ControllerBase
{
    [HttpGet]
    [ProducesResponseType<ClientProfile>(StatusCodes.Status200OK)]
    public Task<IActionResult> Get(CancellationToken cancellationToken) => AnswerAsync(async () =>
        Ok(await profiles.GetAsync(AccountId(), cancellationToken)));

    [HttpPatch]
    [ProducesResponseType<ClientProfile>(StatusCodes.Status200OK)]
    public Task<IActionResult> Update([FromBody] ProfileUpdateRequest request,
        CancellationToken cancellationToken)
    {
        if (request.Name is null || request.ExtraFields is { Count: > 0 })
            return Task.FromResult(this.AsProblem(400, "invalid-profile", "Dados de perfil inválidos.",
                "Envie somente nome e campos pessoais disponíveis para esta conta."));

        return AnswerAsync(async () => Ok(await profiles.UpdateAsync(AccountId(),
            new ClientProfileUpdate(request.Name, request.Phone, request.Position), cancellationToken)));
    }

    [HttpPost("password")]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    public Task<IActionResult> ChangePassword([FromBody] ChangePasswordRequest request,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.CurrentPassword)
            || string.IsNullOrWhiteSpace(request.NewPassword))
            return Task.FromResult(this.AsProblem(400, "invalid-password", "Senha inválida.",
                "Informe a senha atual e uma nova senha válida."));

        return AnswerAsync(async () =>
        {
            await profiles.ChangePasswordAsync(AccountId(), request.CurrentPassword,
                request.NewPassword, cancellationToken);
            return NoContent();
        });
    }

    [HttpPost("email-change")]
    [ProducesResponseType<ClientPendingEmailChange>(StatusCodes.Status202Accepted)]
    public Task<IActionResult> RequestEmailChange([FromBody] EmailChangeRequest request,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.NewEmail)
            || string.IsNullOrWhiteSpace(request.CurrentPassword))
            return Task.FromResult(this.AsProblem(400, "invalid-profile", "Dados inválidos.",
                "Informe o novo e-mail e a senha atual."));

        return AnswerAsync(async () => Accepted(await profiles.RequestEmailChangeAsync(
            AccountId(), request.NewEmail, request.CurrentPassword, cancellationToken)));
    }

    [HttpDelete("email-change")]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    public Task<IActionResult> CancelEmailChange(CancellationToken cancellationToken) => AnswerAsync(async () =>
    {
        await profiles.CancelEmailChangeAsync(AccountId(), cancellationToken);
        return NoContent();
    });

    [HttpPost("email-change/confirm")]
    [AllowAnonymous]
    [ProducesResponseType<EmailChangeConfirmation>(StatusCodes.Status200OK)]
    public Task<IActionResult> ConfirmEmailChange([FromBody] EmailChangeConfirmationRequest request,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Token))
            return Task.FromResult(this.AsProblem(400, "invalid-email-change-token", "Link inválido.",
                "Solicite uma nova alteração de e-mail e use um dos links recebidos."));

        return AnswerAsync(async () => Ok(await profiles.ConfirmEmailChangeAsync(
            request.Token, cancellationToken)));
    }

    private string AccountId()
    {
        // A fonte Spring resolve a identidade pelo Bearer original encaminhado pelo adapter.
        // O identificador só é lido do JWT C# no modo Lactec, nunca do corpo da requisição.
        if (source.DataSource == PortalOptions.BackendLabSource)
            return string.Empty;
        return User.FindFirst("sub")?.Value
            ?? throw new ProfileOperationException("session-expired", StatusCodes.Status401Unauthorized);
    }

    private async Task<IActionResult> AnswerAsync(Func<Task<IActionResult>> action)
    {
        try
        {
            return await action();
        }
        catch (ProfileOperationException failure)
        {
            if (failure.StatusCode == StatusCodes.Status401Unauthorized)
                Response.Headers.WWWAuthenticate = "Bearer";
            if (failure.RetryAfterSeconds is > 0)
                Response.Headers.RetryAfter = failure.RetryAfterSeconds.Value.ToString(
                    System.Globalization.CultureInfo.InvariantCulture);
            return this.AsProblem(failure.StatusCode, failure.Code,
                TitleFor(failure.Code), DetailFor(failure.Code));
        }
        catch (AuthenticationSourceUnavailableException failure)
        {
            logger.LogWarning(failure, "A fonte do perfil está indisponível.");
            return this.AsProblem(StatusCodes.Status502BadGateway, "profile-source-unavailable",
                "Perfil indisponível.", "Não foi possível consultar a conta agora. Tente novamente.");
        }
    }

    private static string TitleFor(string code) => code switch
    {
        "current-password-incorrect" => "Senha atual incorreta.",
        "invalid-password" => "Senha inválida.",
        "invalid-profile" => "Dados de perfil inválidos.",
        "invalid-email-change-token" => "Link inválido ou expirado.",
        "email-already-in-use" => "E-mail já cadastrado.",
        "email-change-conflict" => "Solicitação de alteração conflitante.",
        "rate-limit-exceeded" => "Limite de solicitações atingido.",
        "email-delivery-unavailable" => "Envio de e-mail indisponível.",
        "action-not-available" => "Ação indisponível.",
        "session-expired" => "Sessão expirada.",
        "forbidden" => "Acesso negado.",
        _ => "Não foi possível concluir a operação."
    };

    private static string DetailFor(string code) => code switch
    {
        "current-password-incorrect" => "Confira a senha atual e tente novamente.",
        "invalid-password" => "A nova senha não atende à política da conta.",
        "invalid-profile" => "Confira os campos informados e tente novamente.",
        "invalid-email-change-token" => "Solicite uma nova alteração e use um dos links recebidos.",
        "email-already-in-use" => "Use um endereço que ainda não esteja associado a outra conta.",
        "email-change-conflict" => "Solicite novamente a alteração de e-mail.",
        "rate-limit-exceeded" => "Aguarde o período informado antes de solicitar outra alteração.",
        "email-delivery-unavailable" => "A alteração de e-mail não está disponível neste momento.",
        "action-not-available" => "Esta operação não está disponível para a fonte da conta.",
        "session-expired" => "Entre novamente para continuar.",
        "forbidden" => "Sua conta não pode executar esta operação.",
        _ => "Tente novamente em instantes."
    };

    public sealed class ProfileUpdateRequest
    {
        public string? Name { get; init; }
        public string? Phone { get; init; }
        public string? Position { get; init; }

        [JsonExtensionData]
        public Dictionary<string, JsonElement>? ExtraFields { get; init; }
    }

    public sealed record ChangePasswordRequest(string? CurrentPassword, string? NewPassword);
    public sealed record EmailChangeRequest(string? NewEmail, string? CurrentPassword);
    public sealed record EmailChangeConfirmationRequest(string? Token);
}
