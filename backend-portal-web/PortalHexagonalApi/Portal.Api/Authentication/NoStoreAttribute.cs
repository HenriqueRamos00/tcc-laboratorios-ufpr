using Microsoft.AspNetCore.Mvc.Filters;

namespace Portal.Api.Authentication;

// Also runs for automatic model-validation errors returned before the action.
[AttributeUsage(AttributeTargets.Method)]
public sealed class NoStoreAttribute : Attribute, IAlwaysRunResultFilter
{
    public void OnResultExecuting(ResultExecutingContext context) =>
        context.HttpContext.Response.Headers.CacheControl = "no-store";

    public void OnResultExecuted(ResultExecutedContext context) { }
}
