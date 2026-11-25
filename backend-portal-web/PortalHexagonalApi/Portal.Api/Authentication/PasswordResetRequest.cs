using System.ComponentModel.DataAnnotations;

namespace Portal.Api.Authentication;

public sealed record PasswordResetRequest(
    [Required(ErrorMessage = "O link de recuperação é inválido.")]
    [StringLength(128, ErrorMessage = "O link de recuperação é inválido.")]
    string Token,
    [Required(ErrorMessage = "Informe a nova senha.")]
    [StringLength(72, ErrorMessage = "A senha deve ter no máximo 72 caracteres.")]
    string Password)
{
    public override string ToString() => "PasswordResetRequest { Token = [OMITIDO], Password = [OMITIDA] }";
}
