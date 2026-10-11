using Portal.Domain.LabResults;

namespace Portal.Api.LabResults;

/// <summary>
/// Uma linha da listagem de equipamentos, campo por campo igual a
/// <c>frontend/src/app/model/equipment.ts</c>.
///
/// <see cref="Type"/> e <see cref="Location"/> viajam nulas sempre nesta fatia. A tela
/// mostra as duas colunas desde a historia HU-05, e o AutoLAB nao tem nenhuma das duas: o
/// campo existe para a tela poder anunciar a ausencia em vez de imprimir um valor que o
/// laboratorio nunca informou.
/// </summary>
public sealed record EquipmentResponse
{
    public required string Id { get; init; }

    public string? SerialNumber { get; init; }

    public required string Tag { get; init; }

    public string? Type { get; init; }

    public string? Location { get; init; }

    public required string Company { get; init; }

    public required string Identification { get; init; }

    public required int ReportCount { get; init; }

    public required string MostRecentReportDate { get; init; }
}

/// <summary>
/// O detalhe acrescenta o que a tela de dentro mostra nos cartoes. Tipo do oleo, tensao e
/// potencia seguem a mesma regra de <see cref="EquipmentResponse.Type"/>: sao placa de
/// equipamento, e placa de equipamento nao esta no AutoLAB.
/// </summary>
public sealed record EquipmentDetailResponse
{
    public required string Id { get; init; }

    public string? SerialNumber { get; init; }

    public required string Tag { get; init; }

    public string? Type { get; init; }

    public string? Location { get; init; }

    public required string Company { get; init; }

    public required string Identification { get; init; }

    public required int ReportCount { get; init; }

    public required string MostRecentReportDate { get; init; }

    public string? OilType { get; init; }

    public string? Voltage { get; init; }

    public string? Power { get; init; }
}

/// <summary>
/// Uma linha do historico de laudos.
///
/// <see cref="ReportUrl"/> e nula enquanto nao houver arquivo: o AutoLAB desta fatia
/// serve o resultado dos ensaios, nao o PDF assinado. Nulo faz a tela esconder o botao, e
/// um endereco inventado faria a tela oferecer um download que so sabe falhar.
/// </summary>
public sealed record EquipmentReportResponse
{
    public required string Id { get; init; }

    public required string Date { get; init; }

    public required ReportAnalysisType AnalysisType { get; init; }

    public required ReportStatus Status { get; init; }

    public string? ReportUrl { get; init; }
}
