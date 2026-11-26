using System.ComponentModel.DataAnnotations;

namespace Portal.Api.Authentication;

public sealed record EmailConfirmationRequest(
    [Required(ErrorMessage = "O link de confirmação é inválido.")]
    [StringLength(128, ErrorMessage = "O link de confirmação é inválido.")]
    string Token)
{
    public override string ToString() => "EmailConfirmationRequest { Token = [OMITIDO] }";
}
