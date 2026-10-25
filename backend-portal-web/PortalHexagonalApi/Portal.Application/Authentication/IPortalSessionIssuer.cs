namespace Portal.Application.Authentication;

public interface IPortalSessionIssuer
{
    ClientSession Issue(ClientUser user);
}
