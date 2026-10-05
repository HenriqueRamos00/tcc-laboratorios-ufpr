using System.ComponentModel.DataAnnotations;

namespace Portal.Application.Internal.Technicians;

public sealed record UpdateTechnicianRequest(
    [Required(ErrorMessage = "Nome é obrigatório")] string Name,
    [Required(ErrorMessage = "E-mail é obrigatório")] string Email,
    [Required(ErrorMessage = "Unidade é obrigatória")] string Unit,
    [Required(ErrorMessage = "Especialidade é obrigatória")] string Specialty,
    [Required(ErrorMessage = "Status é obrigatório")] TechnicianStatus? Status,
    string? Password);
