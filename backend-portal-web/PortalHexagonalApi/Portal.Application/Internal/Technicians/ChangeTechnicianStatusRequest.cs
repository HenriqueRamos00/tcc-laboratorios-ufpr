using System.ComponentModel.DataAnnotations;

namespace Portal.Application.Internal.Technicians;

public sealed record ChangeTechnicianStatusRequest(
    [Required(ErrorMessage = "Status é obrigatório")]
    TechnicianStatus? Status);
