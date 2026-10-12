namespace Portal.Application.LabResults.Exceptions;

/// <summary>
/// A fonte de ensaios laboratoriais nao respondeu a consulta. Pertence ao contrato da porta
/// conduzida, e nao ao adaptador: quem chama a porta precisa poder tratar essa falha sem
/// saber se do outro lado ha um SQL Server, um arquivo embutido ou um servico HTTP.
///
/// A mensagem fica no log e nao vai para o fio: ela vem da infraestrutura e pode carregar
/// nome de servidor e caminho interno, que nao sao assunto de quem fez a requisicao.
/// </summary>
public sealed class LabTestSourceUnavailableException : Exception
{
    public LabTestSourceUnavailableException(string message)
        : base(message)
    {
    }

    public LabTestSourceUnavailableException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
