namespace Portal.Application.Authentication;

public interface IClientPasswordRecovery
{
    Task RequestAsync(string email, CancellationToken cancellationToken = default);

    Task<bool> ResetAsync(
        string token, string password, CancellationToken cancellationToken = default);
}
