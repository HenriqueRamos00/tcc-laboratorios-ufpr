using Portal.Application.Visibility;

namespace Portal.Application.CompanyRecords;

/// <summary>
/// Porta conduzida: a fonte dos registros do laboratorio que pertencem as empresas do
/// escopo. E o unico lugar por onde o CNPJ da sessao vira registro de laboratorio, e todo
/// adaptador que le ensaio, relatorio, arquivo assinado ou etapa pergunta aqui antes de ler.
///
/// Ha um unico adaptador desta porta. Hoje ele percorre as tabelas do AutoLAB (cliente,
/// solicitacao, amostra, protocolo, emissao); quando existir uma view do portal com a coluna
/// de CNPJ, a troca e escrever outro adaptador desta mesma porta com um
/// <c>SELECT ... WHERE Cnpj IN (...)</c>, sem tocar em controller, aplicacao ou nos demais
/// adaptadores.
///
/// Escopo vazio devolve colecao vazia. A porta nao tem operacao que liste sem escopo.
/// </summary>
public interface ICompanyRecordsSource
{
    /// <summary>
    /// Os protocolos Lactec das amostras das empresas do escopo, como a fonte os guarda. E a
    /// chave pela qual as linhas de ensaio sao lidas.
    /// </summary>
    Task<IReadOnlyCollection<string>> GetProtocolsAsync(CompanyScope scope, CancellationToken cancellationToken);

    /// <summary>
    /// As emissoes de relatorio vivas das solicitacoes das empresas do escopo, uma linha por
    /// emissao, sem ordem garantida.
    /// </summary>
    Task<IReadOnlyList<CompanyReportRow>> GetReportsAsync(CompanyScope scope, CancellationToken cancellationToken);

    /// <summary>
    /// Os codigos de orcamento (<c>OrcamentoCRM</c>) das solicitacoes das empresas do escopo.
    /// </summary>
    Task<IReadOnlyCollection<string>> GetQuoteCodesAsync(CompanyScope scope, CancellationToken cancellationToken);
}
