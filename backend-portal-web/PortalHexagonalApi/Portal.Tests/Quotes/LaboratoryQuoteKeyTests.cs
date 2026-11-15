using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.Quotes;

/// <summary>
/// A regra de correlacao entre a cotacao do Salesforce e o <c>OrcamentoCRM</c> do AutoLAB.
/// A sigla da area e a versao sao opcionais e ficam de fora da chave nua; qualquer outra
/// forma nao vira chave nenhuma.
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
    [InlineData("SET_2026_47711_V_1", "2026_47711")]
    [InlineData("SET_2026_47711", "2026_47711")]
    [InlineData("PED_2025_1205_V_3", "2025_1205")]
    [InlineData("EAQ_2026_1205_V1", "2026_1205")]
    [InlineData("SET_2026_47711_V12", "2026_47711")]
    [InlineData("2026_47711_V1", "2026_47711")]
    [InlineData("2026_47711_V_1", "2026_47711")]
    [InlineData(" SET_2026_47711_V1 ", "2026_47711")]
    public void ASiglaDaAreaEAVersaoFicamDeForaDaChaveNua(string quotationCode, string expected)
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
    [InlineData("set_2026_47711_V_1")]
    [InlineData("Set_2026_47711_V_1")]
    [InlineData("SET_2026_47711_v1")]
    [InlineData("SET_2026_47711_V")]
    [InlineData("SET_2026_47711_V1_extra")]
    [InlineData("SET_2026_47711_V__1")]
    [InlineData("SET_2026_47711V1")]
    [InlineData("SET_2026_47711_1")]
    [InlineData("SET 2026_47711")]
    [InlineData("SET-2026_47711")]
    [InlineData("SET2026_47711")]
    [InlineData("_2026_47711")]
    [InlineData("S_2026_47711")]
    [InlineData("S3T_2026_47711")]
    [InlineData("SET_EAQ_2026_47711")]
    [InlineData("ABCDEFGHIJK_2026_47711")]
    [InlineData("ÁREA_2026_47711")]
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
