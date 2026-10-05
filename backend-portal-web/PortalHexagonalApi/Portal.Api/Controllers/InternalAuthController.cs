using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Portal.Api.Errors;
using Portal.Application.Internal;
using Portal.Application.Internal.Auth;
using Portal.Api.Models;

namespace Portal.Api.Controllers;

[ApiController]
[Route("api/auth/internal")]
[Produces("application/json")]
public sealed class InternalAuthController : ControllerBase
{
    private readonly InternalAuthService _internalAuthService;

    public InternalAuthController(InternalAuthService internalAuthService)
    {
        _internalAuthService = internalAuthService;
    }

    [AllowAnonymous]
    [HttpPost("login")]
    [ProducesResponseType(typeof(InternalLoginResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status502BadGateway)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status503ServiceUnavailable)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status504GatewayTimeout)]
    public async Task<ActionResult<InternalLoginResponse>> Login(
        [FromBody] InternalLoginRequest request,
        CancellationToken cancellationToken)
    {
        BackendLabResult<InternalLoginResponse> result =
            await _internalAuthService.LoginAsync(request, cancellationToken);

        if (!result.IsSuccess) return InternalApiFailureMapper.ToActionResult(this, result.Failure!);
        return Ok(result.Value);
    }
}
