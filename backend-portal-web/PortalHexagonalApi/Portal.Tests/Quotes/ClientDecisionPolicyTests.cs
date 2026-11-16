using Portal.Domain.Pipeline;
using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.Quotes;

public sealed class ClientDecisionPolicyTests
{
    [Theory]
    [InlineData("Negociação", ClientDecisionKind.Acceptance, true)]
    [InlineData("Negociação", ClientDecisionKind.PurchaseOrder, true)]
    [InlineData("Negociação", ClientDecisionKind.Rejection, true)]
    [InlineData("Negociação", ClientDecisionKind.SampleShipment, false)]
    [InlineData("Elaborando proposta", ClientDecisionKind.Acceptance, false)]
    [InlineData("Qualificação", ClientDecisionKind.Rejection, false)]
    [InlineData("Aprovado pelo cliente", ClientDecisionKind.Acceptance, false)]
    [InlineData("Aprovado pelo cliente", ClientDecisionKind.SampleShipment, true)]
    [InlineData("Aguardando entrega da amostra", ClientDecisionKind.SampleShipment, false)]
    [InlineData("Cancelado", ClientDecisionKind.Acceptance, false)]
    public void ACadaEtapaComercialSoCabeADecisaoDaTela(string stage, ClientDecisionKind kind, bool allowed)
    {
        Assert.Equal(allowed, ClientDecisionPolicy.Allows(kind, CommercialStage.AsTheSourceWroteIt(stage), null, []));
    }

    [Fact]
    public void ODepoisDoAceiteGravadoLiberaOEnvioEFechaUmSegundoAceite()
    {
        ClientDecision[] recorded = [Decision(ClientDecisionKind.Acceptance)];
        CommercialStage negotiation = CommercialStage.AsTheSourceWroteIt("Negociação");

        Assert.True(ClientDecisionPolicy.Allows(ClientDecisionKind.SampleShipment, negotiation, null, recorded));
        Assert.False(ClientDecisionPolicy.Allows(ClientDecisionKind.Acceptance, negotiation, null, recorded));
        Assert.False(ClientDecisionPolicy.Allows(ClientDecisionKind.Rejection, negotiation, null, recorded));
    }

    [Fact]
    public void ARecusaGravadaEncerraQualquerOutraDecisao()
    {
        ClientDecision[] recorded = [Decision(ClientDecisionKind.Rejection)];
        CommercialStage negotiation = CommercialStage.AsTheSourceWroteIt("Negociação");

        Assert.All(Enum.GetValues<ClientDecisionKind>(), kind =>
            Assert.False(ClientDecisionPolicy.Allows(kind, negotiation, null, recorded)));
    }

    [Fact]
    public void OLaboratorioAdiantadoNaoDeixaConfirmarOEnvioDeNovo()
    {
        CommercialStage approved = CommercialStage.AsTheSourceWroteIt("Aprovado pelo cliente");

        Assert.False(ClientDecisionPolicy.Allows(
            ClientDecisionKind.SampleShipment, approved, PipelineStage.InProgress, []));
    }

    [Fact]
    public void ADecisaoMaisRecenteEAQueAparece()
    {
        ClientDecision older = Decision(ClientDecisionKind.Acceptance, minutes: 0);
        ClientDecision newer = Decision(ClientDecisionKind.SampleShipment, minutes: 5);

        Assert.Same(newer, ClientDecisionPolicy.Latest([older, newer]));
        Assert.Null(ClientDecisionPolicy.Latest([]));
    }

    private static ClientDecision Decision(ClientDecisionKind kind, int minutes = 0) => new(
        Guid.NewGuid(), "quote-3", "EAQ_2026_45484_V_1", kind,
        new ClientDecisionAuthor("42", "Cliente", "cliente@empresa.com"),
        new DateTimeOffset(2026, 11, 15, 10, minutes, 0, TimeSpan.Zero));
}
