using System.Threading.Channels;

using Portal.Application.RealTime;

namespace Portal.Api.RealTime;

public sealed class RealTimeOptions
{
    public const string SectionName = "RealTime";

    public int PingIntervalSeconds { get; set; } = 20;

    public int MaxDurationMinutes { get; set; } = 30;

    public int MaxConnectionsPerUser { get; set; } = 5;

    public int HistorySize { get; set; } = 200;

    public int CapacityPerConnection { get; set; } = 100;
}

public sealed record RealTimeEvent(long Id, string Name, string DataJson)
{
    public static readonly RealTimeEvent Reload = new(0, "recarregar", string.Empty);
}

public sealed record RealTimeResume(bool MustReload, IReadOnlyList<RealTimeEvent> Missed);

public sealed class RealTimeSubscription : IDisposable
{
    private readonly Action _release;
    private int _released;

    internal RealTimeSubscription(ChannelReader<RealTimeEvent> events, RealTimeResume resume, Action release)
    {
        Events = events;
        Resume = resume;
        _release = release;
    }

    public ChannelReader<RealTimeEvent> Events { get; }

    public RealTimeResume Resume { get; }

    public void Dispose()
    {
        if (Interlocked.Exchange(ref _released, 1) == 0) _release();
    }
}

public sealed class InMemoryRealTimeChannel : IRealTimeChannel
{
    private readonly int _historySize;
    private readonly int _maxPerUser;
    private readonly int _capacityPerConnection;
    private readonly object _lock = new();
    private readonly LinkedList<Published> _history = new();
    private readonly Dictionary<Guid, Subscriber> _subscribers = [];
    private long _lastId;

    public InMemoryRealTimeChannel(RealTimeOptions options)
    {
        _historySize = Math.Max(1, options.HistorySize);
        _maxPerUser = Math.Max(1, options.MaxConnectionsPerUser);
        _capacityPerConnection = Math.Max(1, options.CapacityPerConnection);
    }

    public RealTimeSubscription? TryOpen(string userKey, long lastEventReceived)
    {
        Channel<RealTimeEvent> channel = Channel.CreateBounded<RealTimeEvent>(
            new BoundedChannelOptions(_capacityPerConnection)
            {
                SingleReader = true,
                SingleWriter = false,
                FullMode = BoundedChannelFullMode.Wait
            });
        Guid id = Guid.NewGuid();

        lock (_lock)
        {
            int open = _subscribers.Values.Count(subscriber =>
                string.Equals(subscriber.UserKey, userKey, StringComparison.Ordinal));

            if (open >= _maxPerUser) return null;

            RealTimeResume resume = ResumeOf(userKey, lastEventReceived);
            _subscribers[id] = new Subscriber(userKey, channel.Writer);

            return new RealTimeSubscription(channel.Reader, resume, () => Release(id, channel.Writer));
        }
    }

    public void Publish(string name, string dataJson, IReadOnlySet<string> userKeys)
    {
        ArgumentNullException.ThrowIfNull(userKeys);

        string safeName = WithoutLineBreaks(name);
        string safeData = WithoutLineBreaks(dataJson);

        lock (_lock)
        {
            RealTimeEvent published = new(++_lastId, safeName, safeData);
            _history.AddLast(new Published(published, userKeys));

            while (_history.Count > _historySize) _history.RemoveFirst();

            foreach (Subscriber subscriber in _subscribers.Values)
            {
                if (!userKeys.Contains(subscriber.UserKey)) continue;

                if (!subscriber.Writer.TryWrite(published)) subscriber.Writer.TryComplete();
            }
        }
    }

    private RealTimeResume ResumeOf(string userKey, long lastEventReceived)
    {
        if (lastEventReceived <= 0) return new RealTimeResume(false, []);

        if (lastEventReceived > _lastId
            || _history.Count == 0
            || _history.First!.Value.Event.Id > lastEventReceived + 1)
            return new RealTimeResume(true, []);

        return new RealTimeResume(
            false,
            [.. _history
                .Where(published => published.Event.Id > lastEventReceived && published.UserKeys.Contains(userKey))
                .Select(published => published.Event)]);
    }

    private void Release(Guid id, ChannelWriter<RealTimeEvent> writer)
    {
        lock (_lock)
        {
            _subscribers.Remove(id);
        }

        writer.TryComplete();
    }

    private static string WithoutLineBreaks(string text)
    {
        return string.IsNullOrEmpty(text) ? text : text.Replace('\r', ' ').Replace('\n', ' ');
    }

    private sealed record Subscriber(string UserKey, ChannelWriter<RealTimeEvent> Writer);

    private sealed record Published(RealTimeEvent Event, IReadOnlySet<string> UserKeys);
}
