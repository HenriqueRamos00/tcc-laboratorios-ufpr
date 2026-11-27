using System.Globalization;

using Portal.Application.ReportProjection;

namespace Portal.Api.ReportProjection;

public sealed class ReportLoadSchedule
{
    private static readonly TimeSpan BrasiliaOffset = TimeSpan.FromHours(-3);

    public ReportLoadSchedule(ReportLoadOptions options)
    {
        DailyAt = TimeOnly.Parse(options.DailyAt, CultureInfo.InvariantCulture);
        RetryAt = TimeOnly.Parse(options.RetryAt, CultureInfo.InvariantCulture);
        Zone = ZoneOf(options.TimeZone);
    }

    public TimeOnly DailyAt { get; }

    public TimeOnly RetryAt { get; }

    public TimeZoneInfo Zone { get; }

    public DateTimeOffset NextRun(DateTimeOffset now)
    {
        DateOnly today = DateOnly.FromDateTime(TimeZoneInfo.ConvertTime(now, Zone).DateTime);
        DateTimeOffset candidate = On(today, DailyAt);

        return candidate > now ? candidate : On(today.AddDays(1), DailyAt);
    }

    public DateTimeOffset? RetryOfToday(DateTimeOffset now)
    {
        if (RetryAt <= DailyAt) return null;

        DateOnly today = DateOnly.FromDateTime(TimeZoneInfo.ConvertTime(now, Zone).DateTime);
        DateTimeOffset retry = On(today, RetryAt);

        return retry > now ? retry : null;
    }

    public DateTime StartOfTodayUtc(DateTimeOffset now)
    {
        DateTime local = TimeZoneInfo.ConvertTime(now, Zone).Date;

        return new DateTimeOffset(local, Zone.GetUtcOffset(local)).UtcDateTime;
    }

    public bool IsPastTheDailyTime(DateTimeOffset now)
    {
        return TimeOnly.FromDateTime(TimeZoneInfo.ConvertTime(now, Zone).DateTime) >= DailyAt;
    }

    private DateTimeOffset On(DateOnly day, TimeOnly time)
    {
        DateTime local = day.ToDateTime(time);

        return new DateTimeOffset(local, Zone.GetUtcOffset(local));
    }

    private static TimeZoneInfo ZoneOf(string id)
    {
        try
        {
            return TimeZoneInfo.FindSystemTimeZoneById(id);
        }
        catch (Exception missing) when (id == ReportLoadOptions.DefaultTimeZone
            && missing is TimeZoneNotFoundException or InvalidTimeZoneException)
        {
            return TimeZoneInfo.CreateCustomTimeZone(id, BrasiliaOffset, id, id);
        }
    }
}
