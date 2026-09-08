using Portal.Application.LabResults;
using Portal.Domain.LabResults;

namespace Portal.Adapters.LabResults;

public sealed class FakeSqlServerLabResultRepository : ILabResultRepository
{
    private static readonly List<LabResult> LabResults =
    [
        new LabResult(
            id: "result-1",
            sampleId: "A-1407/2022",
            testName: "Compactação",
            status: "Finalizado",
            completedAt: new DateTime(2026, 10, 15),
            results: new Dictionary<string, string>
            {
                ["Densidade máxima"] = "1,934 g/cm³",
                ["Umidade ótima"] = "9,77%"
            }
        ),
        new LabResult(
            id: "result-2",
            sampleId: "A-1408/2022",
            testName: "Índice de Suporte Califórnia",
            status: "Concluído",
            completedAt: new DateTime(2026, 10, 16),
            results: new Dictionary<string, string>
            {
                ["ISC"] = "18%",
                ["Expansão"] = "0,4%"
            }
        )
    ];

    public Task<IReadOnlyList<LabResult>> GetLabResultsAsync(CancellationToken cancellationToken)
    {
        return Task.FromResult<IReadOnlyList<LabResult>>(LabResults);
    }

    public Task<LabResult?> GetLabResultByIdAsync(string id, CancellationToken cancellationToken)
    {
        LabResult? result = LabResults.FirstOrDefault(r => r.Id == id);
        return Task.FromResult(result);
    }
}