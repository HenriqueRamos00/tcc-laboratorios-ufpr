using Portal.Application.Internal;

namespace Portal.Application.Internal.Auth;

public sealed class InternalAuthService
{
    private readonly IInternalAuthPort _internalAuthPort;

    public InternalAuthService(IInternalAuthPort internalAuthPort)
    {
        _internalAuthPort = internalAuthPort;
    }

    public Task<BackendLabResult<InternalLoginResponse>> LoginAsync(
        InternalLoginRequest request,
        CancellationToken cancellationToken)
    {
        return _internalAuthPort.LoginAsync(request, cancellationToken);
    }
}
