using Microsoft.AspNetCore.Mvc;

using Portal.Api.Authentication;
using Portal.Api.Configuration;
using Portal.Api.LabResults;
using Portal.Api.RealTime;
using Portal.Application.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Nameplate.Exceptions;
using Portal.Application.ReportProjection;
using Portal.Application.Visibility;

namespace Portal.Api.Controllers;

[ApiController]
[NoStore]
[Route("api/equipments")]
public sealed class EquipmentStatusController : ControllerBase
{
    private readonly IForConsultingLabResults _labResults;
    private readonly IForConsultingEquipmentStatus _statuses;
    private readonly IReportLoad _load;
    private readonly LabResultsOptions _labResultsOptions;
    private readonly PortalOptions _portalOptions;
    private readonly ReportLoadOptions _loadOptions;

    public EquipmentStatusController(
        IForConsultingLabResults labResults,
        IForConsultingEquipmentStatus statuses,
        IReportLoad load,
        LabResultsOptions labResultsOptions,
        PortalOptions portalOptions,
        ReportLoadOptions loadOptions)
    {
        _loadOptions = loadOptions;
        _labResults = labResults;
        _statuses = statuses;
        _load = load;
        _labResultsOptions = labResultsOptions;
        _portalOptions = portalOptions;
    }

    [HttpGet("{id}/status")]
    [ProducesResponseType(typeof(EquipmentStatusResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<IActionResult> Get(string id, CancellationToken cancellationToken)
    {
        if (!IsAvailable) return this.EquipmentStatusUnavailable();

        if (!OpaqueIdentifier.TryDecode(id, out string equipmentKey))
            return this.UnreadableEquipmentIdentifier();

        CompanyScope? scope = CompanyScopeResolution.Resolve(User);

        if (scope is null) return this.TenantNotIdentified();

        EquipmentStatus? status = await StatusOfAsync(scope, equipmentKey, cancellationToken);

        return status is null ? NoContent() : Ok(EquipmentStatusResponse.From(status));
    }

    [HttpPost("{id}/status/refresh")]
    [ProducesResponseType(typeof(EquipmentStatusRefreshResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status502BadGateway)]
    public async Task<IActionResult> Refresh(string id, CancellationToken cancellationToken)
    {
        if (!IsAvailable) return this.EquipmentStatusUnavailable();

        if (!OpaqueIdentifier.TryDecode(id, out string equipmentKey))
            return this.UnreadableEquipmentIdentifier();

        CompanyScope? scope = CompanyScopeResolution.Resolve(User);
        string? userKey = RealTimeUser.KeyOf(User);

        if (scope is null || userKey is null) return this.TenantNotIdentified();

        try
        {
            if (await _labResults.GetEquipmentAsync(scope, equipmentKey, cancellationToken) is null)
                return this.EquipmentWithoutAnalysis();
        }
        catch (Exception failure) when (failure is LabTestSourceUnavailableException
            or NameplateSourceUnavailableException)
        {
            return this.LabTestSourceUnavailable();
        }

        ReportLoadResult result = await _load.RefreshEquipmentAsync(scope, equipmentKey, userKey, cancellationToken);

        EquipmentStatus? status = result.AlreadyRunning
            ? null
            : await StatusOfAsync(scope, equipmentKey, cancellationToken);

        return Ok(new EquipmentStatusRefreshResponse
        {
            AlreadyRunning = result.AlreadyRunning,
            Success = result.Success,
            ReportsRead = result.ReportsRead,
            ReportsUnreadable = result.ReportsUnreadable,
            Status = status is null ? null : EquipmentStatusResponse.From(status)
        });
    }

    [HttpPost("status/refresh")]
    [ProducesResponseType(typeof(EquipmentStatusAcceptedResponse), StatusCodes.Status202Accepted)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<IActionResult> RefreshVisible(
        [FromBody] EquipmentStatusBatchRequest request,
        CancellationToken cancellationToken)
    {
        if (!IsAvailable) return this.EquipmentStatusUnavailable();

        if (request.Ids is not { Count: > 0 } ids || ids.Count > _loadOptions.MaxEquipmentPerBatch)
            return this.TooManyEquipment(_loadOptions.MaxEquipmentPerBatch);

        List<string> equipmentKeys = [];

        foreach (string id in ids)
        {
            if (!OpaqueIdentifier.TryDecode(id, out string equipmentKey))
                return this.UnreadableEquipmentIdentifier();

            equipmentKeys.Add(equipmentKey);
        }

        CompanyScope? scope = CompanyScopeResolution.Resolve(User);
        string? userKey = RealTimeUser.KeyOf(User);

        if (scope is null || userKey is null) return this.TenantNotIdentified();

        return AcceptedOf(await _load.RequestEquipmentAsync(scope, equipmentKeys, userKey, cancellationToken));
    }

    [HttpPost("status/sync")]
    [ProducesResponseType(typeof(EquipmentStatusAcceptedResponse), StatusCodes.Status202Accepted)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public IActionResult RefreshCompanies()
    {
        if (!IsAvailable) return this.EquipmentStatusUnavailable();

        CompanyScope? scope = CompanyScopeResolution.Resolve(User);
        string? userKey = RealTimeUser.KeyOf(User);

        if (scope is null || userKey is null) return this.TenantNotIdentified();

        return AcceptedOf(_load.RequestCompanies(scope, userKey));
    }

    private IActionResult AcceptedOf(ReportLoadAcceptance acceptance)
    {
        return StatusCode(StatusCodes.Status202Accepted, new EquipmentStatusAcceptedResponse
        {
            Accepted = acceptance.Accepted,
            AlreadyRunning = acceptance.AlreadyRunning
        });
    }

    private bool IsAvailable =>
        _load.IsAvailable
        && _labResultsOptions.Source == LabResultsOptions.SqlServerSource
        && _portalOptions.DataSource != PortalOptions.BackendLabSource;

    private async Task<EquipmentStatus?> StatusOfAsync(
        CompanyScope scope,
        string equipmentKey,
        CancellationToken cancellationToken)
    {
        IReadOnlyDictionary<string, EquipmentStatus> found = await _statuses
            .OfEquipmentAsync(scope, [equipmentKey.Trim()], cancellationToken);

        return found.GetValueOrDefault(equipmentKey.Trim());
    }
}
