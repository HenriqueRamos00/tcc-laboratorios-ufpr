using System.ComponentModel.DataAnnotations;

namespace Portal.Api.Authentication;

public sealed record PasswordRecoveryRequest(
    [Required(ErrorMessage = "Informe seu e-mail.")]
    [EmailAddress(ErrorMessage = "Informe um e-mail válido.")]
    [StringLength(254, ErrorMessage = "O e-mail deve ter no máximo 254 caracteres.")]
    string Email)
{
    public override string ToString() => "PasswordRecoveryRequest { Email = [OMITIDO] }";
}
