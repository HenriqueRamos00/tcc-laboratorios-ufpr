namespace Portal.Application.Authentication;

public interface ILoginUseCase
{
    Task<ClientSession?> LoginAsync(
        string email, string password, CancellationToken cancellationToken);
}
