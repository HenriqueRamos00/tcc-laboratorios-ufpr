namespace Portal.Domain.Pipeline.Exceptions;

/// <summary>
/// Pediu-se a altura de um degrau que a escada nao declara.
///
/// Nao e dado ruim da fonte: e degrau acrescentado ao enum e esquecido na ordem, ou valor
/// inteiro convertido a forca para o enum. O degrau recusado viaja na mensagem porque e ele
/// que diz qual linha falta escrever em <c>PipelineLadder.InOrder</c>.
/// </summary>
public sealed class StageOutsideTheLadderException : Exception
{
    public StageOutsideTheLadderException(PipelineStage stage)
        : base($"Etapa fora da ordem declarada da progressao: '{stage}'.")
    {
        Stage = stage;
    }

    public PipelineStage Stage { get; }
}
