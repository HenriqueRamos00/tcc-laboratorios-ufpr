using Microsoft.AspNetCore.Mvc;

using Portal.Api.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Nameplate.Exceptions;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.Visibility;
using Portal.Application.LabResults;
using Portal.Domain.LabResults;

namespace Portal.Api.Controllers;

/// <summary>
/// Os dois caminhos que o frontend ja promete no JSDoc do servico de saude. O controller e
/// adaptador condutor: resolve endereco, resolve o escopo, chama a porta condutora e
/// achata a resposta. Nenhuma regra de laudo passa por aqui.
/// </summary>
[ApiController]
[Route("api/equipments")]
public sealed class HealthIndicatorsController : ControllerBase
{
    private readonly IForConsultingLabResults _labResults;
    private readonly LabResultsOptions _labResultsOptions;

    public HealthIndicatorsController(
        IForConsultingLabResults labResults,
        LabResultsOptions labResultsOptions)
    {
        _labResults = labResults;
        _labResultsOptions = labResultsOptions;
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
        if (_labResultsOptions.Source == LabResultsOptions.NotConnectedSource)
            return this.LabIntegrationPending();

        if (!OpaqueIdentifier.TryDecode(id, out string equipmentKey))
            return this.UnreadableEquipmentIdentifier();

        string? selectedProtocol = null;

        if (reportId is not null && !OpaqueIdentifier.TryDecode(reportId, out selectedProtocol))
            return this.UnreadableReportIdentifier();

        VisibilityScope? scope = VisibilityScopeResolution.Resolve(User, _labResultsOptions);

        if (scope is null) return this.TenantNotIdentified();

        try
        {
            SampleHealthIndicators? indicators = await _labResults.GetHealthIndicatorsAsync(
                new HealthIndicatorsQuery(scope, equipmentKey, selectedProtocol),
                cancellationToken);

            return indicators is null
                ? this.EquipmentWithoutAnalysis()
                : Ok(PositionalFlattening.Flatten(indicators, id));
        }
        catch (ReportDocumentSourceUnavailableException)
        {
            return this.ReportDocumentSourceUnavailable();
        }
        catch (Exception failure) when (failure is LabTestSourceUnavailableException
            or NameplateSourceUnavailableException)
        {
            return this.LabTestSourceUnavailable();
        }
    }
}
