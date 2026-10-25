using System.Text.Json.Serialization;
using Portal.Application.Authentication;

namespace Portal.Api.Authentication;

public sealed record ClientUserResponse(
    string Id, string Name, string Email, string Role,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] IReadOnlyList<ClientCompany>? Companies,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? ServiceCode,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] ClientAuthorizations? Authorizations)
{
    public static ClientUserResponse From(ClientUser user) => new(user.Id, user.Name, user.Email, user.Role,
        user.Companies, user.ServiceCode, user.Authorizations);
}
