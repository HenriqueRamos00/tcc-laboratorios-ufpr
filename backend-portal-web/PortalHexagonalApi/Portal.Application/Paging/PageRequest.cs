namespace Portal.Application.Paging;

public sealed record PageRequest(int Page, int PageSize)
{
    public const int DefaultPageSize = 50;
    public const int MaxPageSize = 200;
    public const int MaxPage = 100_000;

    public int Page { get; } = Page is >= 1 and <= MaxPage
        ? Page
        : throw new ArgumentOutOfRangeException(nameof(Page));

    public int PageSize { get; } = PageSize is >= 1 and <= MaxPageSize
        ? PageSize
        : throw new ArgumentOutOfRangeException(nameof(PageSize));

    public static PageRequest First { get; } = new(1, DefaultPageSize);

    public int Offset => (Page - 1) * PageSize;

    public static bool TryCreate(int? page, int? pageSize, out PageRequest request)
    {
        int wantedPage = page ?? 1;
        int wantedSize = pageSize ?? DefaultPageSize;

        if (wantedPage is < 1 or > MaxPage || wantedSize is < 1 or > MaxPageSize)
        {
            request = First;
            return false;
        }

        request = new PageRequest(wantedPage, wantedSize);
        return true;
    }
}
