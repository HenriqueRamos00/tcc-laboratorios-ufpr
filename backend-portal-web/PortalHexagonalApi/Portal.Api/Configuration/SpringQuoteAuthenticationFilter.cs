using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Controllers;
using Microsoft.AspNetCore.Mvc.Filters;

using Portal.Api;
using Portal.Api.Controllers;

namespace Portal.Api.Configuration;

/// <summary>Exige um Bearer antes das ações de orçamento que consultam o Spring.</summary>
public sealed class SpringQuoteAuthenticationFilter : IAsyncActionFilter
{
    public async Task OnActionExecutionAsync(ActionExecutingContext context, ActionExecutionDelegate next)
    {
        if (context.ActionDescriptor is ControllerActionDescriptor action
            && action.ControllerTypeInfo.AsType() == typeof(QuotesController)
            && !SpringBearerCredential.IsValid(context.HttpContext.Request.Headers["Authorization"].ToString()))
        {
            context.HttpContext.Response.Headers["WWW-Authenticate"] = "Bearer";
            context.Result = ((ControllerBase)context.Controller).QuoteAuthenticationRequired();
            return;
        }

        await next();
    }
}
