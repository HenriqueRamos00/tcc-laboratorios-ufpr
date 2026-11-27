using Portal.Application.ReportProjection;
using Portal.Tests.AutoLab;

using Xunit;

namespace Portal.Tests.ReportProjection;

public sealed class ReportLoadInProgressTests
{
    [Fact]
    public void ASecondAttemptDoesNotGetTheSlotAndFinishingGivesItBack()
    {
        ReportLoadInProgress inProgress = new();

        Assert.True(inProgress.TryStart());
        Assert.False(inProgress.TryStart());

        inProgress.Finish();
        Assert.True(inProgress.TryStart());
    }

    [Fact]
    public void TheSamePersonTwiceIsOneInterested()
    {
        ReportLoadInProgress inProgress = new();

        Assert.True(inProgress.RegisterInterest("42"));
        Assert.False(inProgress.RegisterInterest("42"));

        Assert.Equal(["42"], inProgress.HarvestInterested());
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    public void AKeyThatIdentifiesNobodyDoesNotEnter(string key)
    {
        ReportLoadInProgress inProgress = new();

        Assert.False(inProgress.RegisterInterest(key, new EquipmentRefreshRequest(Scope(), "571289")));
        Assert.Empty(inProgress.HarvestInterested());
        Assert.Empty(inProgress.TakeRequests());
    }

    [Fact]
    public void HarvestingEmptiesTheList()
    {
        ReportLoadInProgress inProgress = new();
        inProgress.RegisterInterest("42");
        inProgress.RegisterInterest("77");

        Assert.Equal(["42", "77"], inProgress.HarvestInterested().Order());
        Assert.Empty(inProgress.HarvestInterested());
    }

    [Fact]
    public void AKeyInAnotherCaseIsAnotherInterested()
    {
        ReportLoadInProgress inProgress = new();

        Assert.True(inProgress.RegisterInterest("a1"));
        Assert.True(inProgress.RegisterInterest("A1"));

        Assert.Equal(2, inProgress.HarvestInterested().Count);
    }

    [Fact]
    public void TheSameEquipmentAskedTwiceIsServedOnceAndWhoAskedStaysInterested()
    {
        ReportLoadInProgress inProgress = new();

        inProgress.RegisterInterest("42", new EquipmentRefreshRequest(Scope(), "571289"));
        inProgress.RegisterInterest("77", new EquipmentRefreshRequest(Scope(), "571289"));
        inProgress.RegisterInterest("77", new EquipmentRefreshRequest(Scope(), "111.594/1"));

        Assert.Equal(["111.594/1", "571289"], inProgress.TakeRequests().Select(request => request.EquipmentKey).Order());
        Assert.Empty(inProgress.TakeRequests());
        Assert.Equal(["42", "77"], inProgress.HarvestInterested().Order());
    }

    [Fact]
    public void ARequestThatArrivedAfterTheLastServingIsDroppedWithTheHarvest()
    {
        ReportLoadInProgress inProgress = new();

        inProgress.RegisterInterest("42", new EquipmentRefreshRequest(Scope(), "571289"));

        Assert.Equal(["42"], inProgress.HarvestInterested());
        Assert.Empty(inProgress.TakeRequests());
    }

    [Fact]
    public void WhatIsBeingServedIsNotQueuedAgainUntilTheNextServing()
    {
        ReportLoadInProgress inProgress = new();
        EquipmentRefreshRequest request = new(Scope(), "571289", OnlyIfMissing: true);

        Assert.Equal(2, inProgress.Enqueue("42", [request], [TestCompanies.EnergyCnpj]));
        Assert.Equal(0, inProgress.Enqueue("77", [request], [TestCompanies.EnergyCnpj]));

        Assert.Single(inProgress.TakeCompanies());
        Assert.Single(inProgress.TakeRequests());

        Assert.Equal(0, inProgress.Enqueue("78", [request], [TestCompanies.EnergyCnpj]));
        Assert.Empty(inProgress.TakeCompanies());
        Assert.Empty(inProgress.TakeRequests());

        Assert.Equal(2, inProgress.Enqueue("79", [request], [TestCompanies.EnergyCnpj]));
        Assert.Equal(["42", "77", "78", "79"], inProgress.HarvestInterested().Order());
        Assert.Empty(inProgress.TakeCompanies());
        Assert.Empty(inProgress.TakeRequests());
    }

    [Fact]
    public async Task QueuingWakesWhoServesOnceHoweverManyAsked()
    {
        ReportLoadInProgress inProgress = new();

        Assert.False(inProgress.WaitForRequestsAsync(new CancellationToken(canceled: true)).IsCompletedSuccessfully);

        inProgress.Enqueue("42", [new EquipmentRefreshRequest(Scope(), "571289")], []);
        inProgress.Enqueue("77", [new EquipmentRefreshRequest(Scope(), "TR-2")], []);
        await inProgress.WaitForRequestsAsync(CancellationToken.None);

        using CancellationTokenSource limit = new(TimeSpan.FromMilliseconds(50));

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => inProgress.WaitForRequestsAsync(limit.Token));
        Assert.Equal(0, inProgress.Enqueue("", [new EquipmentRefreshRequest(Scope(), "TR-3")], []));
        Assert.Equal(2, inProgress.TakeRequests().Count);
    }

    [Fact]
    public void TheSlotTellsWhetherARoundIsRunning()
    {
        ReportLoadInProgress inProgress = new();

        Assert.False(inProgress.IsRunning);
        Assert.True(inProgress.TryStart());
        Assert.True(inProgress.IsRunning);

        inProgress.Finish();
        Assert.False(inProgress.IsRunning);
    }

    private static Portal.Application.Visibility.CompanyScope Scope() => TestCompanies.ScopeOf(TestCompanies.Energy);
}
