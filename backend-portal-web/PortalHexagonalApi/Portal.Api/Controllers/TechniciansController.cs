using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Portal.Api.Errors;
using Portal.Application.Internal;
using Portal.Application.Internal.Technicians;
using Portal.Api.Security;
using Portal.Api.Models;

namespace Portal.Api.Controllers;

[ApiController]
[Route("api/technicians")]
[Produces("application/json")]
[Authorize(Policy = InternalSecurity.AdminPolicy)]
public sealed class TechniciansController : ControllerBase
{
    private readonly TechniciansService _techniciansService;

    public TechniciansController(TechniciansService techniciansService)
    {
        _techniciansService = techniciansService;
    }

    [HttpGet]
    [ProducesResponseType(typeof(IReadOnlyList<Technician>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status502BadGateway)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status503ServiceUnavailable)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status504GatewayTimeout)]
    public async Task<ActionResult<IReadOnlyList<Technician>>> List(
        [FromQuery] string? search,
        [FromQuery] TechnicianStatus? status,
        CancellationToken cancellationToken)
    {
        BackendLabResult<IReadOnlyList<Technician>> result =
            await _techniciansService.ListAsync(search, status, cancellationToken);

        if (!result.IsSuccess) return InternalApiFailureMapper.ToActionResult(this, result.Failure!);
        return Ok(result.Value);
    }

    [HttpPost]
    [ProducesResponseType(typeof(Technician), StatusCodes.Status201Created)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status409Conflict)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status502BadGateway)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status503ServiceUnavailable)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status504GatewayTimeout)]
    public async Task<ActionResult<Technician>> Create(
        [FromBody] CreateTechnicianRequest request,
        CancellationToken cancellationToken)
    {
        BackendLabResult<Technician> result =
            await _techniciansService.CreateAsync(request, cancellationToken);

        if (!result.IsSuccess) return InternalApiFailureMapper.ToActionResult(this, result.Failure!);
        Technician technician = result.Value!;
        return Created(new Uri($"/api/technicians/{technician.Id}", UriKind.Relative), technician);
    }

    [HttpPut("{id:long}")]
    [ProducesResponseType(typeof(Technician), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status409Conflict)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status502BadGateway)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status503ServiceUnavailable)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status504GatewayTimeout)]
    public async Task<ActionResult<Technician>> Update(
        long id,
        [FromBody] UpdateTechnicianRequest request,
        CancellationToken cancellationToken)
    {
        BackendLabResult<Technician> result =
            await _techniciansService.UpdateAsync(id, request, cancellationToken);

        if (!result.IsSuccess) return InternalApiFailureMapper.ToActionResult(this, result.Failure!);
        return Ok(result.Value);
    }

    [HttpPatch("{id:long}/status")]
    [ProducesResponseType(typeof(Technician), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status502BadGateway)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status503ServiceUnavailable)]
    [ProducesResponseType(typeof(InternalApiError), StatusCodes.Status504GatewayTimeout)]
    public async Task<ActionResult<Technician>> ChangeStatus(
        long id,
        [FromBody] ChangeTechnicianStatusRequest request,
        CancellationToken cancellationToken)
    {
        BackendLabResult<Technician> result =
            await _techniciansService.ChangeStatusAsync(id, request.Status!.Value, cancellationToken);

        if (!result.IsSuccess) return InternalApiFailureMapper.ToActionResult(this, result.Failure!);
        return Ok(result.Value);
    }
}
