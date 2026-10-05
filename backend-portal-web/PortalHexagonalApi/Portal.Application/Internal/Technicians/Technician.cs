namespace Portal.Application.Internal.Technicians;

public sealed record Technician(
    long Id,
    string Name,
    string Email,
    string? Unit,
    string? Specialty,
    TechnicianStatus Status);
