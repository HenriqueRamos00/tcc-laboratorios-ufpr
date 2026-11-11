using System.Globalization;

using Microsoft.AspNetCore.Mvc;

using Portal.Application.Paging;

namespace Portal.Api.LabResults;

public sealed record PageQuery
{
    public const string TotalCountHeader = "X-Total-Count";
    public const string PageHeader = "X-Page";
    public const string PageSizeHeader = "X-Page-Size";

    public static readonly string[] ExposedHeaders = [TotalCountHeader, PageHeader, PageSizeHeader];

    [FromQuery(Name = "page")]
    public int? Page { get; init; }

    [FromQuery(Name = "pageSize")]
    public int? PageSize { get; init; }

    public bool TryToRequest(out PageRequest request)
    {
        return PageRequest.TryCreate(Page, PageSize, out request);
    }

    public static void WriteHeaders<T>(HttpResponse response, Page<T> page)
    {
        ArgumentNullException.ThrowIfNull(response);
        ArgumentNullException.ThrowIfNull(page);

        response.Headers[TotalCountHeader] = page.TotalCount.ToString(CultureInfo.InvariantCulture);
        response.Headers[PageHeader] = page.PageNumber.ToString(CultureInfo.InvariantCulture);
        response.Headers[PageSizeHeader] = page.PageSize.ToString(CultureInfo.InvariantCulture);
    }
}
