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
    ILoginUseCase loginUseCase, PortalOptions source, ILogger<AuthController> logger,
    IClientProfileOperations profiles, IClientSessionStateVerifier? sessionVerifier = null) : ControllerBase
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

    [HttpPost("refresh")]
    [AllowAnonymous]
    [NoStore]
    [ProducesResponseType<LoginResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status501NotImplemented)]
    public async Task<IActionResult> Refresh(CancellationToken cancellationToken)
    {
        PortalJwtService? jwt = HttpContext.RequestServices.GetService<PortalJwtService>();
        IOptions<BackendAuthOptions>? backend = HttpContext.RequestServices.GetService<IOptions<BackendAuthOptions>>();

        if (source.DataSource != PortalOptions.LactecSource || jwt is null || backend is null
            || sessionVerifier is null)
            return this.AsProblem(501, "session-renewal-not-available", "Renovação de sessão indisponível.",
                "Esta operação está disponível no modo lactec.");

        bool presented = AuthenticationHeaderValue.TryParse(Request.Headers.Authorization.ToString(), out var header)
            && string.Equals(header.Scheme, "Bearer", StringComparison.OrdinalIgnoreCase)
            && !string.IsNullOrWhiteSpace(header.Parameter);
        ClientSession? renewed;
        try
        {
            renewed = presented
                ? await jwt.RenewAsync(header!.Parameter!, backend.Value.ServiceCode,
                    sessionVerifier, profiles, cancellationToken)
                : null;
        }
        catch (AuthenticationSourceUnavailableException failure)
        {
            logger.LogWarning(failure, "A fonte não respondeu ao renovar a sessão.");
            return this.AsProblem(502, "profile-source-unavailable", "Renovação indisponível.",
                "Não foi possível validar a sessão agora. Tente novamente.");
        }
        catch (ProfileOperationException failure) when (failure.StatusCode == StatusCodes.Status401Unauthorized)
        {
            renewed = null;
        }

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
    public async Task<IActionResult> Me(CancellationToken cancellationToken)
    {
        if (source.DataSource != PortalOptions.LactecSource)
            return this.AsProblem(501, "current-user-not-available", "Consulta de sessão indisponível.",
                "Esta operação está disponível no modo lactec.");

        ClientUser user = PortalJwtService.ReadUser(User);
        try
        {
            ClientProfile profile = await profiles.GetAsync(user.Id, cancellationToken);
            return Ok(ClientUserResponse.From(user with { Name = profile.Name, Email = profile.Email }));
        }
        catch (ProfileOperationException failure) when (failure.StatusCode == StatusCodes.Status401Unauthorized)
        {
            Response.Headers.WWWAuthenticate = "Bearer";
            return this.AsProblem(401, "session-expired", "Sessão expirada.",
                "Entre novamente para continuar.");
        }
        catch (AuthenticationSourceUnavailableException failure)
        {
            logger.LogWarning(failure, "A fonte não respondeu ao consultar a sessão atual.");
            return this.AsProblem(502, "profile-source-unavailable", "Consulta de sessão indisponível.",
                "Não foi possível consultar a conta agora. Tente novamente.");
        }
    }

}
