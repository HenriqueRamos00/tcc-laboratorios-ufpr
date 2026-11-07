using System.Text.RegularExpressions;

using Portal.Domain.Pipeline;

namespace Portal.Domain.Quotes;

/// <summary>
/// O degrau em que a fonte comercial colocou um orcamento, lido do texto livre que ela
/// escreve em <see cref="Quote.Stage"/>.
///
/// E o par de <c>recognizeStage</c> de <c>frontend/src/app/model/quote-presentation.ts</c>,
/// entrada por entrada. Os dois lados existem porque os dois decidem a mesma coisa sobre o
/// mesmo orcamento: a tela decide se desenha o botao de baixar, e a aplicacao decide se
/// serve o arquivo. Reconhecer de um jeito aqui e de outro la faz a tela oferecer um
/// download que a rota recusa, e o cliente ve um botao que so da erro.
///
/// Encerrado nao e um degrau: cancelado e recusado sao o fim da progressao, e por isso
/// <see cref="Reached"/> e nula neles. Os dois chegam ao mesmo lugar porque nenhuma regra
/// deste modulo os separa; quem precisar separar acrescenta o motivo do encerramento, e nao
/// um degrau a escada.
///
/// Texto que nenhuma entrada e nenhum radical reconhece vira
/// <see cref="PipelineStage.Qualification"/>, o degrau mais baixo, como no frontend.
/// Devolver ausencia faria cada chamador inventar um padrao proprio, e o padrao mais
/// generoso publicaria orcamento que ninguem terminou de escrever.
///
/// A ordem de declaracao dos membros nao e estetica: <see cref="Closed"/> e lida pelas duas
/// tabelas abaixo, e inicializador estatico roda na ordem em que foi escrito. Declarada
/// depois delas, ela entraria nas tabelas como nulo.
/// </summary>
public sealed class CommercialStage
{
    /// <summary>A solicitacao foi encerrada: nao ha degrau, e nao ha para onde avancar.</summary>
    public static CommercialStage Closed { get; } = new(null);

    private const RegexOptions RadicalOptions =
        RegexOptions.CultureInvariant | RegexOptions.ExplicitCapture;

    private static readonly Dictionary<string, CommercialStage> ByName =
        new(StringComparer.Ordinal)
        {
            ["qualificação"] = At(PipelineStage.Qualification),
            ["em análise pela área"] = At(PipelineStage.AreaAnalysis),
            ["em revisão"] = At(PipelineStage.AreaAnalysis),
            ["elaborando proposta"] = At(PipelineStage.DraftingProposal),
            ["negociação"] = At(PipelineStage.Negotiation),
            ["aprovado pelo cliente"] = At(PipelineStage.ApprovedByClient),
            ["aguardando entrega da amostra"] = At(PipelineStage.AwaitingSample),
            ["fechado ganho"] = At(PipelineStage.Received),
            ["protocolado"] = At(PipelineStage.ProtocolAssigned),
            ["em execução"] = At(PipelineStage.InProgress),
            ["completo"] = At(PipelineStage.Complete),
            ["validado"] = At(PipelineStage.Validated),
            ["finalizado"] = At(PipelineStage.Finalized),
            ["elaborando relatório"] = At(PipelineStage.DraftingReport),
            ["relatório publicado"] = At(PipelineStage.ReportPublished),
            ["fechado perdido"] = Closed,
            ["fechado recusado lactec"] = Closed,
            ["cancelado"] = Closed
        };

    private static readonly (Regex Radical, CommercialStage Recognized)[] ByRadical =
    [
        (new Regex("(perdid|recus|cancel|reject|denied|negad)", RadicalOptions), Closed),
        (new Regex("(ganho|won|recebid|conclu|complete)", RadicalOptions), At(PipelineStage.Received)),
        (new Regex("amostra", RadicalOptions), At(PipelineStage.AwaitingSample)),
        (new Regex("(aprovad|aceit|accept|approv)", RadicalOptions), At(PipelineStage.ApprovedByClient)),
        (new Regex("negocia", RadicalOptions), At(PipelineStage.Negotiation)),
        (new Regex("(elabor|draft|propost)", RadicalOptions), At(PipelineStage.DraftingProposal)),
        (new Regex("(análise|analise|revis|review|analysis)", RadicalOptions), At(PipelineStage.AreaAnalysis))
    ];

    private CommercialStage(PipelineStage? reached)
    {
        Reached = reached;
    }

    public PipelineStage? Reached { get; }

    public bool IsClosed => Reached is null;

    public static CommercialStage At(PipelineStage reached) => new(reached);

    /// <summary>
    /// O degrau como a fonte o escreveu. A comparacao exata cobre os valores conhecidos do
    /// Salesforce; os radicais existem para variacao futura de rotulo nao derrubar a leitura
    /// inteira, e sao conferidos na mesma ordem do frontend, porque "fechado recusado" casa
    /// com mais de um deles e so o primeiro esta certo.
    /// </summary>
    public static CommercialStage AsTheSourceWroteIt(string? stageWrittenBySource)
    {
        string stage = (stageWrittenBySource ?? string.Empty).Trim().ToLowerInvariant();

        if (ByName.TryGetValue(stage, out CommercialStage? named)) return named;

        foreach ((Regex radical, CommercialStage recognized) in ByRadical)
            if (radical.IsMatch(stage)) return recognized;

        return At(PipelineStage.Qualification);
    }
}
