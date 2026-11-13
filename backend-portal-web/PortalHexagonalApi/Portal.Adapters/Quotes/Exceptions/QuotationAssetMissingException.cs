namespace Portal.Adapters.Quotes.Exceptions;

/// <summary>
/// Uma das imagens do papel timbrado nao esta dentro do assembly. E defeito de empacotamento,
/// e nao dado ruim da fonte: o nome do recurso vem na mensagem porque o conserto e uma linha
/// de <c>EmbeddedResource</c> no <c>.csproj</c>, e nao uma consulta a lugar nenhum.
///
/// Mora no adaptador porque nao atravessa porta nenhuma: quem desenha o documento a converte
/// na falha que a porta ja declara.
/// </summary>
public sealed class QuotationAssetMissingException : Exception
{
    public QuotationAssetMissingException(string resourceName)
        : base($"Recurso embutido '{resourceName}' não encontrado.")
    {
        ResourceName = resourceName;
    }

    public string ResourceName { get; }
}
