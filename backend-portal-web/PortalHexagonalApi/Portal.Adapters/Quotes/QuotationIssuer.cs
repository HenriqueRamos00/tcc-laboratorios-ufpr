namespace Portal.Adapters.Quotes;

/// <summary>
/// O emitente do orcamento. Esses valores nao vem do Salesforce: sao do papel timbrado do
/// Lactec e mudam por decisao administrativa, nao por orcamento. Ficam aqui, e nao numa
/// tabela, porque hoje nao existe lugar no dominio que os represente; quando existir, eles
/// migram e esta classe some.
///
/// O numero do mandado de seguranca na secao de impostos tem validade juridica e pode ser
/// substituido: quem for mexer nele precisa confirmar com o juridico, nao com o desenho do
/// documento.
/// </summary>
internal static class QuotationIssuer
{
    public const string LegalName = "Instituto de Tecnologia para o Desenvolvimento";
    public const string Registration = "01.715.975/0001-69";
    public const string StateRegistration = "901.29788-60";

    public const string BillingAddress =
        "Av. Comendador Franco, nº 1341 - Jardim Botânico - Curitiba/PR - Brasil - Cep: 80215-090";

    public const string GeneralPhone = "(41)3361-6200";
    public const string Website = "www.lactec.com.br";

    public const string DocumentRevision = "DOC-05_Rev.05 de jan/2026";
}
