using Portal.Domain.Pipeline.Exceptions;

namespace Portal.Domain.Pipeline;

/// <summary>
/// Em que degrau da progressao o laboratorio colocou um orcamento. E o que atravessa a
/// porta conduzida, e por isso nao tem nada da fonte dentro: nenhum inteiro de
/// <c>SituacaoEnsaios</c>, nenhuma nocao de que do outro lado exista um SQL Server.
///
/// So existe quando ha etapa. Orcamento que o laboratorio nao alcancou nao vira um
/// progresso com etapa vazia: ele nao vira progresso nenhum, e quem pergunta recebe
/// ausencia. Etapa vazia obrigaria todo chamador a lembrar de testar o campo, e quem
/// esquecesse publicaria qualificacao para um cliente que nem sabe que a amostra chegou.
/// </summary>
public sealed class LaboratoryProgress
{
    public LaboratoryProgress(string quoteCode, PipelineStage stage)
    {
        if (string.IsNullOrWhiteSpace(quoteCode)) throw new ProgressWithoutQuoteCodeException();

        QuoteCode = quoteCode.Trim();
        Stage = stage;
    }

    /// <summary>
    /// O codigo do orcamento a que esta etapa pertence, aparado nas pontas. Coluna
    /// <c>char</c> no SQL Server chega preenchida com espaco a direita, e um codigo com
    /// espaco sobrando nao casa com nenhum orcamento do portal.
    /// </summary>
    public string QuoteCode { get; }

    public PipelineStage Stage { get; }
}
