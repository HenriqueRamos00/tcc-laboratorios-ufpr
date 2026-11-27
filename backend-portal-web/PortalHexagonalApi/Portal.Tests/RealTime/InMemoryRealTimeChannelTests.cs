using Portal.Api.RealTime;
using Portal.Api.ReportProjection;
using Portal.Application.ReportProjection;

using Xunit;

namespace Portal.Tests.RealTime;

public sealed class InMemoryRealTimeChannelTests
{
    [Fact]
    public void AnEventReachesOnlyTheConnectionsOfTheUsersItNames()
    {
        InMemoryRealTimeChannel channel = new(new RealTimeOptions());
        using RealTimeSubscription asked = channel.TryOpen("42", 0)!;
        using RealTimeSubscription other = channel.TryOpen("77", 0)!;

        channel.Publish("carga.concluida", "{}", new HashSet<string> { "42" });

        Assert.True(asked.Events.TryRead(out RealTimeEvent? received));
        Assert.Equal((1L, "carga.concluida", "{}"), (received!.Id, received.Name, received.DataJson));
        Assert.False(other.Events.TryRead(out _));
    }

    [Fact]
    public void EveryOpenTabOfTheSameUserReceivesTheEvent()
    {
        InMemoryRealTimeChannel channel = new(new RealTimeOptions());
        using RealTimeSubscription first = channel.TryOpen("42", 0)!;
        using RealTimeSubscription second = channel.TryOpen("42", 0)!;

        channel.Publish("carga.concluida", "{}", new HashSet<string> { "42" });

        Assert.True(first.Events.TryRead(out _));
        Assert.True(second.Events.TryRead(out _));
    }

    [Fact]
    public void TheConnectionBeyondTheLimitOfTheUserIsRefusedAndClosingOneFreesIt()
    {
        InMemoryRealTimeChannel channel = new(new RealTimeOptions { MaxConnectionsPerUser = 2 });
        RealTimeSubscription first = channel.TryOpen("42", 0)!;
        using RealTimeSubscription second = channel.TryOpen("42", 0)!;

        Assert.Null(channel.TryOpen("42", 0));
        using RealTimeSubscription another = channel.TryOpen("77", 0)!;
        Assert.NotNull(another);

        first.Dispose();
        first.Dispose();

        using RealTimeSubscription reopened = channel.TryOpen("42", 0)!;
        Assert.NotNull(reopened);
    }

    [Fact]
    public void AReconnectionReceivesWhatItMissedAndOnlyWhatWasMeantForIt()
    {
        InMemoryRealTimeChannel channel = new(new RealTimeOptions());
        channel.Publish("carga.concluida", "1", new HashSet<string> { "42" });
        channel.Publish("carga.concluida", "2", new HashSet<string> { "77" });
        channel.Publish("carga.concluida", "3", new HashSet<string> { "42" });

        using RealTimeSubscription resumed = channel.TryOpen("42", 1)!;

        Assert.False(resumed.Resume.MustReload);
        Assert.Equal(["3"], resumed.Resume.Missed.Select(missed => missed.DataJson));
    }

    [Fact]
    public void AReconnectionOlderThanTheHistoryIsToldToReload()
    {
        InMemoryRealTimeChannel channel = new(new RealTimeOptions { HistorySize = 1 });
        channel.Publish("carga.concluida", "1", new HashSet<string> { "42" });
        channel.Publish("carga.concluida", "2", new HashSet<string> { "42" });
        channel.Publish("carga.concluida", "3", new HashSet<string> { "42" });

        using RealTimeSubscription resumed = channel.TryOpen("42", 1)!;
        using RealTimeSubscription ahead = channel.TryOpen("42", 99)!;

        Assert.True(resumed.Resume.MustReload);
        Assert.Empty(resumed.Resume.Missed);
        Assert.True(ahead.Resume.MustReload);
    }

    [Fact]
    public void ALineBreakNeverSplitsTheEventOnTheWire()
    {
        InMemoryRealTimeChannel channel = new(new RealTimeOptions());
        using RealTimeSubscription subscription = channel.TryOpen("42", 0)!;

        channel.Publish("carga\nconcluida", "{\r\n}", new HashSet<string> { "42" });

        Assert.True(subscription.Events.TryRead(out RealTimeEvent? received));
        Assert.Equal(("carga concluida", "{  }"), (received!.Name, received.DataJson));
    }

    [Fact]
    public void AConnectionThatStopsReadingIsClosedInsteadOfGrowingWithoutLimit()
    {
        InMemoryRealTimeChannel channel = new(new RealTimeOptions { CapacityPerConnection = 1 });
        using RealTimeSubscription slow = channel.TryOpen("42", 0)!;

        channel.Publish("carga.concluida", "1", new HashSet<string> { "42" });
        channel.Publish("carga.concluida", "2", new HashSet<string> { "42" });

        Assert.True(slow.Events.TryRead(out _));
        Assert.False(slow.Events.TryRead(out _));
        Assert.True(slow.Events.Completion.IsCompleted);
    }

    [Fact]
    public void TheDailyRoundIsScheduledInTheTimeOfBrasilia()
    {
        ReportLoadSchedule schedule = new(new ReportLoadOptions { DailyAt = "06:00" });

        DateTimeOffset before = new(2026, 11, 10, 8, 30, 0, TimeSpan.Zero);
        DateTimeOffset after = new(2026, 11, 10, 9, 0, 0, TimeSpan.Zero);

        Assert.Equal(new DateTimeOffset(2026, 11, 10, 9, 0, 0, TimeSpan.Zero), schedule.NextRun(before));
        Assert.Equal(new DateTimeOffset(2026, 11, 11, 9, 0, 0, TimeSpan.Zero), schedule.NextRun(after));
        Assert.False(schedule.IsPastTheDailyTime(before));
        Assert.True(schedule.IsPastTheDailyTime(after));
        Assert.Equal(new DateTime(2026, 11, 10, 3, 0, 0, DateTimeKind.Utc), schedule.StartOfTodayUtc(before));
        Assert.Equal(new DateTime(2026, 11, 9, 3, 0, 0, DateTimeKind.Utc), schedule.StartOfTodayUtc(new DateTimeOffset(2026, 11, 10, 2, 0, 0, TimeSpan.Zero)));
    }

    [Fact]
    public void AFailedRoundIsRetriedOnlyLaterTheSameDay()
    {
        ReportLoadSchedule schedule = new(new ReportLoadOptions { DailyAt = "06:00", RetryAt = "15:00" });

        Assert.Equal(
            new DateTimeOffset(2026, 11, 10, 18, 0, 0, TimeSpan.Zero),
            schedule.RetryOfToday(new DateTimeOffset(2026, 11, 10, 9, 30, 0, TimeSpan.Zero)));
        Assert.Null(schedule.RetryOfToday(new DateTimeOffset(2026, 11, 10, 18, 30, 0, TimeSpan.Zero)));
        Assert.Null(new ReportLoadSchedule(new ReportLoadOptions { DailyAt = "16:00", RetryAt = "15:00" })
            .RetryOfToday(new DateTimeOffset(2026, 11, 10, 19, 30, 0, TimeSpan.Zero)));
    }

    [Theory]
    [InlineData("6 da manha")]
    [InlineData("")]
    public void AnInvalidDailyTimeIsAConfigurationProblem(string dailyAt)
    {
        Assert.Contains(new ReportLoadOptions { DailyAt = dailyAt }.Problems(), problem => problem.Contains("DailyAt", StringComparison.Ordinal));
        Assert.Contains(new ReportLoadOptions { RetryAt = dailyAt }.Problems(), problem => problem.Contains("RetryAt", StringComparison.Ordinal));
        Assert.Empty(new ReportLoadOptions().Problems());
    }
}
