using Portal.Application.Pipeline;
using Portal.Domain.Pipeline;

namespace Portal.Adapters.Pipeline;

/// <summary>
/// Adaptador conduzido que serve uma amostra de <c>SituaProcess</c> guardada em memoria. O
/// nome diz a tecnologia que ele de fato usa: nao ha conexao, nao ha transacao, nao ha
/// latencia e ele nao imita nada disso. Chamar um adaptador assim de "fake SQL Server"
/// prometeria uma fidelidade que ele nao entrega e ocuparia a vaga do adaptador real.
///
/// A amostra inteira foi escrita a mao, e nenhum numero dela saiu do AutoLAB. Ela tem uma
/// procedencia so, e por isso um arquivo so: a consulta real nao tem nome de cliente, mas
/// o <c>OrcamentoCRM</c> e identificador de negocio e o sufixo livre carrega nome de usina,
/// entao o que ficou aqui e a FORMA medida com codigo inventado. As combinacoes de valores
/// sao as do censo das 3345 linhas, uma por classe, e nao a contagem delas.
///
/// Ela atende dois propositos declarados. Os codigos <c>EAQ_2026_454...</c> sao os mesmos
/// de <c>FakeSalesforceQuoteRepository</c>, para a demonstracao mostrar a etapa do
/// laboratorio passando na frente da etapa comercial. Os codigos <c>2026_49...</c> nao
/// pertencem a orcamento nenhum da demonstracao e existem para a suite exercitar as tres
/// formas de codigo, a linha errada na origem e a combinacao que a amostra real nao tem.
/// </summary>
public sealed class InMemorySituaProcessRepository : ILaboratoryProgressRepository
{
    private static readonly IReadOnlyList<SituaProcessRow> Rows =
    [
        // Orcamentos da demonstracao: a etapa do laboratorio vai alem da etapa comercial.
        new SituaProcessRow
        {
            OrcamentoCRM = "EAQ_2026_45490_V_1",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 0, SolicitacaoRelatorio = 0
        },
        new SituaProcessRow
        {
            OrcamentoCRM = "EAQ_2026_45491_V_1",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 1, SolicitacaoRelatorio = 0
        },
        new SituaProcessRow
        {
            OrcamentoCRM = "EAQ_2026_45492_V_1",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 2, SolicitacaoRelatorio = 0
        },
        new SituaProcessRow
        {
            OrcamentoCRM = "EAQ_2026_45493_V_1",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 3, SolicitacaoRelatorio = 0
        },
        // As 69 linhas medidas em que o relatorio comeca antes de o ensaio sair de validado.
        new SituaProcessRow
        {
            OrcamentoCRM = "EAQ_2026_45494_V_1",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 2, SolicitacaoRelatorio = 1
        },
        // A combinacao mais comum da fonte: 2268 das 3345 linhas.
        new SituaProcessRow
        {
            OrcamentoCRM = "EAQ_2026_45495_V_1",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 3, SolicitacaoRelatorio = 2
        },
        new SituaProcessRow
        {
            OrcamentoCRM = "EAQ_2026_45496_V_1",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 5, SolicitacaoRelatorio = 2
        },
        // Orcamento com amostras em pes diferentes, que e o caso dos 46 codigos repetidos
        // com estados divergentes: o resumo honesto e ausencia de etapa.
        new SituaProcessRow
        {
            OrcamentoCRM = "EAQ_2026_45499_V_1",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 3, SolicitacaoRelatorio = 2
        },
        new SituaProcessRow
        {
            OrcamentoCRM = "EAQ_2026_45499_V_1",
            Recebido = 0, Protocolado = 0, SituacaoEnsaios = null, SolicitacaoRelatorio = 0
        },
        // As 687 linhas que so existem no Salesforce, com SituacaoEnsaios nulo.
        new SituaProcessRow
        {
            OrcamentoCRM = "EAQ_2026_45482_V_1",
            Recebido = 0, Protocolado = 0, SituacaoEnsaios = null, SolicitacaoRelatorio = 0
        },
        // As tres formas de OrcamentoCRM, com codigo inventado. As duas primeiras sao
        // prefixo uma da outra de proposito: casar por prefixo as fundiria.
        new SituaProcessRow
        {
            OrcamentoCRM = "2026_49001",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 4, SolicitacaoRelatorio = 2
        },
        new SituaProcessRow
        {
            OrcamentoCRM = "2026_49001_UHAB Agua Boa",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 0, SolicitacaoRelatorio = 0
        },
        new SituaProcessRow
        {
            OrcamentoCRM = "EAQ 2026_49002",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 3, SolicitacaoRelatorio = 1
        },
        // A forma da unica linha lancada errada na origem: ensaio completo sem recebimento.
        new SituaProcessRow
        {
            OrcamentoCRM = "2026_49003",
            Recebido = 0, Protocolado = 0, SituacaoEnsaios = 1, SolicitacaoRelatorio = 0
        },
        // Recebido sem protocolo, combinacao que nao existe nas 3345 linhas.
        new SituaProcessRow
        {
            OrcamentoCRM = "2026_49004",
            Recebido = 1, Protocolado = 0, SituacaoEnsaios = 3, SolicitacaoRelatorio = 2
        },
        // Linha sem codigo de orcamento, que nao pode casar com orcamento nenhum.
        new SituaProcessRow
        {
            OrcamentoCRM = "",
            Recebido = 1, Protocolado = 1, SituacaoEnsaios = 3, SolicitacaoRelatorio = 2
        }
    ];

    public Task<IReadOnlyList<LaboratoryProgress>> GetProgressOfQuotesAsync(
        IReadOnlyCollection<string> quoteCodes,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(quoteCodes);

        return Task.FromResult(SituaProcessReading.ProgressOf(Rows, quoteCodes));
    }

    public Task<LaboratoryProgress?> GetProgressOfQuoteAsync(
        string quoteCode,
        CancellationToken cancellationToken)
    {
        LaboratoryProgress? found = SituaProcessReading
            .ProgressOf(Rows, new[] { quoteCode })
            .FirstOrDefault();

        return Task.FromResult(found);
    }

    /// <summary>
    /// Expoe a amostra para a suite poder afirmar sobre as combinacoes escritas sem repetir
    /// a tabela dentro do teste.
    /// </summary>
    public static IReadOnlyList<SituaProcessRow> ReadWrittenSample()
    {
        return Rows;
    }
}
