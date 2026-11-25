using System.Net.Http.Headers;

using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Authorization;
using Microsoft.Extensions.Options;
using Portal.Adapters.BackendAuth;
using Portal.Api.Configuration;

using Portal.Api.Authentication;
using Portal.Application.Authentication;

namespace Portal.Api.Controllers;

[ApiController]
[Route("auth")]
public sealed class AuthController(
    ILoginUseCase loginUseCase, IClientPasswordRecovery passwordRecovery,
    PortalOptions source, ILogger<AuthController> logger) : ControllerBase
{
    [HttpPost("login")]
    [AllowAnonymous]
    [NoStore]
    [ProducesResponseType<LoginResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status400BadRequest)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status429TooManyRequests)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status502BadGateway)]
    public async Task<IActionResult> Login(LoginRequest request, CancellationToken cancellationToken)
    {
        try
        {
            ClientSession? session = await loginUseCase.LoginAsync(
                request.Email, request.Password, cancellationToken);

            if (session is null)
                return this.AsProblem(
                    StatusCodes.Status401Unauthorized, "invalid-credentials",
                    "Credenciais inválidas.", "Confira seu e-mail e senha.");

            return Ok(new LoginResponse(session.AccessToken, session.TokenType, session.ExpiresIn, ClientUserResponse.From(session.User)));
        }
        catch (LoginRequestRejectedException)
        {
            return this.AsProblem(
                StatusCodes.Status400BadRequest, "invalid-login-request",
                "Dados de login inválidos.", "Confira seu e-mail e senha e tente novamente.");
        }
        catch (LoginCooldownException cooldown)
        {
            Response.Headers.RetryAfter = cooldown.RetryAfterSeconds.ToString(System.Globalization.CultureInfo.InvariantCulture);
            return this.AsProblem(429, "login-cooldown", "Login temporariamente bloqueado.",
                "Aguarde o período informado antes de tentar novamente.");
        }
        catch (AuthenticationSourceUnavailableException failure)
        {
            logger.LogWarning(failure, "A fonte de autenticação está indisponível.");
            return this.AsProblem(
                StatusCodes.Status502BadGateway, "authentication-source-unavailable",
                "Login indisponível.", "Não foi possível entrar no momento. Tente novamente em instantes.");
        }
    }

    [HttpPost("recoveries")]
    [AllowAnonymous]
    [NoStore]
    [ProducesResponseType(StatusCodes.Status202Accepted)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status400BadRequest)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status502BadGateway)]
    public async Task<IActionResult> RequestRecovery(
        PasswordRecoveryRequest request, CancellationToken cancellationToken)
    {
        try
        {
            await passwordRecovery.RequestAsync(request.Email, cancellationToken);
            return Accepted();
        }
        catch (AuthenticationSourceUnavailableException failure)
        {
            logger.LogWarning(failure, "A fonte de recuperação de senha está indisponível.");
            return this.AsProblem(StatusCodes.Status502BadGateway,
                "password-recovery-source-unavailable", "Recuperação indisponível.",
                "Não foi possível solicitar a recuperação agora. Tente novamente em instantes.");
        }
    }

    [HttpPost("password-resets")]
    [AllowAnonymous]
    [NoStore]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status400BadRequest)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status502BadGateway)]
    public async Task<IActionResult> ResetPassword(
        PasswordResetRequest request, CancellationToken cancellationToken)
    {
        try
        {
            bool changed = await passwordRecovery.ResetAsync(
                request.Token, request.Password, cancellationToken);
            if (!changed)
                return this.AsProblem(StatusCodes.Status400BadRequest,
                    "invalid-password-reset", "Não foi possível redefinir a senha.",
                    "O link pode ter expirado, já ter sido usado ou a senha não atender aos requisitos.");
            return NoContent();
        }
        catch (AuthenticationSourceUnavailableException failure)
        {
            logger.LogWarning(failure, "A fonte de recuperação de senha está indisponível.");
            return this.AsProblem(StatusCodes.Status502BadGateway,
                "password-recovery-source-unavailable", "Recuperação indisponível.",
                "Não foi possível redefinir a senha agora. Tente novamente em instantes.");
        }
    }

    [HttpPost("confirmations")]
    [AllowAnonymous]
    [NoStore]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status400BadRequest)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status501NotImplemented)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status502BadGateway)]
    public async Task<IActionResult> ConfirmEmail(
        EmailConfirmationRequest request, CancellationToken cancellationToken)
    {
        IClientEmailConfirmation? confirmation =
            HttpContext.RequestServices.GetService<IClientEmailConfirmation>();
        if (confirmation is null)
            return this.AsProblem(StatusCodes.Status501NotImplemented,
                "email-confirmation-not-available", "Confirmação indisponível.",
                "Esta operação não está disponível para a fonte de autenticação configurada.");

        try
        {
            bool confirmed = await confirmation.ConfirmAsync(request.Token, cancellationToken);
            if (!confirmed)
                return this.AsProblem(StatusCodes.Status400BadRequest,
                    "invalid-email-confirmation", "Não foi possível confirmar o e-mail.",
                    "O link pode ter expirado ou já ter sido utilizado.");
            return NoContent();
        }
        catch (AuthenticationSourceUnavailableException failure)
        {
            logger.LogWarning(failure, "A fonte de confirmação de e-mail está indisponível.");
            return this.AsProblem(StatusCodes.Status502BadGateway,
                "email-confirmation-source-unavailable", "Confirmação indisponível.",
                "Não foi possível confirmar o e-mail agora. Tente novamente em instantes.");
        }
    }

    [HttpPost("refresh")]
    [AllowAnonymous]
    [NoStore]
    [ProducesResponseType<LoginResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status501NotImplemented)]
    public async Task<IActionResult> Refresh()
    {
        PortalJwtService? jwt = HttpContext.RequestServices.GetService<PortalJwtService>();
        IOptions<BackendAuthOptions>? backend = HttpContext.RequestServices.GetService<IOptions<BackendAuthOptions>>();

        if (source.DataSource != PortalOptions.LactecSource || jwt is null || backend is null)
            return this.AsProblem(501, "session-renewal-not-available", "Renovação de sessão indisponível.",
                "Esta operação está disponível no modo lactec.");

        bool presented = AuthenticationHeaderValue.TryParse(Request.Headers.Authorization.ToString(), out var header)
            && string.Equals(header.Scheme, "Bearer", StringComparison.OrdinalIgnoreCase)
            && !string.IsNullOrWhiteSpace(header.Parameter);
        ClientSession? renewed = presented ? await jwt.RenewAsync(header!.Parameter!, backend.Value.ServiceCode) : null;

        if (renewed is null)
        {
            Response.Headers.WWWAuthenticate = presented ? "Bearer error=\"invalid_token\"" : "Bearer";
            return this.AsProblem(StatusCodes.Status401Unauthorized, "session-expired",
                "Sessão expirada.", "Entre novamente para continuar.");
        }

        return Ok(new LoginResponse(renewed.AccessToken, renewed.TokenType, renewed.ExpiresIn, ClientUserResponse.From(renewed.User)));
    }

    [HttpGet("me")]
    [NoStore]
    [ProducesResponseType<ClientUserResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status501NotImplemented)]
    public IActionResult Me()
    {
        if (source.DataSource != PortalOptions.LactecSource)
            return this.AsProblem(501, "current-user-not-available", "Consulta de sessão indisponível.",
                "Esta operação está disponível no modo lactec.");
        return Ok(ClientUserResponse.From(PortalJwtService.ReadUser(User)));
    }

}
