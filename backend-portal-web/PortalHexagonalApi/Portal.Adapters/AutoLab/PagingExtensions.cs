using Microsoft.EntityFrameworkCore;

using Portal.Application.Paging;

namespace Portal.Adapters.AutoLab;

public static class PagingExtensions
{
    public static IQueryable<T> OnlyThePage<T>(this IQueryable<T> orderedQuery, PageRequest request)
    {
        ArgumentNullException.ThrowIfNull(orderedQuery);
        ArgumentNullException.ThrowIfNull(request);

        int offset = request.Offset;
        int pageSize = request.PageSize;

        return orderedQuery.Skip(offset).Take(pageSize);
    }

    public static async Task<Page<T>> ToPageAsync<T>(
        this IQueryable<T> orderedQuery,
        PageRequest request,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(orderedQuery);
        ArgumentNullException.ThrowIfNull(request);

        int totalCount = await orderedQuery.CountAsync(cancellationToken);

        if (totalCount <= request.Offset)
            return new Page<T>([], request.Page, request.PageSize, totalCount);

        List<T> items = await orderedQuery.OnlyThePage(request).ToListAsync(cancellationToken);

        return new Page<T>(items, request.Page, request.PageSize, totalCount);
    }
}
