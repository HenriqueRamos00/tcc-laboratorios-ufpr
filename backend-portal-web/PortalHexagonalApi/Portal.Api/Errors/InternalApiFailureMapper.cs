using Microsoft.AspNetCore.Mvc;
using Portal.Api.Models;
using Portal.Application.Internal;

namespace Portal.Api.Errors;

public static class InternalApiFailureMapper
{
    public static ObjectResult ToActionResult(ControllerBase controller, BackendLabFailure failure)
    {
        if (failure.WwwAuthenticate is not null)
            controller.Response.Headers.WWWAuthenticate = failure.WwwAuthenticate;

        int status = failure.Kind switch
        {
            BackendLabFailureKind.InvalidInput => StatusCodes.Status400BadRequest,
            BackendLabFailureKind.Unauthorized => StatusCodes.Status401Unauthorized,
            BackendLabFailureKind.Forbidden => StatusCodes.Status403Forbidden,
            BackendLabFailureKind.NotFound => StatusCodes.Status404NotFound,
            BackendLabFailureKind.Conflict => StatusCodes.Status409Conflict,
            BackendLabFailureKind.InvalidResponse => StatusCodes.Status502BadGateway,
            BackendLabFailureKind.Unavailable => StatusCodes.Status503ServiceUnavailable,
            BackendLabFailureKind.Timeout => StatusCodes.Status504GatewayTimeout,
            _ => StatusCodes.Status502BadGateway
        };

        return controller.StatusCode(status, new InternalApiError(failure.Message));
    }
}
