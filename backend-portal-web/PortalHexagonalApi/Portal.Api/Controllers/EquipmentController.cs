using Microsoft.AspNetCore.Mvc;

using Portal.Api.LabResults;
using Portal.Api.Nameplate;
using Portal.Api.ReportDocuments;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Nameplate.Exceptions;
using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;
using Portal.Domain.Nameplate;

namespace Portal.Api.Controllers;

/// <summary>
/// A listagem de equipamentos e o detalhe de um deles, que as historias HU-05 e HU-06
/// pedem. Como no controller de indicadores, aqui nao mora regra nenhuma de laudo: resolve
/// endereco, resolve o escopo, chama a porta condutora e traduz para o corpo que a tela le.
/// </summary>
[ApiController]
[Route("api/equipments")]
public sealed class EquipmentController : ControllerBase
{
    private readonly IForConsultingLabResults _labResults;
    private readonly IForDownloadingSignedReports _signedReports;
    private readonly LabResultsOptions _labResultsOptions;

    public EquipmentController(
        IForConsultingLabResults labResults,
        IForDownloadingSignedReports signedReports,
        LabResultsOptions labResultsOptions)
    {
        _labResults = labResults;
        _signedReports = signedReports;
        _labResultsOptions = labResultsOptions;
    }

    [HttpGet]
    [ProducesResponseType(typeof(IReadOnlyList<EquipmentResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public Task<IActionResult> List(
        [FromQuery] PageQuery paging,
        CancellationToken cancellationToken)
    {
        if (!paging.TryToRequest(out PageRequest request))
            return Task.FromResult(this.InvalidPage());

        return AnswerAsync(async scope =>
        {
            Page<EquipmentWithNameplate> equipmentPage = await _labResults
                .ListEquipmentAsync(scope, request, cancellationToken);

            PageQuery.WriteHeaders(Response, equipmentPage);

            return Ok(equipmentPage.Items.Select(BuildListingRow));
        });
    }

    [HttpGet("{id}")]
    [ProducesResponseType(typeof(EquipmentDetailResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public Task<IActionResult> Get(string id, CancellationToken cancellationToken)
    {
        return AnswerAboutOneAsync(id, cancellationToken, found =>
        {
            TestedEquipment equipment = found.Equipment;
            EquipmentNameplate? nameplate = found.Nameplate;

            return Ok(new EquipmentDetailResponse
            {
                Id = OpaqueIdentifier.Encode(equipment.EquipmentKey),
                SerialNumber = equipment.SerialNumber,
                Tag = equipment.DisplayLabel,
                EquipmentTag = nameplate?.Tag,
                Type = nameplate?.EquipmentType,
                Location = null,
                Company = equipment.Company,
                Identification = equipment.MostRecentReport.Identification,
                ReportCount = found.ReportCount,
                MostRecentReportDate = WireDate.From(equipment.MostRecentReport.AnalysisEndDate),
                OilType = nameplate?.OilType,
                Voltage = nameplate?.Voltage,
                Power = NameplateWireText.PowerOf(nameplate),
                Manufacturer = nameplate?.Manufacturer,
                ManufacturingYear = nameplate?.ManufacturingYear,
                OilVolume = nameplate?.OilVolume,
                NameplateKnown = nameplate is not null
            });
        });
    }

    [HttpGet("{id}/reports")]
    [ProducesResponseType(typeof(IReadOnlyList<EquipmentReportResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public Task<IActionResult> ListReports(
        string id,
        [FromQuery] PageQuery paging,
        CancellationToken cancellationToken)
    {
        if (!paging.TryToRequest(out PageRequest request))
            return Task.FromResult(this.InvalidPage());

        return AnswerAsync(async scope =>
        {
            if (!OpaqueIdentifier.TryDecode(id, out string equipmentKey))
                return this.UnreadableEquipmentIdentifier();

            Page<EquipmentReportEntry>? reports;

            try
            {
                reports = await _labResults
                    .ListReportsAsync(scope, equipmentKey, request, cancellationToken);
            }
            catch (ReportDocumentSourceUnavailableException)
            {
                return this.ReportDocumentSourceUnavailable();
            }

            if (reports is null) return this.EquipmentWithoutAnalysis();

            PageQuery.WriteHeaders(Response, reports);

            return Ok(reports.Items.Select(entry => new EquipmentReportResponse
            {
                Id = OpaqueIdentifier.Encode(entry.Report.ProtocoloLactec),
                Date = WireDate.From(entry.Report.AnalysisEndDate),
                AnalysisType = entry.Report.AnalysisType,
                Status = entry.Report.Status,
                HasSignedDocument = entry.HasSignedDocument
            }));
        });
    }

    [HttpGet("{id}/reports/{reportId}/document")]
    [ProducesResponseType(typeof(FileContentResult), StatusCodes.Status200OK, "application/pdf")]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> GetSignedReport(
        string id,
        string reportId,
        CancellationToken cancellationToken)
    {
        return AnswerAsync(async scope =>
        {
            if (!OpaqueIdentifier.TryDecode(id, out string equipmentKey))
                return this.UnreadableEquipmentIdentifier();

            if (!OpaqueIdentifier.TryDecode(reportId, out string protocoloLactec))
                return this.UnreadableReportIdentifier();

            SignedReportDocument? document;

            try
            {
                document = await _signedReports.GetSignedReportAsync(
                    scope, equipmentKey, protocoloLactec, cancellationToken);
            }
            catch (ReportDocumentSourceUnavailableException)
            {
                return this.ReportDocumentSourceUnavailable();
            }

            return document is null
                ? this.ReportDocumentNotFound()
                : File(document.Content, "application/pdf", SignedDocumentName.Of(document));
        });
    }

    /// <summary>
    /// O preambulo de toda rota desta classe: resolve o escopo e traduz a falha da fonte.
    /// Estava copiado em cada metodo, e copia de tratamento de erro diverge em silencio,
    /// porque basta uma das copias ganhar um caso novo.
    /// </summary>
    private async Task<IActionResult> AnswerAsync(Func<VisibilityScope, Task<IActionResult>> answer)
    {
        if (_labResultsOptions.Source == LabResultsOptions.NotConnectedSource)
            return this.LabIntegrationPending();

        VisibilityScope? scope = VisibilityScopeResolution.Resolve(User, _labResultsOptions);

        if (scope is null) return this.TenantNotIdentified();

        try
        {
            return await answer(scope);
        }
        catch (Exception failure) when (IsSourceFailure(failure))
        {
            return this.LabTestSourceUnavailable();
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
        Func<EquipmentWithNameplate, IActionResult> describe)
    {
        return AnswerAsync(async scope =>
        {
            if (!OpaqueIdentifier.TryDecode(id, out string equipmentKey))
                return this.UnreadableEquipmentIdentifier();

            EquipmentWithNameplate? found = await _labResults
                .GetEquipmentAsync(scope, equipmentKey, cancellationToken);

            return found is null ? this.EquipmentWithoutAnalysis() : describe(found);
        });
    }

    private static EquipmentResponse BuildListingRow(EquipmentWithNameplate found)
    {
        TestedEquipment equipment = found.Equipment;

        return new EquipmentResponse
        {
            Id = OpaqueIdentifier.Encode(equipment.EquipmentKey),
            SerialNumber = equipment.SerialNumber,
            Tag = equipment.DisplayLabel,
            EquipmentTag = found.Nameplate?.Tag,
            Type = found.Nameplate?.EquipmentType,
            Location = null,
            Company = equipment.Company,
            Identification = equipment.MostRecentReport.Identification,
            ReportCount = found.ReportCount,
            MostRecentReportDate = WireDate.From(equipment.MostRecentReport.AnalysisEndDate),
            NameplateKnown = found.Nameplate is not null
        };
    }

    /// <summary>
    /// A queda do cadastro entra aqui junto com a queda da fonte de ensaios, e as duas
    /// respondem 502. E escolha discutivel de proposito, e esta declarada: a placa e
    /// acessoria e a listagem poderia ser servida sem ela, mas servir a listagem com tipo,
    /// tensao e potencia em branco enquanto o cadastro esta fora do ar diria ao cliente que
    /// ninguem sabe esses dados, que e falso, e ele nao teria como notar a diferenca.
    /// Enquanto o corpo nao tiver um campo que diga "o cadastro nao respondeu", recusar a
    /// resposta inteira e o que nao mente.
    /// </summary>
    private static bool IsSourceFailure(Exception exception)
    {
        return exception is LabTestSourceUnavailableException
            or NameplateSourceUnavailableException;
    }
}
