using Microsoft.AspNetCore.Mvc;
using Portal.Application.LabResults;
using Portal.Domain.LabResults;

namespace Portal.Api.Controllers;

[ApiController]
[Route("api/lab-results")]
public sealed class LabResultsController : ControllerBase
{
    private readonly ILabResultUseCase _labResultUseCase;

    public LabResultsController(ILabResultUseCase labResultUseCase)
    {
        _labResultUseCase = labResultUseCase;
    }

    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<LabResult>>> GetLabResults(CancellationToken cancellationToken)
    {
        IReadOnlyList<LabResult> results = await _labResultUseCase.GetLabResultsAsync(cancellationToken);
        return Ok(results);
    }

    [HttpGet("{id}")]
    public async Task<ActionResult<LabResult>> GetLabResultById(string id, CancellationToken cancellationToken)
    {
        LabResult? result = await _labResultUseCase.GetLabResultByIdAsync(id, cancellationToken);

        if (result is null)
            return NotFound(new { message = "Lab result not found." });

        return Ok(result);
    }
}
