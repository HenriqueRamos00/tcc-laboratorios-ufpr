using Portal.Application.Internal;

namespace Portal.Application.Internal.Technicians;

public sealed class TechniciansService
{
    private readonly ITechniciansPort _techniciansPort;

    public TechniciansService(ITechniciansPort techniciansPort)
    {
        _techniciansPort = techniciansPort;
    }

    public Task<BackendLabResult<IReadOnlyList<Technician>>> ListAsync(
        string? search,
        TechnicianStatus? status,
        CancellationToken cancellationToken) =>
        _techniciansPort.ListAsync(search, status, cancellationToken);

    public Task<BackendLabResult<Technician>> CreateAsync(
        CreateTechnicianRequest request,
        CancellationToken cancellationToken) =>
        _techniciansPort.CreateAsync(request, cancellationToken);

    public Task<BackendLabResult<Technician>> UpdateAsync(
        long id,
        UpdateTechnicianRequest request,
        CancellationToken cancellationToken) =>
        _techniciansPort.UpdateAsync(id, request, cancellationToken);

    public Task<BackendLabResult<Technician>> ChangeStatusAsync(
        long id,
        TechnicianStatus status,
        CancellationToken cancellationToken) =>
        _techniciansPort.ChangeStatusAsync(id, status, cancellationToken);
}
