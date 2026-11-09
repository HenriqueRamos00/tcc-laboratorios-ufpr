namespace Portal.Application.Paging;

public sealed record Page<T>(IReadOnlyList<T> Items, int PageNumber, int PageSize, int TotalCount)
{
    public bool HasNext => (long)PageNumber * PageSize < TotalCount;

    public static Page<T> Empty(PageRequest request)
    {
        ArgumentNullException.ThrowIfNull(request);

        return new Page<T>([], request.Page, request.PageSize, 0);
    }

    public static Page<T> Of(IEnumerable<T> all, PageRequest request)
    {
        ArgumentNullException.ThrowIfNull(all);
        ArgumentNullException.ThrowIfNull(request);

        List<T> items = [.. all];

        return new Page<T>(
            [.. items.Skip(request.Offset).Take(request.PageSize)],
            request.Page,
            request.PageSize,
            items.Count);
    }

    public Page<TOther> WithItems<TOther>(IReadOnlyList<TOther> items)
    {
        return new Page<TOther>(items, PageNumber, PageSize, TotalCount);
    }
}
