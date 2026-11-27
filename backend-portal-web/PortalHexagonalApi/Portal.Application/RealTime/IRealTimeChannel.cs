namespace Portal.Application.RealTime;

public interface IRealTimeChannel
{
    void Publish(string name, string dataJson, IReadOnlySet<string> userKeys);
}
