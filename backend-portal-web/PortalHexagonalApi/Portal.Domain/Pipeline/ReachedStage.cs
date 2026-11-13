namespace Portal.Domain.Pipeline;

/// <summary>
/// As duas regras que decidem a etapa, e nenhum codigo de coluna de fonte nenhuma.
///
/// A primeira e a porteira do recebimento. Enquanto a amostra nao entrou no laboratorio,
/// nada que o laboratorio reporte vale, e isso nao e um tratamento de dado inconsistente:
/// e a propria estrutura da regra. Um registro que diz "ensaio completo" sem recebimento
/// nao e corrigido aqui, nem acusado, nem promovido; ele simplesmente nao alcanca a metade
/// do laboratorio, pelo mesmo caminho das amostras que ainda nao chegaram. Existe
/// exatamente uma linha assim nas 3345 medidas, e ela esta lancada errado na origem.
///
/// A segunda e a direcao da dobra. Dentro de uma amostra vale o degrau mais alto que os
/// campos justificam, porque cada campo e a prova de que aquele marco aconteceu. Entre as
/// amostras de um mesmo orcamento vale o degrau mais baixo, porque <c>OrcamentoCRM</c> nao
/// e chave e o resumo nao pode prometer ao cliente um relatorio publicado enquanto outra
/// amostra do mesmo orcamento nem chegou.
/// </summary>
public static class ReachedStage
{
    /// <summary>
    /// A etapa de uma amostra, ou nenhuma quando o laboratorio nao tem nada a dizer sobre
    /// ela.
    ///
    /// O piso e <see cref="PipelineStage.Received"/>: amostra que entrou no laboratorio foi,
    /// no minimo, recebida. O piso existe para a funcao ser total, e nao porque alguma fonte
    /// de hoje chega a ele: pelo dado medido, o que o AutoLAB reporta sempre comeca em
    /// execucao ou acima.
    /// </summary>
    public static PipelineStage? OfOneSample(
        bool sampleEnteredTheLaboratory,
        IEnumerable<PipelineStage> stagesTheLaboratoryReports)
    {
        ArgumentNullException.ThrowIfNull(stagesTheLaboratoryReports);

        if (!sampleEnteredTheLaboratory) return null;

        List<PipelineStage> reported = [PipelineStage.Received, .. stagesTheLaboratoryReports];

        return PipelineLadder.Highest(reported);
    }

    /// <summary>
    /// A etapa de um orcamento, a partir da etapa de cada amostra dele.
    ///
    /// Uma amostra sem etapa derruba o resumo inteiro, e nao e descartada da conta. Pular as
    /// ausencias devolveria "relatorio publicado" para um orcamento que tem uma amostra
    /// ainda no comercial, e e esse resultado bonito e falso que a tela mostraria ao cliente.
    /// Sem amostra nenhuma tambem nao ha resumo: nao ha o que afirmar.
    /// </summary>
    public static PipelineStage? OfAQuote(IEnumerable<PipelineStage?> stageOfEachSample)
    {
        ArgumentNullException.ThrowIfNull(stageOfEachSample);

        List<PipelineStage?> received = [.. stageOfEachSample];

        if (received.Count == 0) return null;
        if (received.Any(stage => stage is null)) return null;

        List<PipelineStage> known = [.. received.Select(stage => stage!.Value)];

        return PipelineLadder.LeastAdvanced(known);
    }
}
