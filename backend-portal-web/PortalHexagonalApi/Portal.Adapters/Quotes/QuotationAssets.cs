using System.Reflection;

using Portal.Adapters.Quotes.Exceptions;

using QuestPDF.Infrastructure;

namespace Portal.Adapters.Quotes;

/// <summary>
/// As tres imagens do papel timbrado, embutidas no assembly. Elas viajam dentro do binario
/// pelo mesmo motivo da amostra de ensaios: a suite e a API desenham exatamente o mesmo
/// arquivo, e o PDF gerado dentro do contentor nao depende de nada copiado para o disco ao
/// lado do executavel.
///
/// Cada imagem e decodificada uma vez e reaproveitada: a faixa do topo e a do rodape
/// aparecem em todas as paginas de todos os documentos, e decodificar o JPEG a cada pagina
/// seria trabalho repetido sem nenhum efeito no resultado.
/// </summary>
internal static class QuotationAssets
{
    public const string HeaderBannerResourceName =
        "Portal.Adapters.Quotes.Assets.header-banner.png";

    public const string FooterStripesResourceName =
        "Portal.Adapters.Quotes.Assets.footer-stripes.png";

    public const string SocialNetworksResourceName =
        "Portal.Adapters.Quotes.Assets.social-networks.png";

    private static readonly Lazy<Image> HeaderBannerImage = Decoded(HeaderBannerResourceName);
    private static readonly Lazy<Image> FooterStripesImage = Decoded(FooterStripesResourceName);
    private static readonly Lazy<Image> SocialNetworksImage = Decoded(SocialNetworksResourceName);

    public static Image HeaderBanner => HeaderBannerImage.Value;

    public static Image FooterStripes => FooterStripesImage.Value;

    public static Image SocialNetworks => SocialNetworksImage.Value;

    private static Lazy<Image> Decoded(string resourceName) =>
        new(() => Image.FromBinaryData(Read(resourceName)), LazyThreadSafetyMode.ExecutionAndPublication);

    private static byte[] Read(string resourceName)
    {
        Assembly assembly = typeof(QuotationAssets).Assembly;
        using Stream? resource = assembly.GetManifestResourceStream(resourceName);

        if (resource is null) throw new QuotationAssetMissingException(resourceName);

        using MemoryStream content = new();
        resource.CopyTo(content);

        return content.ToArray();
    }
}
