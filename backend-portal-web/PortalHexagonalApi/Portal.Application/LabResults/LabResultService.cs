using Portal.Domain.LabResults;

namespace Portal.Application.LabResults;

public sealed class LabResultService : ILabResultUseCase
{
    private readonly ILabResultRepository _labResultRepository;

    public LabResultService(ILabResultRepository labResultRepository)
    {
        _labResultRepository = labResultRepository;
    }

    public Task<IReadOnlyList<LabResult>> GetLabResultsAsync(CancellationToken cancellationToken)
    {
        return _labResultRepository.GetLabResultsAsync(cancellationToken);
    }

    public Task<LabResult?> GetLabResultByIdAsync(string id, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(id))
            return Task.FromResult<LabResult?>(null);

        return _labResultRepository.GetLabResultByIdAsync(id, cancellationToken);
    }
}
