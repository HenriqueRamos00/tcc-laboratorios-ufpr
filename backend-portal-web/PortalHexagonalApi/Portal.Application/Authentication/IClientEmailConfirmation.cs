namespace Portal.Application.Authentication;

public interface IClientEmailConfirmation
{
    Task<bool> ConfirmAsync(string token, CancellationToken cancellationToken = default);
}
