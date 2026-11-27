using System.Globalization;
using System.Net.ServerSentEvents;
using System.Runtime.CompilerServices;

using Microsoft.AspNetCore.Mvc;

using Portal.Api.RealTime;

namespace Portal.Api.Controllers;

[ApiController]
[Route("api/events")]
public sealed class RealTimeController : ControllerBase
{
    public const string PingEvent = "ping";

    private const string LastEventHeader = "Last-Event-ID";

    private readonly InMemoryRealTimeChannel _channel;
    private readonly RealTimeOptions _options;

    public RealTimeController(InMemoryRealTimeChannel channel, RealTimeOptions options)
    {
        _channel = channel;
        _options = options;
    }

    [HttpGet]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status429TooManyRequests)]
    public async Task<IActionResult> Connect(CancellationToken cancellationToken)
    {
        string? userKey = RealTimeUser.KeyOf(User);

        if (userKey is null) return this.TenantNotIdentified();

        RealTimeSubscription? subscription = _channel.TryOpen(userKey, LastEventReceived());

        if (subscription is null) return this.TooManyRealTimeConnections();

        Response.Headers["X-Accel-Buffering"] = "no";

        try
        {
            await TypedResults
                .ServerSentEvents(EventsOf(subscription, cancellationToken))
                .ExecuteAsync(HttpContext);
        }
        finally
        {
            subscription.Dispose();
        }

        return new EmptyResult();
    }

    private async IAsyncEnumerable<SseItem<string>> EventsOf(
        RealTimeSubscription subscription,
        [EnumeratorCancellation] CancellationToken cancellationToken)
    {
        using RealTimeSubscription open = subscription;
        using CancellationTokenSource duration = new(TimeSpan.FromMinutes(_options.MaxDurationMinutes));
        using CancellationTokenSource closing = CancellationTokenSource
            .CreateLinkedTokenSource(cancellationToken, duration.Token);

        yield return new SseItem<string>(string.Empty, PingEvent);

        if (open.Resume.MustReload) yield return ItemOf(RealTimeEvent.Reload);

        foreach (RealTimeEvent missed in open.Resume.Missed) yield return ItemOf(missed);

        TimeSpan pingInterval = TimeSpan.FromSeconds(_options.PingIntervalSeconds);

        while (true)
        {
            Wait waited = await WaitAsync(open, pingInterval, closing.Token);

            if (waited == Wait.Closed) yield break;

            if (waited == Wait.Quiet)
            {
                yield return new SseItem<string>(string.Empty, PingEvent);
                continue;
            }

            while (open.Events.TryRead(out RealTimeEvent? published)) yield return ItemOf(published);
        }
    }

    private static async Task<Wait> WaitAsync(
        RealTimeSubscription subscription,
        TimeSpan pingInterval,
        CancellationToken closing)
    {
        using CancellationTokenSource ping = CancellationTokenSource.CreateLinkedTokenSource(closing);
        ping.CancelAfter(pingInterval);

        try
        {
            return await subscription.Events.WaitToReadAsync(ping.Token) ? Wait.Event : Wait.Closed;
        }
        catch (OperationCanceledException)
        {
            return closing.IsCancellationRequested ? Wait.Closed : Wait.Quiet;
        }
    }

    private static SseItem<string> ItemOf(RealTimeEvent published)
    {
        return new SseItem<string>(published.DataJson, published.Name)
        {
            EventId = published.Id > 0 ? published.Id.ToString(CultureInfo.InvariantCulture) : null
        };
    }

    private long LastEventReceived()
    {
        return Request.Headers.TryGetValue(LastEventHeader, out var value)
            && long.TryParse(value.ToString(), out long number)
                ? number
                : 0;
    }

    private enum Wait
    {
        Event,
        Quiet,
        Closed
    }
}
