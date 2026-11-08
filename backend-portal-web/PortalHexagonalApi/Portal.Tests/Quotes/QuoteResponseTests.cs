using System.Text.Json;
using System.Text.Json.Serialization;

using Portal.Api.Quotes;

using Xunit;

namespace Portal.Tests.Quotes;

public sealed class QuoteResponseTests
{
    [Theory]
    [InlineData(null)]
    [InlineData("L-45490-2026-X")]
    public void ProtocoloSoEhSerializadoQuandoExiste(string? protocol)
    {
        QuoteResponse response = new()
        {
            Id = "512",
            OpportunityId = string.Empty,
            Code = "ORC-2026-0091",
            Name = "Análise de óleo",
            Status = "InProgress",
            Stage = "Em execução",
            Description = "Análise físico-química",
            CompanyName = "EMPRESA DE ENERGIA",
            Protocol = protocol,
            ExternalContactName = "Ana",
            ExternalContactEmail = "ana@example.test",
            TotalPrice = 1234.56m,
            CreatedDate = DateTimeOffset.Parse("2026-10-20T13:45:00-03:00")
        };

        JsonSerializerOptions options = new(JsonSerializerDefaults.Web)
        {
            DefaultIgnoreCondition = JsonIgnoreCondition.Never
        };
        using JsonDocument json = JsonDocument.Parse(JsonSerializer.Serialize(response, options));
        JsonElement body = json.RootElement;

        Assert.Equal("ORC-2026-0091", body.GetProperty("code").GetString());
        if (protocol is null)
        {
            Assert.False(body.TryGetProperty("protocol", out _));
            return;
        }

        Assert.Equal(protocol, body.GetProperty("protocol").GetString());
    }
}
