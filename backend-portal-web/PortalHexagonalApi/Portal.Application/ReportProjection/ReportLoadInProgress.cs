using System.Collections.Immutable;

using Portal.Application.Visibility;

namespace Portal.Application.ReportProjection;

public sealed record EquipmentRefreshRequest(CompanyScope Scope, string EquipmentKey, bool OnlyIfMissing = false);

public sealed class ReportLoadInProgress
{
    private readonly SemaphoreSlim _slot = new(1, 1);
    private readonly SemaphoreSlim _pending = new(0, 1);
    private readonly object _lock = new();
    private readonly HashSet<string> _interested = new(StringComparer.Ordinal);
    private readonly HashSet<EquipmentRefreshRequest> _requests = [];
    private readonly HashSet<EquipmentRefreshRequest> _servingRequests = [];
    private readonly HashSet<string> _companies = new(StringComparer.Ordinal);
    private readonly HashSet<string> _servingCompanies = new(StringComparer.Ordinal);

    public bool IsRunning => _slot.CurrentCount == 0;

    public bool TryStart() => _slot.Wait(0);

    public void Finish() => _slot.Release();

    public bool RegisterInterest(string userKey, EquipmentRefreshRequest? request = null)
    {
        if (string.IsNullOrWhiteSpace(userKey)) return false;

        lock (_lock)
        {
            if (request is not null && !_servingRequests.Contains(request)) _requests.Add(request);

            return _interested.Add(userKey);
        }
    }

    public int Enqueue(
        string userKey,
        IReadOnlyCollection<EquipmentRefreshRequest> requests,
        IReadOnlyCollection<string> companies)
    {
        if (string.IsNullOrWhiteSpace(userKey)) return 0;

        lock (_lock)
        {
            int added = 0;

            foreach (EquipmentRefreshRequest request in requests)
            {
                if (!_servingRequests.Contains(request) && _requests.Add(request)) added++;
            }

            foreach (string cnpj in companies)
            {
                if (!_servingCompanies.Contains(cnpj) && _companies.Add(cnpj)) added++;
            }

            _interested.Add(userKey);

            if (_pending.CurrentCount == 0) _pending.Release();

            return added;
        }
    }

    public Task WaitForRequestsAsync(CancellationToken cancellationToken) => _pending.WaitAsync(cancellationToken);

    public IReadOnlyList<EquipmentRefreshRequest> TakeRequests()
    {
        lock (_lock)
        {
            _servingRequests.Clear();

            if (_requests.Count == 0) return [];

            List<EquipmentRefreshRequest> taken = [.. _requests];
            _servingRequests.UnionWith(taken);
            _requests.Clear();

            return taken;
        }
    }

    public IReadOnlyList<string> TakeCompanies()
    {
        lock (_lock)
        {
            _servingCompanies.Clear();

            if (_companies.Count == 0) return [];

            List<string> taken = [.. _companies.Order(StringComparer.Ordinal)];
            _servingCompanies.UnionWith(taken);
            _companies.Clear();

            return taken;
        }
    }

    public IReadOnlySet<string> HarvestInterested()
    {
        lock (_lock)
        {
            _requests.Clear();
            _servingRequests.Clear();
            _companies.Clear();
            _servingCompanies.Clear();

            if (_interested.Count == 0) return ImmutableHashSet<string>.Empty;

            HashSet<string> harvested = new(_interested, StringComparer.Ordinal);
            _interested.Clear();

            return harvested;
        }
    }
}
