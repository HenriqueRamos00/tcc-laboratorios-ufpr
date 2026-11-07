namespace Portal.Application.Pipeline.Exceptions;

/// <summary>
/// A fonte que informa a etapa do laboratorio nao respondeu a consulta. Pertence ao
/// contrato da porta conduzida, e nao ao adaptador: quem chama a porta trata essa falha sem
/// saber se do outro lado ha um SQL Server, uma amostra escrita a mao ou um servico HTTP.
///
/// E separada da falha da fonte de orcamentos de proposito. Uma excecao que servisse as
/// duas nao diria qual fonte caiu, e quem atende o cliente iria olhar o Salesforce quando
/// quem parou foi o AutoLAB.
///
/// A mensagem fica no log e nao vai para o fio: ela vem da infraestrutura e pode carregar
/// nome de servidor e caminho interno, que nao sao assunto de quem fez a requisicao.
/// </summary>
public sealed class LaboratoryProgressSourceUnavailableException : Exception
{
    public LaboratoryProgressSourceUnavailableException(string message)
        : base(message)
    {
    }

    public LaboratoryProgressSourceUnavailableException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
