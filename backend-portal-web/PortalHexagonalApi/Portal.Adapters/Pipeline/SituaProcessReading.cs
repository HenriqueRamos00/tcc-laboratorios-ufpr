using Portal.Domain.Pipeline;

namespace Portal.Adapters.Pipeline;

/// <summary>
/// O unico lugar do sistema que conhece os numeros de <c>SituaProcess</c>. Traduzir o
/// codigo da fonte para o vocabulario do portal e trabalho de adaptador: "SituacaoEnsaios
/// igual a 2" e um fato sobre o esquema do AutoLAB, e se ele morasse no dominio o dominio
/// passaria a saber que existe um banco do outro lado.
///
/// Quem decide a etapa nao e este arquivo: e <see cref="ReachedStage"/>, no dominio. Aqui
/// so se le o que cada coluna afirma. O corte e esse de proposito: a escada, a porteira do
/// recebimento e o resumo pela amostra menos avancada sao regra de negocio e sao testaveis
/// sem fonte nenhuma; a tabela de codigos e traducao e muda quando a fonte muda.
///
/// A porteira pede as duas colunas de entrada. O dono decidiu <c>Recebido</c>, e nas 3345
/// linhas medidas <c>Recebido</c> e <c>Protocolado</c> nunca divergem: so existem (1,1) com
/// 2570 linhas e (0,0) com 775. Exigir as duas e portanto identico no dado real e mais
/// conservador no dado que ninguem viu, e deixa as cinco colunas da consulta carregando
/// peso em vez de uma delas ficar decorativa.
/// </summary>
public static class SituaProcessReading
{
    /// <summary>
    /// A etapa dos orcamentos pedidos, resumida por codigo. A comparacao e pelo
    /// <c>OrcamentoCRM</c> inteiro, aparado nas pontas e sem distincao de caixa, nunca por
    /// prefixo: a consulta tem tres formas de codigo (<c>2025_41663</c>,
    /// <c>2024_40915_UHCB Canabrava</c> e <c>EAQ 2025_42965</c>), e casar por prefixo
    /// funde orcamentos distintos, porque o sufixo livre e o que distingue uma usina da
    /// outra.
    /// </summary>
    public static IReadOnlyList<LaboratoryProgress> ProgressOf(
        IEnumerable<SituaProcessRow> rows,
        IReadOnlyCollection<string> quoteCodes)
    {
        ArgumentNullException.ThrowIfNull(rows);
        ArgumentNullException.ThrowIfNull(quoteCodes);

        HashSet<string> wanted = new(
            quoteCodes
                .Where(code => !string.IsNullOrWhiteSpace(code))
                .Select(code => code.Trim()),
            StringComparer.OrdinalIgnoreCase);

        if (wanted.Count == 0) return [];

        return [.. rows
            .Where(row => wanted.Contains(QuoteCodeOf(row)))
            .GroupBy(QuoteCodeOf, StringComparer.OrdinalIgnoreCase)
            .Select(ProgressOfOneQuote)
            .OfType<LaboratoryProgress>()];
    }

    /// <summary>
    /// A etapa de uma linha, que e uma amostra. Exposta porque a tabela de codigos e a
    /// parte desta fatia que mais precisa de teste proprio: cada valor de cada coluna e uma
    /// classe de equivalencia, e os limites 0, 1, 2, 3 e acima de 3 sao onde o erro mora.
    /// </summary>
    public static PipelineStage? StageOfOneRow(SituaProcessRow row)
    {
        ArgumentNullException.ThrowIfNull(row);

        return ReachedStage.OfOneSample(EnteredTheLaboratory(row), StagesReportedBy(row));
    }

    private static bool EnteredTheLaboratory(SituaProcessRow row)
    {
        return row.Recebido == 1 && row.Protocolado == 1;
    }

    /// <summary>
    /// O que as duas colunas de situacao afirmam, em etapas do portal.
    ///
    /// As duas entram juntas e sem precedencia, porque e a escada que decide: nas 69 linhas
    /// medidas com <c>SituacaoEnsaios</c> igual a 2 e <c>SolicitacaoRelatorio</c> igual a 1,
    /// a etapa e "elaborando relatorio" e nao "validado", e no instante em que
    /// <c>SolicitacaoRelatorio</c> vira 1 a etapa sobe sem que <c>SituacaoEnsaios</c> mude.
    /// </summary>
    private static IEnumerable<PipelineStage> StagesReportedBy(SituaProcessRow row)
    {
        yield return StageOfTesting(row.SituacaoEnsaios);

        if (StageOfReport(row.SolicitacaoRelatorio) is PipelineStage reportStage)
            yield return reportStage;
    }

    /// <summary>
    /// A tabela de <c>SituacaoEnsaios</c>. Nulo vale zero, por decisao do dono, e valor
    /// abaixo de zero tambem: a leitura que nao promove ninguem e a unica honesta para um
    /// numero que a fonte nunca produziu. De 3 para cima tudo para em finalizado, sem
    /// variacao, porque 3, 4 e 5 significam a mesma coisa para quem espera o resultado.
    /// </summary>
    private static PipelineStage StageOfTesting(int? situacaoEnsaios)
    {
        return (situacaoEnsaios ?? 0) switch
        {
            <= 0 => PipelineStage.InProgress,
            1 => PipelineStage.Complete,
            2 => PipelineStage.Validated,
            _ => PipelineStage.Finalized
        };
    }

    /// <summary>
    /// A tabela de <c>SolicitacaoRelatorio</c>. Zero nao afirma etapa nenhuma, e qualquer
    /// valor fora de 1 e 2 tambem nao: o degrau mais alto da escada e relatorio publicado,
    /// e promover um numero desconhecido ate ele diria ao cliente que o laudo esta pronto.
    /// </summary>
    private static PipelineStage? StageOfReport(int? solicitacaoRelatorio)
    {
        return solicitacaoRelatorio switch
        {
            1 => PipelineStage.DraftingReport,
            2 => PipelineStage.ReportPublished,
            _ => null
        };
    }

    private static string QuoteCodeOf(SituaProcessRow row)
    {
        return row.OrcamentoCRM?.Trim() ?? string.Empty;
    }

    private static LaboratoryProgress? ProgressOfOneQuote(IGrouping<string, SituaProcessRow> rowsOfOneQuote)
    {
        PipelineStage? stage = ReachedStage.OfAQuote([.. rowsOfOneQuote.Select(StageOfOneRow)]);

        return stage is null ? null : new LaboratoryProgress(rowsOfOneQuote.Key, stage.Value);
    }
}
