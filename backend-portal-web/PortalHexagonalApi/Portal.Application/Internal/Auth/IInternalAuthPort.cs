using Portal.Application.Internal;

namespace Portal.Application.Internal.Auth;

public interface IInternalAuthPort
{
    Task<BackendLabResult<InternalLoginResponse>> LoginAsync(
        InternalLoginRequest request,
        CancellationToken cancellationToken);
}
