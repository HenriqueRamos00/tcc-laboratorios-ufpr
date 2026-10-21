using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Authorization;
using Portal.Api.Configuration;

using Portal.Api.Authentication;
using Portal.Application.Authentication;

namespace Portal.Api.Controllers;

[ApiController]
[Route("auth")]
public sealed class AuthController(
    ILoginUseCase loginUseCase, PortalOptions source, ILogger<AuthController> logger) : ControllerBase
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
