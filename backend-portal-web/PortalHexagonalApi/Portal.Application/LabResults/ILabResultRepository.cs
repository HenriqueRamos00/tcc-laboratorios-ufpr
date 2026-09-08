using Portal.Domain.LabResults;

namespace Portal.Application.LabResults;

public interface ILabResultRepository
{
    Task<IReadOnlyList<LabResult>> GetLabResultsAsync(CancellationToken cancellationToken);
    Task<LabResult?> GetLabResultByIdAsync(string id, CancellationToken cancellationToken);
}