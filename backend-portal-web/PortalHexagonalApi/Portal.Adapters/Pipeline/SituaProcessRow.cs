namespace Portal.Adapters.Pipeline;

/// <summary>
/// Uma linha de <c>select * from SituaProcess</c>, com os nomes das colunas da consulta.
/// Mora no adaptador porque e vocabulario da fonte, nao do dominio, e os nomes ficam como
/// o AutoLAB escreve porque traduzir nome de coluna faz perder o rastro ate o banco.
///
/// Sao cinco colunas e nada mais. Nao existe coluna que identifique a amostra, e e por isso
/// que <c>OrcamentoCRM</c> nao e chave: nas 3345 linhas medidas, 76 codigos repetem e 46
/// deles com estados diferentes. A granularidade real e por amostra e esta consulta nao a
/// expoe.
///
/// Os quatro inteiros sao anulaveis. <c>SituacaoEnsaios</c> porque o dado medido tem 690
/// linhas nulas; os outros tres porque o censo diz que hoje nunca vem nulos, e campo nulo
/// onde a fonte promete valor e justamente a situacao que derruba a listagem inteira em
/// producao depois de passar verde na suite. Nulo e lido como zero, isto e, como "nao", que
/// e a leitura que nao promove ninguem.
/// </summary>
public sealed record SituaProcessRow
{
    public string OrcamentoCRM { get; init; } = string.Empty;

    public int? Recebido { get; init; }

    public int? Protocolado { get; init; }

    public int? SituacaoEnsaios { get; init; }

    public int? SolicitacaoRelatorio { get; init; }
}
