using Microsoft.AspNetCore.Mvc;

using Portal.Api.Authentication;
using Portal.Application.Authentication;

namespace Portal.Api.Controllers;

[ApiController]
[Route("auth")]
public sealed class AuthController(
    ILoginUseCase loginUseCase, ILogger<AuthController> logger) : ControllerBase
{
    [HttpPost("login")]
    [ResponseCache(NoStore = true, Location = ResponseCacheLocation.None)]
    [ProducesResponseType<LoginResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status400BadRequest)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status501NotImplemented)]
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

            return Ok(new LoginResponse(session.AccessToken, session.TokenType, session.ExpiresIn, session.User));
        }
        catch (LoginRequestRejectedException)
        {
            return this.AsProblem(
                StatusCodes.Status400BadRequest, "invalid-login-request",
                "Dados de login inválidos.", "Confira seu e-mail e senha e tente novamente.");
        }
        catch (AuthenticationNotAvailableException)
        {
            return this.AsProblem(
                StatusCodes.Status501NotImplemented, "authentication-not-available",
                "Login indisponível.", "A autenticação nesta fonte ainda não está disponível.");
        }
        catch (AuthenticationSourceUnavailableException failure)
        {
            logger.LogWarning(failure, "A fonte de autenticação está indisponível.");
            return this.AsProblem(
                StatusCodes.Status502BadGateway, "authentication-source-unavailable",
                "Login indisponível.", "Não foi possível entrar no momento. Tente novamente em instantes.");
        }
    }
}
