using Microsoft.AspNetCore.Mvc;

using Portal.Api.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Domain.LabResults.Exceptions;
using Portal.Application.LabResults;
using Portal.Domain.LabResults;

namespace Portal.Api.Controllers;

/// <summary>
/// A listagem de equipamentos e o detalhe de um deles, que as historias HU-05 e HU-06
/// pedem. Como no controller de indicadores, aqui nao mora regra nenhuma de laudo: resolve
/// endereco, resolve inquilino, chama a porta condutora e traduz para o corpo que a tela le.
/// </summary>
[ApiController]
[Route("api/equipments")]
public sealed class EquipmentController : ControllerBase
{
    private readonly IForConsultingLabResults _labResults;
    private readonly LabResultsOptions _labResultsOptions;

    public EquipmentController(
        IForConsultingLabResults labResults,
        LabResultsOptions labResultsOptions)
    {
        _labResults = labResults;
        _labResultsOptions = labResultsOptions;
    }

    [HttpGet]
    [ProducesResponseType(typeof(IReadOnlyList<EquipmentResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public Task<IActionResult> List(CancellationToken cancellationToken)
    {
        return AnswerAsync(async tenant =>
        {
            IReadOnlyList<TestedEquipment> equipmentList = await _labResults
                .ListEquipmentAsync(tenant, cancellationToken);

            return Ok(equipmentList.Select(BuildListingRow));
        });
    }

    [HttpGet("{id}")]
    [ProducesResponseType(typeof(EquipmentDetailResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public Task<IActionResult> Get(string id, CancellationToken cancellationToken)
    {
        return AnswerAboutOneAsync(id, cancellationToken, equipment => Ok(new EquipmentDetailResponse
        {
            Id = OpaqueIdentifier.Encode(equipment.EquipmentKey),
            SerialNumber = equipment.SerialNumber,
            Tag = equipment.DisplayLabel,
            Company = equipment.Company,
            Identification = equipment.MostRecentReport.Identification,
            ReportCount = equipment.Reports.Count,
            MostRecentReportDate = WireDate.From(equipment.MostRecentReport.AnalysisEndDate)
        }));
    }

    [HttpGet("{id}/reports")]
    [ProducesResponseType(typeof(IReadOnlyList<EquipmentReportResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public Task<IActionResult> ListReports(string id, CancellationToken cancellationToken)
    {
        return AnswerAboutOneAsync(id, cancellationToken, equipment =>
            Ok(equipment.Reports.Select(report => new EquipmentReportResponse
            {
                Id = OpaqueIdentifier.Encode(report.ProtocoloLactec),
                Date = WireDate.From(report.AnalysisEndDate),
                AnalysisType = report.AnalysisType,
                Status = report.Status
            })));
    }

    /// <summary>
    /// O preambulo de toda rota desta classe: resolve o inquilino e traduz a falha da fonte.
    /// Estava copiado em cada metodo, e copia de tratamento de erro diverge em silencio,
    /// porque basta uma das copias ganhar um caso novo.
    /// </summary>
    private async Task<IActionResult> AnswerAsync(Func<string, Task<IActionResult>> answer)
    {
        string? tenant = TenantResolution.Resolve(User, _labResultsOptions);

        if (tenant is null) return this.TenantNotIdentified();

        try
        {
            return await answer(tenant);
        }
        catch (Exception failure) when (IsSourceFailure(failure))
        {
            return this.LabTestSourceUnavailable(failure);
        }
    }

    /// <summary>
    /// As duas rotas que falam de um equipamento so fazem a mesma travessia: decodificar o
    /// identificador opaco, achar o equipamento e responder 404 quando nao ha nenhum. O que
    /// muda entre elas e apenas o corpo da resposta.
    /// </summary>
    private Task<IActionResult> AnswerAboutOneAsync(
        string id,
        CancellationToken cancellationToken,
        Func<TestedEquipment, IActionResult> describe)
    {
        return AnswerAsync(async tenant =>
        {
            if (!OpaqueIdentifier.TryDecode(id, out string equipmentKey))
                return this.UnreadableEquipmentIdentifier();

            TestedEquipment? equipment = await _labResults
                .GetEquipmentAsync(tenant, equipmentKey, cancellationToken);

            return equipment is null ? this.EquipmentWithoutAnalysis() : describe(equipment);
        });
    }

    private static EquipmentResponse BuildListingRow(TestedEquipment equipment)
    {
        return new EquipmentResponse
        {
            Id = OpaqueIdentifier.Encode(equipment.EquipmentKey),
            SerialNumber = equipment.SerialNumber,
            Tag = equipment.DisplayLabel,
            Company = equipment.Company,
            Identification = equipment.MostRecentReport.Identification,
            ReportCount = equipment.Reports.Count,
            MostRecentReportDate = WireDate.From(equipment.MostRecentReport.AnalysisEndDate)
        };
    }

    private static bool IsSourceFailure(Exception exception)
    {
        return exception is ResultNameOutsideKnownListException or LabTestSourceUnavailableException;
    }
}
