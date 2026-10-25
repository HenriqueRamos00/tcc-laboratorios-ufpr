using Portal.Application.Quotes;
using Portal.Adapters.Demo;
using Portal.Domain.Quotes;

namespace Portal.Adapters.Quotes;

public sealed class FakeSalesforceQuoteRepository(IQuoteCompanyScope? companyScope = null) : IQuoteRepository
{
    private static readonly List<FakeQuote> Quotes =
    [
        new FakeQuote(
            Id: "quote-3",
            CompanyCnpj: DemoCompanies.EnergyCnpj,
            Quote: new Quote(
                id: "quote-3",
                opportunityId: "opportunity-3",
                code: "EAQ_2026_45484_V_1",
                name: "Qualidade do ar: proposta de teste",
                status: "Pendente de Aceite",
                stage: "Negociação",
                description: "Avaliação da qualidade do ar: coleta de 3 amostras e análise laboratorial. Proposta fictícia para testes.",
                companyName: DemoCompanies.EnergyName,
                externalContactName: "Equipe Comercial",
                externalContactEmail: "comercial@example.com",
                totalPrice: 3450.00m,
                createdDate: new DateTimeOffset(2026, 10, 4, 9, 0, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-1",
            CompanyCnpj: DemoCompanies.EnergyCnpj,
            Quote: new Quote(
                id: "quote-1",
                opportunityId: "opportunity-1",
                code: "EAQ_2026_45482_V_1",
                name: "Análise físico-química de óleo isolante",
                status: "Pendente de Aceite",
                stage: "Qualificação",
                description: "Análise físico-química de óleo isolante",
                companyName: DemoCompanies.EnergyName,
                externalContactName: "Fulano de Tal",
                externalContactEmail: "fulano@exemplo.com",
                totalPrice: 56292.30m,
                createdDate: new DateTimeOffset(2026, 1, 15, 9, 30, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-4",
            CompanyCnpj: DemoCompanies.DistributorCnpj,
            Quote: new Quote(
                id: "quote-4",
                opportunityId: "opportunity-4",
                code: "EAQ_2026_45485_V_1",
                name: "Ensaio de rigidez dielétrica em óleo isolante",
                status: "Pendente de Aceite",
                stage: "Em análise pela área",
                description: "Medição de rigidez dielétrica em seis amostras de óleo mineral isolante.",
                companyName: DemoCompanies.DistributorName,
                externalContactName: "Mariana Alves",
                externalContactEmail: "mariana.alves@exemplo.com",
                totalPrice: 8740.00m,
                createdDate: new DateTimeOffset(2026, 3, 11, 10, 0, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-5",
            CompanyCnpj: DemoCompanies.DistributorCnpj,
            Quote: new Quote(
                id: "quote-5",
                opportunityId: "opportunity-5",
                code: "EAQ_2026_45486_V_1",
                name: "Cromatografia de gases dissolvidos em transformador de força",
                status: "Pendente de Aceite",
                stage: "Elaborando proposta",
                description: "Coleta e cromatografia de gases dissolvidos em três transformadores de subestação.",
                companyName: DemoCompanies.DistributorName,
                externalContactName: "Rafael Moreira",
                externalContactEmail: "rafael.moreira@exemplo.com",
                totalPrice: 21300.00m,
                createdDate: new DateTimeOffset(2026, 3, 24, 15, 30, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-6",
            CompanyCnpj: DemoCompanies.EnergyCnpj,
            Quote: new Quote(
                id: "quote-6",
                opportunityId: "opportunity-6",
                code: "EAQ_2026_45487_V_1",
                name: "Análise de teor de água em óleo isolante",
                status: "Em Andamento",
                stage: "Aprovado pelo cliente",
                description: "Determinação de teor de água por coulometria em quatro amostras.",
                companyName: DemoCompanies.EnergyName,
                externalContactName: "Camila Prado",
                externalContactEmail: "camila.prado@exemplo.com",
                totalPrice: 4980.00m,
                createdDate: new DateTimeOffset(2026, 4, 2, 9, 15, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-7",
            CompanyCnpj: DemoCompanies.DistributorCnpj,
            Quote: new Quote(
                id: "quote-7",
                opportunityId: "opportunity-7",
                code: "EAQ_2026_45488_V_1",
                name: "Contagem de partículas em óleo isolante",
                status: "Em Andamento",
                stage: "Aguardando entrega da amostra",
                description: "Contagem de partículas conforme norma, com laudo por amostra.",
                companyName: DemoCompanies.DistributorName,
                externalContactName: "Eduardo Lima",
                externalContactEmail: "eduardo.lima@exemplo.com",
                totalPrice: 6150.00m,
                createdDate: new DateTimeOffset(2026, 4, 17, 11, 45, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-8",
            CompanyCnpj: DemoCompanies.EnergyCnpj,
            Quote: new Quote(
                id: "quote-8",
                opportunityId: "opportunity-8",
                code: "EAQ_2026_45489_V_1",
                name: "Ensaio de tangente delta em buchas de transformador",
                status: "Em Andamento",
                stage: "Fechado ganho",
                description: "Medição de fator de dissipação e capacitância em oito buchas.",
                companyName: DemoCompanies.EnergyName,
                externalContactName: "Equipe de Manutenção",
                externalContactEmail: "manutencao@exemplo.com",
                totalPrice: 15800.00m,
                createdDate: new DateTimeOffset(2026, 5, 5, 8, 30, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-9",
            CompanyCnpj: DemoCompanies.DistributorCnpj,
            Quote: new Quote(
                id: "quote-9",
                opportunityId: "opportunity-9",
                code: "EAQ_2026_45490_V_1",
                name: "Análise de furfuraldeído em óleo isolante",
                status: "Em Andamento",
                stage: "Protocolado",
                description: "Quantificação de compostos furânicos para estimativa de envelhecimento do papel.",
                companyName: DemoCompanies.DistributorName,
                externalContactName: "Juliana Castro",
                externalContactEmail: "juliana.castro@exemplo.com",
                totalPrice: 9420.00m,
                createdDate: new DateTimeOffset(2026, 5, 21, 14, 0, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-10",
            CompanyCnpj: DemoCompanies.EnergyCnpj,
            Quote: new Quote(
                id: "quote-10",
                opportunityId: "opportunity-10",
                code: "EAQ_2026_45491_V_1",
                name: "Ensaio de índice de neutralização em óleo isolante",
                status: "Em Andamento",
                stage: "Em execução",
                description: "Determinação de acidez total em cinco amostras de óleo em serviço.",
                companyName: DemoCompanies.EnergyName,
                externalContactName: "",
                externalContactEmail: "contato.beta@exemplo.com",
                totalPrice: 3760.00m,
                createdDate: new DateTimeOffset(2026, 6, 8, 10, 20, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-11",
            CompanyCnpj: DemoCompanies.DistributorCnpj,
            Quote: new Quote(
                id: "quote-11",
                opportunityId: "opportunity-11",
                code: "EAQ_2026_45492_V_1",
                name: "Análise de compostos inibidores de oxidação",
                status: "Em Andamento",
                stage: "Completo",
                description: "Quantificação de inibidor de oxidação em óleo mineral isolante.",
                companyName: DemoCompanies.DistributorName,
                externalContactName: "Beatriz Nunes",
                externalContactEmail: "beatriz.nunes@exemplo.com",
                totalPrice: 7310.00m,
                createdDate: new DateTimeOffset(2026, 7, 3, 9, 0, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-12",
            CompanyCnpj: DemoCompanies.EnergyCnpj,
            Quote: new Quote(
                id: "quote-12",
                opportunityId: "opportunity-12",
                code: "EAQ_2026_45493_V_1",
                name: "Ensaio de tensão interfacial em óleo isolante",
                status: "Em Andamento",
                stage: "Validado",
                description: "Medição de tensão interfacial pelo método do anel em três amostras.",
                companyName: DemoCompanies.EnergyName,
                externalContactName: "André Figueiredo",
                externalContactEmail: "andre.figueiredo@exemplo.com",
                totalPrice: 4420.00m,
                createdDate: new DateTimeOffset(2026, 7, 22, 13, 40, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-13",
            CompanyCnpj: DemoCompanies.DistributorCnpj,
            Quote: new Quote(
                id: "quote-13",
                opportunityId: "opportunity-13",
                code: "EAQ_2026_45494_V_1",
                name: "Avaliação de papel isolante por grau de polimerização",
                status: "Finalizado",
                stage: "Finalizado",
                description: "Determinação do grau de polimerização em amostras de papel isolante.",
                companyName: DemoCompanies.DistributorName,
                externalContactName: "Letícia Barros",
                externalContactEmail: "leticia.barros@exemplo.com",
                totalPrice: 18950.00m,
                createdDate: new DateTimeOffset(2026, 8, 6, 10, 55, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-14",
            CompanyCnpj: DemoCompanies.EnergyCnpj,
            Quote: new Quote(
                id: "quote-14",
                opportunityId: "opportunity-14",
                code: "EAQ_2026_45495_V_1",
                name: "Cromatografia e físico-químico de rotina anual",
                status: "Finalizado",
                stage: "Elaborando relatório",
                description: "Pacote anual de cromatografia e ensaios físico-químicos em doze equipamentos.",
                companyName: DemoCompanies.EnergyName,
                externalContactName: "Equipe Comercial",
                externalContactEmail: "comercial@exemplo.com",
                totalPrice: 64180.00m,
                createdDate: new DateTimeOffset(2026, 8, 25, 15, 5, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-15",
            CompanyCnpj: DemoCompanies.DistributorCnpj,
            Quote: new Quote(
                id: "quote-15",
                opportunityId: "opportunity-15",
                code: "EAQ_2026_45496_V_1",
                name: "Ensaios físico-químicos em óleo de transformador de distribuição",
                status: "Finalizado",
                stage: "Relatório publicado",
                description: "Conjunto de ensaios físico-químicos com laudo publicado no portal.",
                companyName: DemoCompanies.DistributorName,
                externalContactName: "Sandra Teixeira",
                externalContactEmail: "sandra.teixeira@exemplo.com",
                totalPrice: 11270.00m,
                createdDate: new DateTimeOffset(2026, 9, 9, 8, 45, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-16",
            CompanyCnpj: DemoCompanies.EnergyCnpj,
            Quote: new Quote(
                id: "quote-16",
                opportunityId: "opportunity-16",
                code: "EAQ_2026_45497_V_1",
                name: "Ensaios elétricos em cabos de média tensão",
                status: "Cancelado",
                stage: "Fechado perdido",
                description: "Ensaios elétricos em cabos isolados, encerrado sem contratação.",
                companyName: DemoCompanies.EnergyName,
                externalContactName: "Marcos Vieira",
                externalContactEmail: "marcos.vieira@exemplo.com",
                totalPrice: 27600.00m,
                createdDate: new DateTimeOffset(2026, 9, 18, 17, 20, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-17",
            CompanyCnpj: DemoCompanies.DistributorCnpj,
            Quote: new Quote(
                id: "quote-17",
                opportunityId: "opportunity-17",
                code: "EAQ_2026_45498_V_1",
                name: "Caracterização de polímeros por espectroscopia",
                status: "Recusado",
                stage: "Fechado recusado Lactec",
                description: "Caracterização de material polimérico fora do escopo técnico atendido.",
                companyName: DemoCompanies.DistributorName,
                externalContactName: "Renata Souza",
                externalContactEmail: "renata.souza@exemplo.com",
                totalPrice: 13900.00m,
                createdDate: new DateTimeOffset(2026, 10, 1, 12, 30, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-18",
            CompanyCnpj: DemoCompanies.EnergyCnpj,
            Quote: new Quote(
                id: "quote-18",
                opportunityId: "opportunity-18",
                code: "EAQ_2026_45499_V_1",
                name: "Cromatografia de gases dissolvidos em banco de transformadores",
                status: "Finalizado",
                stage: "Relatório publicado",
                description: "Cromatografia de gases dissolvidos em quatro transformadores, com laudo publicado no portal.",
                companyName: DemoCompanies.EnergyName,
                externalContactName: "Paulo Moreira",
                externalContactEmail: "paulo.moreira@exemplo.com",
                totalPrice: 19840.00m,
                createdDate: new DateTimeOffset(2026, 8, 21, 10, 15, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-19",
            CompanyCnpj: DemoCompanies.DistributorCnpj,
            Quote: new Quote(
                id: "quote-19",
                opportunityId: "opportunity-19",
                code: "EAQ_2026_45500_V_1",
                name: "Ensaio de rigidez dielétrica em óleo mineral isolante",
                status: "Finalizado",
                stage: "Relatório publicado",
                description: "Medição de rigidez dielétrica em amostras de óleo mineral isolante, com laudo publicado no portal.",
                companyName: DemoCompanies.DistributorName,
                externalContactName: "Luciana Prado",
                externalContactEmail: "luciana.prado@exemplo.com",
                totalPrice: 7360.00m,
                createdDate: new DateTimeOffset(2026, 8, 5, 14, 40, 0, TimeSpan.Zero))
        )
    ];

    public Task<IReadOnlyList<Quote>> GetQuotesAsync(CancellationToken cancellationToken)
    {
        return Task.FromResult<IReadOnlyList<Quote>>(Quotes.Where(IsVisible).Select(q => q.Quote).ToList());
    }

    public Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken)
    {
        Quote? quote = Quotes.FirstOrDefault(q => q.Id == id && IsVisible(q))?.Quote;
        return Task.FromResult(quote);
    }

    private bool IsVisible(FakeQuote quote) => companyScope is null
        || companyScope.CompanyCnpjs.Contains(quote.CompanyCnpj, StringComparer.Ordinal);

    private sealed record FakeQuote(string Id, string CompanyCnpj, Quote Quote);
}
