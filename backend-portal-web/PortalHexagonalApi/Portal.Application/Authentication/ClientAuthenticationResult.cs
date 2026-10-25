namespace Portal.Application.Authentication;

// Backend Lab supplies its own session; Backend Auth supplies identity only.
public sealed record ClientAuthenticationResult(ClientUser User, ClientSession? ExistingSession = null);
