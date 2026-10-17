using System.Net;
using System.Text;

namespace Portal.Tests.Authentication;

internal sealed class ScriptedBackendLabHandler(
    Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> respond) : HttpMessageHandler
{
    private int _requestCount;

    public int RequestCount => Volatile.Read(ref _requestCount);
    public string? RequestPathAndQuery { get; private set; }
    public HttpMethod? RequestMethod { get; private set; }
    public string? RequestBody { get; private set; }
    public TaskCompletionSource<bool> RequestStarted { get; } =
        new(TaskCreationOptions.RunContinuationsAsynchronously);

    public static ScriptedBackendLabHandler Returning(
        HttpStatusCode statusCode,
        string body,
        string mediaType = "application/json") =>
        new((_, _) => Task.FromResult(new HttpResponseMessage(statusCode)
        {
            Content = new StringContent(body, Encoding.UTF8, mediaType)
        }));

    public static ScriptedBackendLabHandler Failing(Exception failure) =>
        new((_, _) => Task.FromException<HttpResponseMessage>(failure));

    public static ScriptedBackendLabHandler WaitingForCancellation() =>
        new(async (_, cancellationToken) =>
        {
            await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
            return new HttpResponseMessage(HttpStatusCode.OK);
        });

    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request,
        CancellationToken cancellationToken)
    {
        Interlocked.Increment(ref _requestCount);
        RequestMethod = request.Method;
        RequestPathAndQuery = request.RequestUri?.PathAndQuery;
        RequestBody = request.Content is null
            ? null
            : await request.Content.ReadAsStringAsync(cancellationToken);
        RequestStarted.TrySetResult(true);

        return await respond(request, cancellationToken);
    }
}
