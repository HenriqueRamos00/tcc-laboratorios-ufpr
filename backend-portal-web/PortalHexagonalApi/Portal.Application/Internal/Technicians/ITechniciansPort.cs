using Portal.Application.Internal;

namespace Portal.Application.Internal.Technicians;

public interface ITechniciansPort
{
    Task<BackendLabResult<IReadOnlyList<Technician>>> ListAsync(
        string? search,
        TechnicianStatus? status,
        CancellationToken cancellationToken);

    Task<BackendLabResult<Technician>> CreateAsync(
        CreateTechnicianRequest request,
        CancellationToken cancellationToken);

    Task<BackendLabResult<Technician>> UpdateAsync(
        long id,
        UpdateTechnicianRequest request,
        CancellationToken cancellationToken);

    Task<BackendLabResult<Technician>> ChangeStatusAsync(
        long id,
        TechnicianStatus status,
        CancellationToken cancellationToken);
}
