using System.ComponentModel.DataAnnotations;

namespace Portal.Application.Internal.Auth;

public sealed record InternalLoginRequest(
    [Required(ErrorMessage = "E-mail é obrigatório")]
    string Email,
    [Required(ErrorMessage = "Senha é obrigatória")]
    string Password);
