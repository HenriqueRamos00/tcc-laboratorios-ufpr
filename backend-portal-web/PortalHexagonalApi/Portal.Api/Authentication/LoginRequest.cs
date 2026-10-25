using System.ComponentModel.DataAnnotations;

namespace Portal.Api.Authentication;

public sealed record LoginRequest(
    [Required(ErrorMessage = "E-mail é obrigatório.")]
    [EmailAddress(ErrorMessage = "Informe um e-mail válido.")]
    [StringLength(254, ErrorMessage = "E-mail deve ter no máximo 254 caracteres.")]
    string Email,
    [Required(ErrorMessage = "Senha é obrigatória.")]
    [StringLength(72, ErrorMessage = "Senha deve ter no máximo 72 caracteres.")]
    string Password)
{
    public override string ToString() => "LoginRequest { Password = [REDACTED] }";
}
