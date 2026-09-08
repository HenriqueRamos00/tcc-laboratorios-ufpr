namespace Portal.Domain.LabResults;

public sealed class LabResult
{
    public string Id { get; }
    public string SampleId { get; }
    public string TestName { get; }
    public string Status { get; }
    public DateTime CompletedAt { get; }
    public IReadOnlyDictionary<string, string> Results { get; }

    public LabResult(
        string id,
        string sampleId,
        string testName,
        string status,
        DateTime completedAt,
        IReadOnlyDictionary<string, string> results)
    {
        Id = id;
        SampleId = sampleId;
        TestName = testName;
        Status = status;
        CompletedAt = completedAt;
        Results = results;
    }
}