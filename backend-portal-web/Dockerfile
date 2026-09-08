FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

COPY PortalHexagonalApi/PortalHexagonalApi.slnx PortalHexagonalApi/
COPY PortalHexagonalApi/Portal.Api/Portal.Api.csproj PortalHexagonalApi/Portal.Api/
COPY PortalHexagonalApi/Portal.Application/Portal.Application.csproj PortalHexagonalApi/Portal.Application/
COPY PortalHexagonalApi/Portal.Adapters/Portal.Adapters.csproj PortalHexagonalApi/Portal.Adapters/
COPY PortalHexagonalApi/Portal.Domain/Portal.Domain.csproj PortalHexagonalApi/Portal.Domain/

RUN dotnet restore PortalHexagonalApi/Portal.Api/Portal.Api.csproj

COPY PortalHexagonalApi/ PortalHexagonalApi/
RUN dotnet publish PortalHexagonalApi/Portal.Api/Portal.Api.csproj \
    --configuration Release \
    --output /app/publish \
    --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app

COPY --from=build /app/publish .

EXPOSE 8080
ENTRYPOINT ["dotnet", "Portal.Api.dll"]
