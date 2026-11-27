using Portal.Application.ReportDocuments;
using Portal.Application.ReportProjection;
using Portal.Domain.LabResults;

namespace Portal.Api.LabResults;

/// <summary>
/// Uma linha da listagem de equipamentos, campo por campo igual a
/// <c>frontend/src/app/model/equipment.ts</c>.
///
/// <see cref="Type"/> deixou de ser sempre nula: ela vem de <c>TipoEquipamento</c> do
/// cadastro, como o cadastro escreve, sem vocabulario fechado e sem fundir os pares quase
/// iguais. Ela continua nula quando o cadastro nao conhece a serie, quando o cadastro se
/// contradiz sobre o tipo, e quando o equipamento e enderecado pelo protocolo Lactec, que nao
/// e numero de serie e portanto nao casa com cadastro nenhum.
///
/// <see cref="EquipmentTag"/> e campo novo e opcional, e <see cref="Tag"/> nao mudou de
/// significado. Quem decide o que imprimir na coluna "TAG" e a tela, que tem a TAG de placa
/// quando o cadastro a conhece e a identificacao do laudo quando nao. Repontar
/// <see cref="Tag"/> para a TAG do cadastro mudaria tambem o titulo e a migalha de pao do
/// detalhe e da tela de indicadores, que leem o mesmo campo, e as tres passariam a nomear o
/// equipamento de um jeito que nenhuma fatia pediu.
/// </summary>
public sealed record EquipmentResponse
{
    public required string Id { get; init; }

    public string? SerialNumber { get; init; }

    public required string Tag { get; init; }

    /// <summary>
    /// A TAG de placa que o cadastro conhece. Preenchida em 55% das linhas do cadastro, e por
    /// isso nula com frequencia mesmo quando a placa existe.
    /// </summary>
    public string? EquipmentTag { get; init; }

    public string? Type { get; init; }

    /// <summary>Subestação do cadastro Spring; nula quando a fonte não informa localização.</summary>
    public string? Location { get; init; }

    public required string Company { get; init; }

    public required string? Identification { get; init; }

    public required int ReportCount { get; init; }

    public required string? MostRecentReportDate { get; init; }

    /// <summary>
    /// Se o cadastro tem alguma coisa a dizer sobre este equipamento. Falso quando o
    /// equipamento nao tem numero de serie, quando o cadastro nao conhece a serie, e quando as
    /// linhas que ele tem para a serie nao sustentam nenhum campo.
    ///
    /// Existe porque "o cadastro nao conhece este equipamento" e "o cadastro conhece e esta
    /// coluna esta em branco" sao fatos diferentes, e so com os campos nulos a tela nao
    /// consegue distinguir os dois. Com o booleano ela diz a frase certa uma vez, em vez de
    /// repetir "Nao informado" em sete cartoes sem explicar por que.
    /// </summary>
    public required bool NameplateKnown { get; init; }

    public EquipmentStatusResponse? Status { get; init; }
}

public sealed record EquipmentStatusResponse
{
    public required string Overall { get; init; }

    public SectionStatusResponse? Chromatography { get; init; }

    public SectionStatusResponse? PhysicalChemical { get; init; }

    public string? LastReportDate { get; init; }

    public required DateTime UpdatedAt { get; init; }

    public static EquipmentStatusResponse From(EquipmentStatus status)
    {
        ArgumentNullException.ThrowIfNull(status);

        return new EquipmentStatusResponse
        {
            Overall = OverallStatusText.Of(status.Overall),
            Chromatography = SectionStatusResponse.From(status.Chromatography),
            PhysicalChemical = SectionStatusResponse.From(status.PhysicalChemical),
            LastReportDate = status.LastReportDate is DateTime last ? WireDate.From(last) : null,
            UpdatedAt = DateTime.SpecifyKind(status.UpdatedAt, DateTimeKind.Utc)
        };
    }
}

public sealed record SectionStatusResponse
{
    public string? Conformity { get; init; }

    public string? ResultLabel { get; init; }

    public static SectionStatusResponse? From(SectionStatus? section)
    {
        return section is null
            ? null
            : new SectionStatusResponse
            {
                Conformity = ConformityText.Of(section.Conformity),
                ResultLabel = section.ResultLabel
            };
    }
}

public sealed record EquipmentStatusRefreshResponse
{
    public required bool AlreadyRunning { get; init; }

    public required bool Success { get; init; }

    public required int ReportsRead { get; init; }

    public required int ReportsUnreadable { get; init; }

    public EquipmentStatusResponse? Status { get; init; }
}

public sealed record EquipmentStatusBatchRequest
{
    public IReadOnlyList<string>? Ids { get; init; }
}

public sealed record EquipmentStatusAcceptedResponse
{
    public required int Accepted { get; init; }

    public required bool AlreadyRunning { get; init; }
}

/// <summary>
/// O detalhe acrescenta o que a tela de dentro mostra nos cartoes. Tipo do oleo, tensao e
/// potencia seguem a mesma regra de <see cref="EquipmentResponse.Type"/>: vem do cadastro,
/// campo a campo, e sao nulas quando o cadastro nao diz.
///
/// Os tres campos novos sao colunas que o cadastro tem e que a tela ainda nao mostrava:
/// fabricante, ano de fabricacao e volume de oleo. Eles nao viajam na listagem, so aqui, para
/// a listagem nao carregar em toda linha nove campos que ela nao imprime.
///
/// Nenhum deles ganha unidade que o cadastro nao escreveu. <see cref="Voltage"/> sai como o
/// texto da coluna <c>Tensao</c>, que em 643 linhas traz primario e secundario separados por
/// barra; <see cref="OilVolume"/> sai como o texto de <c>VolumeOleo</c>, que tem <c>1L</c> e
/// <c>6 KG</c> no meio dos numeros; <see cref="ManufacturingYear"/> sai como o texto de
/// <c>AnoFabricacao</c>, que em 1531 linhas nao e um ano de quatro digitos. So
/// <see cref="Power"/> e montada, de duas colunas, e so quando ha valor.
/// </summary>
public sealed record EquipmentDetailResponse
{
    public required string Id { get; init; }

    public string? SerialNumber { get; init; }

    public required string Tag { get; init; }

    public string? EquipmentTag { get; init; }

    public string? Type { get; init; }

    public string? Location { get; init; }

    public required string Company { get; init; }

    public required string? Identification { get; init; }

    public required int ReportCount { get; init; }

    public required string? MostRecentReportDate { get; init; }

    public string? OilType { get; init; }

    public string? Voltage { get; init; }

    public string? Power { get; init; }

    public string? Manufacturer { get; init; }

    public string? ManufacturingYear { get; init; }

    public string? OilVolume { get; init; }

    public required bool NameplateKnown { get; init; }
}

public sealed record EquipmentReportResponse
{
    public required string Id { get; init; }

    public required string Date { get; init; }

    public required ReportAnalysisType AnalysisType { get; init; }

    /// <summary>O tipo original do snapshot, preservado quando a tela não tem rótulo fechado.</summary>
    public string? AnalysisTypeCode { get; init; }

    public required ReportStatus Status { get; init; }

    /// <summary>Assinatura digital disponível no AutoLAB; não representa mera disponibilidade de PDF.</summary>
    public required bool HasSignedDocument { get; init; }

    /// <summary>Disponibilidade de download do PDF, sem afirmar assinatura digital.</summary>
    public bool PdfAvailable { get; init; }
}
