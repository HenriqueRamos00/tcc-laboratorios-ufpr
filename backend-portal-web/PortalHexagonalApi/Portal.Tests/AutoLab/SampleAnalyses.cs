using Portal.Adapters.AutoLab;
using Portal.Adapters.LabResults;
using Portal.Adapters.Nameplate;
using Portal.Domain.LabResults;

namespace Portal.Tests.AutoLab;

internal static class SampleAnalyses
{
    private static readonly Lazy<IReadOnlyList<SampleAnalysis>> All = new(() =>
    {
        using AutoLabTestDatabase database = AutoLabTestDatabase.WithSample();
        using AutoLabDbContext context = database.NewContext();

        return AutoLabLabTestRepository.BuildAnalyses([.. context.DadosEnsaios]);
    });

    private static readonly Lazy<IReadOnlyList<EquipmentRow>> Registry = new(() =>
    {
        using AutoLabTestDatabase database = AutoLabTestDatabase.WithSample();
        using AutoLabDbContext context = database.NewContext();

        return [.. context.Equipamentos.AsEnumerable().Select(AutoLabEquipmentRegistryRepository.ToRow)];
    });

    public static IReadOnlyList<SampleAnalysis> Whole => All.Value;

    public static IReadOnlyList<EquipmentRow> RegistryRows => Registry.Value;

    public static SampleAnalysis OfProtocol(string protocoloLactec)
    {
        return Whole.Single(analysis => analysis.ProtocoloLactec == protocoloLactec);
    }
}
