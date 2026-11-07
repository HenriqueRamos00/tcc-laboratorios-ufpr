namespace Portal.Application.Nameplate.Exceptions;

/// <summary>
/// O cadastro de equipamentos nao respondeu a consulta. Pertence ao contrato da porta
/// conduzida, e nao ao adaptador: quem chama a porta trata essa falha sem saber se do outro
/// lado ha um SQL Server, uma amostra escrita a mao ou ninguem.
///
/// E separada da falha da fonte de ensaios de proposito, mesmo com as duas fontes morando
/// hoje no mesmo banco. Uma excecao unica para as duas nao diria qual consulta caiu, e quem
/// atende o cliente iria investigar a view de ensaios quando quem nao respondeu foi a tabela
/// de cadastro. E o mesmo erro que uma excecao unica para Salesforce e AutoLAB ja cometeu
/// neste projeto.
///
/// A mensagem fica no log e nao vai para o fio: ela vem da infraestrutura e pode carregar
/// nome de servidor e caminho interno, que nao sao assunto de quem fez a requisicao.
/// Mensagem de falha desta fonte tambem nao pode interpolar <c>NumSerie</c> nem <c>Tag</c>,
/// porque dez linhas do cadastro carregam nome de cliente real dentro das duas colunas.
/// </summary>
public sealed class NameplateSourceUnavailableException : Exception
{
    public NameplateSourceUnavailableException(string message)
        : base(message)
    {
    }

    public NameplateSourceUnavailableException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
