using Microsoft.AspNetCore.Mvc;

using Portal.Api.LabResults;
using Portal.Api.Configuration;
using Portal.Application.LabResults.Exceptions;
using Portal.Domain.LabResults.Exceptions;
using Portal.Application.LabResults;
using Portal.Domain.LabResults;

namespace Portal.Api.Controllers;

/// <summary>
/// Os dois caminhos que o frontend ja promete no JSDoc do servico de saude. O controller e
/// adaptador condutor: resolve endereco, resolve inquilino, chama a porta condutora e
/// achata a resposta. Nenhuma regra de laudo passa por aqui.
/// </summary>
[ApiController]
[Route("api/equipments")]
public sealed class HealthIndicatorsController : ControllerBase
{
    private readonly IForConsultingLabResults _labResults;
    private readonly LabResultsOptions _labResultsOptions;
    private readonly PortalOptions _portalOptions;

    public HealthIndicatorsController(
        IForConsultingLabResults labResults,
        LabResultsOptions labResultsOptions,
        PortalOptions portalOptions)
    {
        _labResults = labResults;
        _labResultsOptions = labResultsOptions;
        _portalOptions = portalOptions;
    }

    [HttpGet("{id}/health")]
    [ProducesResponseType(typeof(HealthIndicatorsResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public Task<IActionResult> GetIndicators(string id, CancellationToken cancellationToken)
    {
        return AnswerAsync(id, null, cancellationToken);
    }

    [HttpGet("{id}/health/reports/{reportId}")]
    [ProducesResponseType(typeof(HealthIndicatorsResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public Task<IActionResult> GetIndicatorsOfAnalysis(
        string id,
        string reportId,
        CancellationToken cancellationToken)
    {
        return AnswerAsync(id, reportId, cancellationToken);
    }

    private async Task<IActionResult> AnswerAsync(
        string id,
        string? reportId,
        CancellationToken cancellationToken)
    {
        if (!OpaqueIdentifier.TryDecode(id, out string equipmentKey))
            return this.UnreadableEquipmentIdentifier();

        string? selectedProtocol = null;

        if (reportId is not null && !OpaqueIdentifier.TryDecode(reportId, out selectedProtocol))
            return this.UnreadableReportIdentifier();

        string? tenant = TenantResolution.Resolve(User, _labResultsOptions, _portalOptions);

        if (tenant is null) return this.TenantNotIdentified();

        try
        {
            SampleHealthIndicators? indicators = await _labResults.GetHealthIndicatorsAsync(
                new HealthIndicatorsQuery(tenant, equipmentKey, selectedProtocol),
                cancellationToken);

            return indicators is null
                || (_portalOptions is { UseDemoData: true, DataSource: PortalOptions.LactecSource }
                    && selectedProtocol is not null && indicators.SelectedAnalysis is null)
                ? this.EquipmentWithoutAnalysis()
                : Ok(PositionalFlattening.Flatten(indicators, id));
        }
        catch (Exception failure) when (failure is ResultNameOutsideKnownListException or LabTestSourceUnavailableException)
        {
            return this.LabTestSourceUnavailable(failure);
        }
    }
}
