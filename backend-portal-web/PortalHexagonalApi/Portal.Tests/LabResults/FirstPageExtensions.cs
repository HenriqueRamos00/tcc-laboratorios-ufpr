using Portal.Tests.AutoLab;
using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;

namespace Portal.Tests.LabResults;

internal static class FirstPageExtensions
{
    public static async Task<IReadOnlyList<EquipmentWithNameplate>> FirstPageOfEquipmentAsync(
        this IForConsultingLabResults consultation,
        string company)
    {
        Page<EquipmentWithNameplate> page = await consultation.ListEquipmentAsync(
            TestCompanies.ScopeOf(company), null, PageRequest.First, CancellationToken.None);

        return page.Items;
    }

    public static async Task<IReadOnlyList<SampleAnalysis>> FirstPageOfCompanyAsync(
        this ILabTestRepository repository,
        string company)
    {
        Page<SampleAnalysis> page = await repository.GetAnalysesAsync(
            TestCompanies.ScopeOf(company), null, PageRequest.First, CancellationToken.None);

        return page.Items;
    }
}
