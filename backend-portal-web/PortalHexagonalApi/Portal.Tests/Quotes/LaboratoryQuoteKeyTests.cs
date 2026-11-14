using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.Quotes;

/// <summary>
/// A regra de correlacao entre a cotacao do Salesforce e o <c>OrcamentoCRM</c> do AutoLAB.
/// Tres formas viram a chave nua; qualquer outra nao vira chave nenhuma.
/// </summary>
public sealed class LaboratoryQuoteKeyTests
{
    [Theory]
    [InlineData("EAQ_2024_37569_V_1", "2024_37569")]
    [InlineData("EAQ_2024_37569_V_12", "2024_37569")]
    [InlineData("EAQ_2024_37569", "2024_37569")]
    [InlineData("2024_37569", "2024_37569")]
    [InlineData(" EAQ_2024_37569_V_1 ", "2024_37569")]
    [InlineData("EAQ_2026_45482_V_1", "2026_45482")]
    public void AsTresFormasAceitasViramAChaveNua(string quotationCode, string expected)
    {
        Assert.Equal(expected, LaboratoryQuoteKey.FromQuotationCode(quotationCode)?.Value);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("EAQ_2024_37569_V_")]
    [InlineData("EAQ_2024_37569_V_1_extra")]
    [InlineData("EAQ_2024_37569_UHAB")]
    [InlineData("2026_49001_UHAB Agua Boa")]
    [InlineData("EAQ 2026_49002")]
    [InlineData("eaq_2024_37569_v_1")]
    [InlineData("EAQ_24_37569_V_1")]
    [InlineData("EAQ_2024_")]
    [InlineData("EAQ_2024_37569' OR 1=1")]
    [InlineData("ORC-52")]
    [InlineData("EAQ_٢٠٢٤_37569_V_1")]
    public void QualquerOutraFormaNaoProduzChave(string? quotationCode)
    {
        Assert.Null(LaboratoryQuoteKey.FromQuotationCode(quotationCode));
    }

    [Fact]
    public void ACotacaoDoOrcamentoDefineAChaveEOCodigoPublicoNao()
    {
        Quote withQuotation = QuoteWith(code: "EAQ_2099_99999_V_1", quotationCode: "EAQ_2024_37569_V_1");
        Quote withoutQuotation = QuoteWith(code: "2024_37569", quotationCode: null);

        Assert.Equal("2024_37569", withQuotation.LaboratoryKey?.Value);
        Assert.Null(withoutQuotation.LaboratoryKey);
    }

    [Theory]
    [InlineData("2024_37569", true)]
    [InlineData("2024_37569   ", true)]
    [InlineData("2024_37569_UHAB Agua Boa", false)]
    [InlineData("2024_3756", false)]
    [InlineData("EAQ_2024_37569_V_1", false)]
    [InlineData(null, false)]
    public void ACasaComOOrcamentoCrmInteiroAparado(string? laboratoryCode, bool expected)
    {
        LaboratoryQuoteKey key = LaboratoryQuoteKey.FromQuotationCode("EAQ_2024_37569_V_1")!;

        Assert.Equal(expected, key.Matches(laboratoryCode));
    }

    private static Quote QuoteWith(string code, string? quotationCode) => new(
        id: "quote-1",
        opportunityId: "opportunity-1",
        code: code,
        name: "Pacote de ensaios",
        status: "Em Andamento",
        stage: "Em execução",
        description: "Orcamento de teste",
        companyName: "EMPRESA DE ENERGIA",
        externalContactName: "Equipe Comercial",
        externalContactEmail: "comercial@exemplo.com",
        totalPrice: 1000m,
        createdDate: null,
        quotationCode: quotationCode);
}
