using Portal.Application.LabResults;
using Portal.Domain.LabResults;
using Portal.Domain.Nameplate;
using Portal.Tests.LabResults;

using Xunit;

namespace Portal.Tests.Nameplate;

/// <summary>
/// Entra pela porta condutora com um duble em cada porta conduzida: nenhum servidor, nenhum
/// HTTP e nenhum adaptador de producao.
///
/// E aqui que o isolamento desta fatia e provado, e nao no teste de contrato. O cadastro nao
/// tem coluna de empresa, entao nao existe filtro de inquilino para conferir no corpo HTTP: o
/// que protege o cliente e QUAIS series a aplicacao decide perguntar, e isso o fio nao mostra.
/// Um servico que perguntasse o cadastro pela chave vinda da rota, antes de conferir o
/// inquilino, serviria exatamente a mesma resposta para a requisicao legitima.
/// </summary>
public sealed class NameplateIsolationTests
{
    private const string Tenant = "EMPRESA DE ENERGIA";
    private const string OtherTenant = "OUTRA EMPRESA S.A.";

    /// <summary>
    /// O cadastro e consultado uma vez, com as series da listagem, e nao uma vez por linha.
    /// A resposta HTTP e identica nas duas escolhas, e um adaptador de banco com a escolha
    /// errada abre uma consulta por equipamento da tela.
    /// </summary>
    [Fact]
    public async Task TheListingAsksTheRegistryOnceForEverySerialNumberOnThePage()
    {
        InMemoryLabTestRepository labTests = new(
            AnalysisOf("01351/2026-004", "111.594/1"),
            AnalysisOf("01417/2026-001", "571289"));

        InMemoryEquipmentNameplateRepository registry = new(
            NameplateBuilder.Valid(serialNumber: "111.594/1"),
            NameplateBuilder.Valid(serialNumber: "571289"));

        IReadOnlyList<EquipmentWithNameplate> found = await new LabResultsConsultation(
            labTests, registry).ListEquipmentAsync(Tenant, CancellationToken.None);

        Assert.Equal(1, registry.TimesTheListWasAsked);
        Assert.Equal(0, registry.TimesOneSerialNumberWasAsked);
        Assert.Equal(["111.594/1", "571289"], registry.SerialNumbersAsked.Order());
        Assert.All(found, item => Assert.NotNull(item.Nameplate));
    }

    /// <summary>
    /// A pergunta ao cadastro so carrega series do inquilino que perguntou. E a mutacao que
    /// mais importa nesta fatia: pedir ao cadastro as series de todos os inquilinos devolve a
    /// mesma listagem, porque o casamento depois descarta o que nao e da pagina, e entrega ao
    /// adaptador de banco a lista de equipamentos de todos os clientes.
    /// </summary>
    [Fact]
    public async Task TheRegistryIsOnlyAskedAboutTheTenantsOwnSerialNumbers()
    {
        InMemoryLabTestRepository labTests = new(
            AnalysisOf("01417/2026-001", "571289"),
            AnalysisOf("01563/2026-018", "5233", company: OtherTenant));

        InMemoryEquipmentNameplateRepository registry = new(
            NameplateBuilder.Valid(serialNumber: "571289"),
            NameplateBuilder.Valid(serialNumber: "5233", tag: "TR-00004"));

        IReadOnlyList<EquipmentWithNameplate> found = await new LabResultsConsultation(
            labTests, registry).ListEquipmentAsync(Tenant, CancellationToken.None);

        Assert.Equal(["571289"], registry.SerialNumbersAsked);
        Assert.DoesNotContain("5233", registry.SerialNumbersAsked);
        Assert.DoesNotContain("TR-00004", found.Select(item => item.Nameplate?.Tag));
    }

    /// <summary>
    /// A chave que chega na rota nunca alcanca o cadastro antes de o inquilino ser conferido.
    /// A resposta e ausencia, como antes desta fatia, e o cadastro nao e perguntado nenhuma
    /// vez: perguntar primeiro e descartar depois ja teria entregado a placa ao processo, ao
    /// log e a conexao do banco.
    /// </summary>
    [Fact]
    public async Task AnotherTenantsEquipmentNeverReachesTheRegistry()
    {
        InMemoryLabTestRepository labTests = new(
            AnalysisOf("01563/2026-018", "5233", company: OtherTenant));

        InMemoryEquipmentNameplateRepository registry = new(
            NameplateBuilder.Valid(serialNumber: "5233", tag: "TR-00004"));

        EquipmentWithNameplate? found = await new LabResultsConsultation(labTests, registry)
            .GetEquipmentAsync(Tenant, "5233", CancellationToken.None);

        Assert.Null(found);
        Assert.Equal(0, registry.TimesOneSerialNumberWasAsked);
        Assert.Equal(0, registry.TimesTheListWasAsked);
        Assert.Empty(registry.SerialNumbersAsked);
    }

    /// <summary>
    /// O detalhe pergunta por uma serie so. Perguntar em lote com uma serie dentro devolve a
    /// mesma resposta e esconde a diferenca entre as duas operacoes da porta.
    /// </summary>
    [Fact]
    public async Task TheDetailAsksTheRegistryForOneSerialNumberAndNeverForAList()
    {
        InMemoryLabTestRepository labTests = new(AnalysisOf("01417/2026-001", "571289"));
        InMemoryEquipmentNameplateRepository registry = new(
            NameplateBuilder.Valid(serialNumber: "571289"));

        EquipmentWithNameplate? found = await new LabResultsConsultation(labTests, registry)
            .GetEquipmentAsync(Tenant, "571289", CancellationToken.None);

        Assert.NotNull(found);
        Assert.NotNull(found.Nameplate);
        Assert.Equal(1, registry.TimesOneSerialNumberWasAsked);
        Assert.Equal(0, registry.TimesTheListWasAsked);
    }

    /// <summary>
    /// A chave do equipamento cai no protocolo Lactec quando a serie vem em branco, e
    /// protocolo nao e numero de serie. Perguntar o cadastro por "01439/2026-004" so pode
    /// produzir casamento falso, e e a juncao medida que diz isso: as quatro series
    /// verdadeiras da amostra casam com o cadastro, e as duas que nao casam sao as duas que
    /// este projeto inventou para laudos que vieram sem serie.
    /// </summary>
    [Fact]
    public async Task EquipmentAddressedByProtocolIsNeverLookedUpInTheRegistry()
    {
        InMemoryLabTestRepository labTests = new(
            AnalysisBuilder.Valid(
                protocoloLactec: "01439/2026-004",
                serialNumber: null,
                company: Tenant));

        InMemoryEquipmentNameplateRepository registry = new(
            NameplateBuilder.Valid(serialNumber: "01439/2026-004"));

        EquipmentWithNameplate listed = Assert.Single(
            await new LabResultsConsultation(labTests, registry)
                .ListEquipmentAsync(Tenant, CancellationToken.None));

        Assert.Equal("01439/2026-004", listed.Equipment.EquipmentKey);
        Assert.Null(listed.Nameplate);
        Assert.Equal(0, registry.TimesTheListWasAsked);
        Assert.Equal(0, registry.TimesOneSerialNumberWasAsked);
    }

    /// <summary>
    /// O mesmo que o caso acima, pela rota do detalhe, e o caso precisa existir separado.
    ///
    /// A listagem e o detalhe escolhem a chave em lugares diferentes do servico, e a mutacao
    /// que troca o numero de serie pela chave do equipamento no detalhe sobrevivia a suite
    /// inteira: nenhum caso entrava por essa rota com um laudo sem serie. Teste que entra pela
    /// listagem nao alcanca a linha do detalhe, do mesmo jeito que o teste de contrato nao
    /// alcanca a escolha de qual pergunta e feita.
    /// </summary>
    [Fact]
    public async Task TheDetailOfEquipmentAddressedByProtocolNeverReachesTheRegistry()
    {
        InMemoryLabTestRepository labTests = new(
            AnalysisBuilder.Valid(
                protocoloLactec: "01439/2026-004",
                serialNumber: null,
                company: Tenant));

        InMemoryEquipmentNameplateRepository registry = new(
            NameplateBuilder.Valid(serialNumber: "01439/2026-004"));

        EquipmentWithNameplate? detail = await new LabResultsConsultation(labTests, registry)
            .GetEquipmentAsync(Tenant, "01439/2026-004", CancellationToken.None);

        Assert.NotNull(detail);
        Assert.Null(detail.Nameplate);
        Assert.Empty(registry.SerialNumbersAsked);
        Assert.Equal(0, registry.TimesOneSerialNumberWasAsked);
    }

    /// <summary>
    /// O laudo continua aparecendo quando o cadastro nao conhece a serie, e isso e o requisito:
    /// a placa e acessoria, o laudo nao. Uma implementacao que casasse por posicao, ou que
    /// descartasse o equipamento sem placa, esvaziaria a listagem nos 14% de series que o
    /// cadastro nao tem.
    /// </summary>
    [Fact]
    public async Task EquipmentTheRegistryDoesNotKnowIsStillListedWithoutANameplate()
    {
        InMemoryLabTestRepository labTests = new(
            AnalysisOf("01417/2026-001", "571289"),
            AnalysisOf("01482/2026-001", "884121"));

        InMemoryEquipmentNameplateRepository registry = new(
            NameplateBuilder.Valid(serialNumber: "571289"));

        IReadOnlyList<EquipmentWithNameplate> found = await new LabResultsConsultation(
            labTests, registry).ListEquipmentAsync(Tenant, CancellationToken.None);

        Assert.Equal(2, found.Count);
        Assert.NotNull(found.Single(item => item.Equipment.EquipmentKey == "571289").Nameplate);
        Assert.Null(found.Single(item => item.Equipment.EquipmentKey == "884121").Nameplate);
    }

    /// <summary>
    /// Cada equipamento recebe a placa da sua propria serie. Casar por posicao na lista
    /// devolveria duas placas trocadas, e a resposta continuaria com o mesmo numero de linhas
    /// e os mesmos campos preenchidos: so o conteudo estaria errado, e seria errado de um
    /// transformador para outro.
    /// </summary>
    [Fact]
    public async Task EachEquipmentGetsTheNameplateOfItsOwnSerialNumber()
    {
        InMemoryLabTestRepository labTests = new(
            AnalysisOf("01417/2026-001", "571289"),
            AnalysisOf("01351/2026-004", "111.594/1"));

        InMemoryEquipmentNameplateRepository registry = new(
            NameplateBuilder.Valid(serialNumber: "111.594/1", tag: "TR-00002"),
            NameplateBuilder.Valid(serialNumber: "571289", tag: "TR-00001"));

        IReadOnlyList<EquipmentWithNameplate> found = await new LabResultsConsultation(
            labTests, registry).ListEquipmentAsync(Tenant, CancellationToken.None);

        Assert.Equal(
            "TR-00001",
            found.Single(item => item.Equipment.EquipmentKey == "571289").Nameplate?.Tag);
        Assert.Equal(
            "TR-00002",
            found.Single(item => item.Equipment.EquipmentKey == "111.594/1").Nameplate?.Tag);
    }

    /// <summary>
    /// Inquilino sem equipamento nenhum nao chega a perguntar ao cadastro. Uma clausula IN
    /// vazia e escrita de SQL que alguns geradores transformam em consulta sem filtro, e uma
    /// consulta sem filtro aqui le o cadastro inteiro, de todos os clientes.
    /// </summary>
    [Fact]
    public async Task ATenantWithoutEquipmentDoesNotTouchTheRegistryAtAll()
    {
        InMemoryLabTestRepository labTests = new(
            AnalysisOf("01563/2026-018", "5233", company: OtherTenant));

        InMemoryEquipmentNameplateRepository registry = new(
            NameplateBuilder.Valid(serialNumber: "5233"));

        Assert.Empty(await new LabResultsConsultation(labTests, registry)
            .ListEquipmentAsync(Tenant, CancellationToken.None));

        Assert.Equal(0, registry.TimesTheListWasAsked);
    }

    private static SampleAnalysis AnalysisOf(
        string protocoloLactec,
        string serialNumber,
        string company = Tenant)
    {
        return AnalysisBuilder.Valid(
            protocoloLactec: protocoloLactec,
            serialNumber: serialNumber,
            company: company);
    }
}
