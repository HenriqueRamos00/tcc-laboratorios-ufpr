using System.Globalization;
using System.Net;
using System.Text.Json;
using System.Text.Json.Serialization;

using Portal.Application.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Paging;
using Portal.Application.Quotes.Exceptions;

namespace Portal.Adapters.BackendLab;

/// <summary>Adapta os endpoints de cliente do Spring para as portas da API C#.</summary>
public sealed class BackendLabClientLabResultsRepository(HttpClient httpClient) : IClientLabResultsRepository
{
    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNameCaseInsensitive = false,
        NumberHandling = JsonNumberHandling.Strict
    };

    public async Task<Page<ClientEquipment>> ListEquipmentAsync(
        PageRequest page,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(page);
        using CancellationTokenSource operation = CreateOperationTimeout(cancellationToken);
        using HttpResponseMessage response = await SendAsync(
            new HttpRequestMessage(HttpMethod.Get, PagePath("api/client/equipments", page)), operation, cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);
        SpringPage<SpringEquipment?> value = await ReadResponseAsync<SpringPage<SpringEquipment?>>(response, operation, cancellationToken);
        if (value.Items is null) throw InvalidResponse("A lista de equipamentos do Spring não pode ser nula.");

        return new Page<ClientEquipment>(
            value.Items.Select(item => (item ?? throw InvalidResponse("A lista de equipamentos contém item nulo.")).ToClientEquipment()).ToArray(),
            Positive(value.Page, "page"),
            PageSize(value.PageSize),
            NonNegative(value.TotalItems, "totalItems"));
    }

    public async Task<ClientEquipment?> GetEquipmentAsync(string equipmentId, CancellationToken cancellationToken)
    {
        using CancellationTokenSource operation = CreateOperationTimeout(cancellationToken);
        using HttpResponseMessage response = await SendAsync(
            new HttpRequestMessage(HttpMethod.Get, $"api/client/equipments/{Uri.EscapeDataString(RequiredId(equipmentId))}"),
            operation, cancellationToken);
        if (response.StatusCode == HttpStatusCode.NotFound) return null;
        await EnsureSuccessAsync(response, cancellationToken);
        SpringEquipment? value = await ReadResponseAsync<SpringEquipment>(response, operation, cancellationToken);
        return value?.ToClientEquipment() ?? throw InvalidResponse("O equipamento do Spring não pode ser nulo.");
    }

    public async Task<Page<ClientAnalysis>?> ListAnalysesOfEquipmentAsync(
        string equipmentId,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(page);
        string path = $"api/client/equipments/{Uri.EscapeDataString(RequiredId(equipmentId))}/analyses";
        using CancellationTokenSource operation = CreateOperationTimeout(cancellationToken);
        using HttpResponseMessage response = await SendAsync(
            new HttpRequestMessage(HttpMethod.Get, PagePath(path, page)), operation, cancellationToken);
        if (response.StatusCode == HttpStatusCode.NotFound) return null;
        await EnsureSuccessAsync(response, cancellationToken);
        SpringPage<SpringAnalysis?> value = await ReadResponseAsync<SpringPage<SpringAnalysis?>>(response, operation, cancellationToken);
        if (value.Items is null) throw InvalidResponse("O histórico do equipamento do Spring não pode ser nulo.");

        return new Page<ClientAnalysis>(
            value.Items.Select(item => (item ?? throw InvalidResponse("O histórico do Spring contém item nulo.")).ToClientAnalysis()).ToArray(),
            Positive(value.Page, "page"),
            PageSize(value.PageSize),
            NonNegative(value.TotalItems, "totalItems"));
    }

    public async Task<ClientAnalysis?> GetAnalysisAsync(string reportId, CancellationToken cancellationToken)
    {
        using CancellationTokenSource operation = CreateOperationTimeout(cancellationToken);
        using HttpResponseMessage response = await SendAsync(
            new HttpRequestMessage(HttpMethod.Get, $"api/client/analyses/{Uri.EscapeDataString(RequiredId(reportId))}"),
            operation, cancellationToken);
        if (response.StatusCode == HttpStatusCode.NotFound) return null;
        await EnsureSuccessAsync(response, cancellationToken);
        SpringAnalysis? value = await ReadResponseAsync<SpringAnalysis>(response, operation, cancellationToken);
        return value?.ToClientAnalysis() ?? throw InvalidResponse("A análise do Spring não pode ser nula.");
    }

    public async Task<ClientReportDetail?> GetReportDetailAsync(string reportId, CancellationToken cancellationToken)
    {
        using CancellationTokenSource operation = CreateOperationTimeout(cancellationToken);
        using HttpResponseMessage response = await SendAsync(
            new HttpRequestMessage(HttpMethod.Get, $"api/client/reports/{Uri.EscapeDataString(RequiredId(reportId))}"),
            operation, cancellationToken);
        if (response.StatusCode == HttpStatusCode.NotFound) return null;
        await EnsureSuccessAsync(response, cancellationToken);
        SpringReportDetail? value = await ReadResponseAsync<SpringReportDetail>(response, operation, cancellationToken);
        return value is null
            ? throw InvalidResponse("O detalhe do relatório do Spring não pode ser nulo.")
            : value.ToClientReportDetail();
    }

    private async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request,
        CancellationTokenSource operation,
        CancellationToken cancellationToken)
    {
        using (request)
        {
            try
            {
                return await httpClient.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, operation.Token);
            }
            catch (QuoteAuthenticationRequiredException)
            {
                throw new ClientLabAuthenticationRequiredException();
            }
            catch (OperationCanceledException failure) when (IsOwnTimeout(operation, cancellationToken))
            {
                throw new ClientLabSourceUnavailableException("A consulta ao Backend Lab excedeu o tempo limite.", failure);
            }
            catch (Exception failure) when (failure is HttpRequestException or IOException or OperationCanceledException)
            {
                if (failure is OperationCanceledException && cancellationToken.IsCancellationRequested) throw;
                throw new ClientLabSourceUnavailableException("A consulta ao Backend Lab falhou.", failure);
            }
        }
    }

    private static async Task EnsureSuccessAsync(HttpResponseMessage response, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (response.StatusCode == HttpStatusCode.Unauthorized) throw new ClientLabAuthenticationRequiredException();
        if (response.StatusCode == HttpStatusCode.Forbidden) throw new ClientLabAccessForbiddenException();
        if (response.StatusCode == HttpStatusCode.BadRequest) throw new ClientLabRequestRejectedException();
        if (response.IsSuccessStatusCode) return;
        throw new ClientLabSourceUnavailableException($"O Backend Lab respondeu HTTP {(int)response.StatusCode}.");
    }

    private static async Task<T> ReadResponseAsync<T>(
        HttpResponseMessage response,
        CancellationTokenSource operation,
        CancellationToken cancellationToken)
    {
        try
        {
            await using Stream content = await response.Content.ReadAsStreamAsync(operation.Token);
            return await JsonSerializer.DeserializeAsync<T>(content, SerializerOptions, operation.Token)
                ?? throw InvalidResponse("A resposta do Backend Lab é nula.");
        }
        catch (OperationCanceledException failure) when (IsOwnTimeout(operation, cancellationToken))
        {
            throw new ClientLabSourceUnavailableException("A leitura da resposta do Backend Lab excedeu o tempo limite.", failure);
        }
        catch (Exception failure) when (failure is JsonException or IOException or HttpRequestException)
        {
            throw new ClientLabSourceUnavailableException("A resposta do Backend Lab é inválida.", failure);
        }
    }

    private CancellationTokenSource CreateOperationTimeout(CancellationToken cancellationToken)
    {
        CancellationTokenSource operation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        if (httpClient.Timeout != Timeout.InfiniteTimeSpan) operation.CancelAfter(httpClient.Timeout);
        return operation;
    }

    private static bool IsOwnTimeout(CancellationTokenSource operation, CancellationToken consumerToken) =>
        operation.IsCancellationRequested && !consumerToken.IsCancellationRequested;

    private static string PagePath(string path, PageRequest page) =>
        $"{path}?page={page.Page.ToString(CultureInfo.InvariantCulture)}&pageSize={page.PageSize.ToString(CultureInfo.InvariantCulture)}";

    private static string RequiredId(string? value) => string.IsNullOrWhiteSpace(value)
        ? throw InvalidResponse("A resposta ou consulta não contém um identificador.")
        : value.Trim();

    private static int Positive(int value, string name) => value > 0 ? value : throw InvalidResponse($"Campo {name} inválido.");
    private static int PageSize(int value) => value is > 0 and <= 100 ? value : throw InvalidResponse("Campo pageSize inválido.");
    private static int NonNegative(int value, string name) => value >= 0 ? value : throw InvalidResponse($"Campo {name} inválido.");
    private static ClientLabSourceUnavailableException InvalidResponse(string message) => new(message);

    private sealed class SpringPage<T>
    {
        [JsonRequired, JsonPropertyName("items")] public List<T>? Items { get; init; }
        [JsonRequired, JsonPropertyName("page")] public int Page { get; init; }
        [JsonRequired, JsonPropertyName("pageSize")] public int PageSize { get; init; }
        [JsonRequired, JsonPropertyName("totalItems")] public int TotalItems { get; init; }
    }

    private sealed class SpringEquipment
    {
        [JsonRequired, JsonPropertyName("id")] public string? Id { get; init; }
        [JsonRequired, JsonPropertyName("company")] public SpringCompany? Company { get; init; }
        [JsonPropertyName("serialNumber")] public string? SerialNumber { get; init; }
        [JsonPropertyName("tag")] public string? Tag { get; init; }
        [JsonPropertyName("type")] public string? Type { get; init; }
        [JsonPropertyName("substation")] public string? Substation { get; init; }
        [JsonPropertyName("primaryVoltageKv")] public decimal? PrimaryVoltageKv { get; init; }
        [JsonPropertyName("manufacturer")] public string? Manufacturer { get; init; }
        [JsonPropertyName("manufactureYear")] public int? ManufactureYear { get; init; }
        [JsonPropertyName("powerMva")] public decimal? PowerMva { get; init; }
        [JsonPropertyName("oilType")] public string? OilType { get; init; }
        [JsonPropertyName("oilVolumeLiters")] public decimal? OilVolumeLiters { get; init; }
        [JsonRequired, JsonPropertyName("publishedAnalysisCount")] public int PublishedAnalysisCount { get; init; }
        [JsonPropertyName("latestPublishedAnalysis")] public SpringAnalysisSummary? LatestPublishedAnalysis { get; init; }

        public ClientEquipment ToClientEquipment() => new ClientEquipment(
            RequiredId(Id), SerialNumber, Tag, Type, Substation, PrimaryVoltageKv, Manufacturer,
            ManufactureYear, PowerMva, OilType, OilVolumeLiters,
            RequiredId(Company?.Id), RequiredText(Company?.Name, "company.name"),
            NonNegative(PublishedAnalysisCount, "publishedAnalysisCount"),
            LatestPublishedAnalysis?.ToClientAnalysisSummary()) with { Type = SpringClientValueMapper.DisplayEquipmentType(Type) };
    }

    private sealed class SpringCompany
    {
        [JsonRequired, JsonPropertyName("id")] public string? Id { get; init; }
        [JsonRequired, JsonPropertyName("name")] public string? Name { get; init; }
        public ClientCompany ToClientCompany() => new(RequiredId(Id), RequiredText(Name, "company.name"));
    }

    private sealed class SpringQuote
    {
        [JsonRequired, JsonPropertyName("id")] public string? Id { get; init; }
        [JsonPropertyName("protocol")] public string? Protocol { get; init; }
        public ClientQuote ToClientQuote() => new(RequiredId(Id), Protocol);
    }

    private sealed class SpringSample
    {
        [JsonRequired, JsonPropertyName("id")] public string? Id { get; init; }
        [JsonPropertyName("code")] public string? Code { get; init; }
        [JsonPropertyName("name")] public string? Name { get; init; }
        public ClientSample ToClientSample() => new(RequiredId(Id), Code, Name);
    }

    private sealed class SpringEquipmentReference
    {
        [JsonRequired, JsonPropertyName("id")] public string? Id { get; init; }
        [JsonPropertyName("serialNumber")] public string? SerialNumber { get; init; }
        [JsonPropertyName("tag")] public string? Tag { get; init; }
        [JsonPropertyName("type")] public string? Type { get; init; }
        public ClientAnalysisEquipment ToClientEquipment() => new(RequiredId(Id), SerialNumber, Tag, Type);
    }

    // O resumo das listagens não transporta os objetos aninhados do detalhe. Mantê-lo como
    // DTO próprio evita rejeitar uma resposta válida por campos que não fazem parte do JSON.
    private sealed class SpringAnalysisSummary
    {
        [JsonRequired, JsonPropertyName("id")] public string? Id { get; init; }
        [JsonRequired, JsonPropertyName("reportId")] public string? ReportId { get; init; }
        [JsonRequired, JsonPropertyName("testId")] public string? TestId { get; init; }
        [JsonPropertyName("testCode")] public string? TestCode { get; init; }
        [JsonRequired, JsonPropertyName("reportCode")] public string? ReportCode { get; init; }
        [JsonPropertyName("testType")] public string? TestType { get; init; }
        [JsonPropertyName("analysisType")] public string? AnalysisType { get; init; }
        [JsonPropertyName("scheduledAt")] public DateOnly? ScheduledAt { get; init; }
        [JsonPropertyName("validatedAt")] public DateTimeOffset? ValidatedAt { get; init; }
        [JsonPropertyName("publishedAt")] public DateTimeOffset? PublishedAt { get; init; }
        [JsonPropertyName("technicalClassification")] public string? TechnicalClassification { get; init; }
        [JsonPropertyName("conclusion")] public string? Conclusion { get; init; }
        [JsonRequired, JsonPropertyName("pdfAvailable")] public bool PdfAvailable { get; init; }

        public ClientAnalysisSummary ToClientAnalysisSummary() => new(
            RequiredId(Id),
            RequiredId(ReportId),
            RequiredId(TestId),
            TestCode,
            RequiredText(ReportCode, "reportCode"),
            TestType,
            AnalysisType,
            ScheduledAt,
            ValidatedAt,
            PublishedAt,
            TechnicalClassification,
            Conclusion,
            PdfAvailable);
    }

    private class SpringAnalysis
    {
        [JsonPropertyName("id")] public string? Id { get; init; }
        [JsonPropertyName("reportId")] public string? ReportId { get; init; }
        [JsonRequired, JsonPropertyName("testId")] public string? TestId { get; init; }
        [JsonRequired, JsonPropertyName("testCode")] public string? TestCode { get; init; }
        [JsonRequired, JsonPropertyName("reportCode")] public string? ReportCode { get; init; }
        [JsonPropertyName("company")] public SpringCompany? Company { get; init; }
        [JsonPropertyName("quote")] public SpringQuote? Quote { get; init; }
        [JsonPropertyName("sample")] public SpringSample? Sample { get; init; }
        [JsonPropertyName("equipment")] public SpringEquipmentReference? Equipment { get; init; }
        [JsonRequired, JsonPropertyName("testType")] public string? TestType { get; init; }
        [JsonPropertyName("analysisType")] public string? AnalysisType { get; init; }
        [JsonPropertyName("scheduledAt")] public DateOnly? ScheduledAt { get; init; }
        [JsonPropertyName("validatedAt")] public DateTimeOffset? ValidatedAt { get; init; }
        [JsonRequired, JsonPropertyName("publishedAt")] public DateTimeOffset? PublishedAt { get; init; }
        [JsonPropertyName("technicalClassification")] public string? TechnicalClassification { get; init; }
        [JsonPropertyName("conclusion")] public string? Conclusion { get; init; }
        [JsonRequired, JsonPropertyName("pdfAvailable")] public bool PdfAvailable { get; init; }
        [JsonPropertyName("measurements")] public List<SpringMeasurement?>? Measurements { get; init; }

        public ClientAnalysis ToClientAnalysis(bool measurementsRequired = true)
        {
            if (measurementsRequired && Measurements is null)
                throw InvalidResponse("Campo measurements ausente.");

            return new ClientAnalysis(
                RequiredId(ReportId ?? Id),
                RequiredId(ReportId ?? Id),
                RequiredId(TestId),
                TestCode,
                RequiredText(ReportCode, "reportCode"),
                (Company ?? throw InvalidResponse("Campo company ausente.")).ToClientCompany(),
                (Quote ?? throw InvalidResponse("Campo quote ausente.")).ToClientQuote(),
                (Sample ?? throw InvalidResponse("Campo sample ausente.")).ToClientSample(),
                Equipment?.ToClientEquipment(),
                TestType,
                AnalysisType,
                ScheduledAt,
                ValidatedAt,
                PublishedAt ?? throw InvalidResponse("Campo publishedAt ausente ou inválido."),
                TechnicalClassification,
                Conclusion,
                PdfAvailable,
                (Measurements ?? []).Select(item => (item ?? throw InvalidResponse("A lista de medições contém item nulo.")).ToClientMeasurement()).ToArray());
        }

    }

    private sealed class SpringReportDetail : SpringAnalysis
    {
        [JsonPropertyName("analysisId")] public string? AnalysisId { get; init; }
        [JsonRequired, JsonPropertyName("fileUrl")] public string? FileUrl { get; init; }

        public ClientReportDetail ToClientReportDetail()
        {
            ClientAnalysis analysis = ToClientAnalysis(measurementsRequired: false) with
            {
                Id = RequiredId(AnalysisId ?? ReportId ?? Id)
            };
            return new ClientReportDetail(analysis, RequiredText(FileUrl, "fileUrl"));
        }
    }

    private sealed class SpringMeasurement
    {
        [JsonRequired, JsonPropertyName("code")] public string? Code { get; init; }
        [JsonRequired, JsonPropertyName("name")] public string? Name { get; init; }
        [JsonPropertyName("unit")] public string? Unit { get; init; }
        [JsonPropertyName("method")] public string? Method { get; init; }
        [JsonPropertyName("referenceLimit")] public string? ReferenceLimit { get; init; }
        [JsonRequired, JsonPropertyName("result")] public SpringResult? Result { get; init; }

        public ClientMeasurement ToClientMeasurement() => new(
            RequiredText(Code, "measurement.code"),
            RequiredText(Name, "measurement.name"),
            Unit, Method, ReferenceLimit,
            (Result ?? throw InvalidResponse("Campo measurement.result ausente.")).ToClientResult(),
            SpringClientValueMapper.IndicatorCode(RequiredText(Code, "measurement.code"), Unit));
    }

    private sealed class SpringResult
    {
        [JsonRequired, JsonPropertyName("kind")] public string? Kind { get; init; }
        [JsonPropertyName("value")] public string? Value { get; init; }
        [JsonPropertyName("text")] public string? Text { get; init; }

        public ClientMeasurementResult ToClientResult() => Kind switch
        {
            "quantity" => new ClientMeasurementResult.Quantity(ParseDecimal(Value)),
            "not-detected" => new ClientMeasurementResult.NotDetected(),
            "not-performed" => new ClientMeasurementResult.NotPerformed(),
            "text" => new ClientMeasurementResult.Text(Text ?? throw InvalidResponse("Campo result.text ausente.")),
            _ => throw InvalidResponse("Campo result.kind desconhecido.")
        };

        private static decimal ParseDecimal(string? value)
        {
            const NumberStyles DecimalWithoutGrouping = NumberStyles.AllowLeadingSign | NumberStyles.AllowDecimalPoint;
            return decimal.TryParse(value, DecimalWithoutGrouping, CultureInfo.InvariantCulture, out decimal number)
                ? number
                : throw InvalidResponse("Valor decimal de medição inválido.");
        }
    }

    private static string RequiredText(string? value, string name) => string.IsNullOrWhiteSpace(value)
        ? throw InvalidResponse($"Campo {name} ausente ou inválido.")
        : value.Trim();
}
