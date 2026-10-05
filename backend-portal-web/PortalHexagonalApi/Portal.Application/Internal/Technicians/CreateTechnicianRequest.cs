using System.ComponentModel.DataAnnotations;

namespace Portal.Application.Internal.Technicians;

public sealed record CreateTechnicianRequest(
    [Required(ErrorMessage = "Nome é obrigatório")] string Name,
    [Required(ErrorMessage = "E-mail é obrigatório")] string Email,
    [Required(ErrorMessage = "Unidade é obrigatória")] string Unit,
    [Required(ErrorMessage = "Especialidade é obrigatória")] string Specialty,
    [Required(ErrorMessage = "Status é obrigatório")] TechnicianStatus? Status,
    [Required(ErrorMessage = "Senha é obrigatória no cadastro")] string Password);
