using Portal.Domain.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class TestedEquipmentTests
{
    /// <summary>
    /// O risco que o agrupamento existe para evitar. Dois laudos reais podem ter a coluna de
    /// numero de serie em branco, e agrupar por ela juntaria transformadores diferentes num
    /// equipamento so, com os laudos de um aparecendo no historico do outro.
    /// </summary>
    [Fact]
    public void ReportsWithoutASerialNumberDoNotBecomeOnePieceOfEquipment()
    {
        IReadOnlyList<TestedEquipment> equipmentList = TestedEquipment.Gather([
            AnalysisBuilder.Valid(protocoloLactec: "01439/2026-004", serialNumber: null),
            AnalysisBuilder.Valid(protocoloLactec: "01482/2026-001", serialNumber: null)
        ]);

        Assert.Equal(2, equipmentList.Count);
        Assert.Equal(
            ["01439/2026-004", "01482/2026-001"],
            equipmentList.Select(equipment => equipment.EquipmentKey).Order());
        Assert.All(equipmentList, equipment => Assert.Single(equipment.Reports));
    }

    [Fact]
    public void ReportsOfTheSameSerialNumberBecomeOneEquipmentWithAHistory()
    {
        IReadOnlyList<TestedEquipment> equipmentList = TestedEquipment.Gather([
            AnalysisBuilder.Valid(
                protocoloLactec: "01417/2026-001",
                analysisEndDate: new DateTime(2026, 6, 23)),
            AnalysisBuilder.Valid(
                protocoloLactec: "00712/2026-001",
                analysisEndDate: new DateTime(2026, 3, 18))
        ]);

        TestedEquipment only = Assert.Single(equipmentList);

        Assert.Equal("571289", only.EquipmentKey);
        Assert.Equal(2, only.Reports.Count);
    }

    /// <summary>
    /// A primeira linha da primeira pagina do detalhe e o laudo mais recente. Inverter a
    /// ordem nao quebra nada visivel: a tela mostra o laudo mais velho como se fosse o
    /// resultado atual.
    /// </summary>
    [Fact]
    public void TheHistoryComesFromTheMostRecentReportToTheOldest()
    {
        TestedEquipment equipment = Assert.Single(TestedEquipment.Gather([
            AnalysisBuilder.Valid(
                protocoloLactec: "02114/2025-001",
                analysisEndDate: new DateTime(2025, 9, 15)),
            AnalysisBuilder.Valid(
                protocoloLactec: "01417/2026-001",
                analysisEndDate: new DateTime(2026, 6, 23)),
            AnalysisBuilder.Valid(
                protocoloLactec: "02630/2025-001",
                analysisEndDate: new DateTime(2025, 12, 10))
        ]));

        Assert.Equal(
            ["01417/2026-001", "02630/2025-001", "02114/2025-001"],
            equipment.Reports.Select(report => report.ProtocoloLactec));
        Assert.Equal("01417/2026-001", equipment.MostRecentReport.ProtocoloLactec);
    }

    [Fact]
    public void EquipmentWithoutASerialIsDisplayedByTheReportIdentification()
    {
        TestedEquipment equipment = Assert.Single(TestedEquipment.Gather([
            AnalysisBuilder.Valid(
                protocoloLactec: "01439/2026-004",
                identification: "LACTEC-02465/2026",
                serialNumber: null)
        ]));

        Assert.Null(equipment.SerialNumber);
        Assert.Equal("LACTEC-02465/2026", equipment.DisplayLabel);
    }

    [Fact]
    public void EquipmentWithASerialIsDisplayedByTheSerial()
    {
        TestedEquipment equipment = Assert.Single(TestedEquipment.Gather([
            AnalysisBuilder.Valid(serialNumber: "111.594/1")
        ]));

        Assert.Equal("111.594/1", equipment.DisplayLabel);
    }

    [Fact]
    public void AReportWithOnlyAPhysicalChemicalPropertyIsAnnouncedAsPhysicalChemical()
    {
        TestedEquipment equipment = Assert.Single(TestedEquipment.Gather([
            AnalysisBuilder.Valid(measurements:
            [
                AnalysisBuilder.MeasurementOf("Rigidez Dielétrica - Calota (kV)", "48")
            ])
        ]));

        Assert.Equal(ReportAnalysisType.PhysicalChemical, equipment.MostRecentReport.AnalysisType);
    }

    /// <summary>
    /// Laudo que mediu as duas coisas abre a aba de gases, que e a que tem diagnostico,
    /// totalizador e grafico. A precedencia e escolha, nao acidente, e esta aqui para a
    /// escolha nao mudar sem alguem decidir.
    /// </summary>
    [Fact]
    public void AReportWithGasAndPropertyIsAnnouncedAsChromatography()
    {
        TestedEquipment equipment = Assert.Single(TestedEquipment.Gather([
            AnalysisBuilder.Valid(measurements:
            [
                AnalysisBuilder.MeasurementOf("Rigidez Dielétrica - Calota (kV)", "48"),
                AnalysisBuilder.MeasurementOf("Hidrogênio", "60")
            ])
        ]));

        Assert.Equal(ReportAnalysisType.Chromatography, equipment.MostRecentReport.AnalysisType);
    }

    [Fact]
    public void AReportWithoutAnIssueDateIsNotCompleteYet()
    {
        EquipmentReport withoutIssueDate = new(
            "01417/2026-001",
            "LACTEC-02272/2026",
            new DateTime(2026, 6, 23),
            default,
            ReportAnalysisType.Chromatography);

        EquipmentReport withIssueDate = withoutIssueDate with { IssueDate = new DateTime(2026, 6, 24) };

        Assert.Equal(ReportStatus.InProgress, withoutIssueDate.Status);
        Assert.Equal(ReportStatus.Completed, withIssueDate.Status);
    }

    [Fact]
    public void TheListingStartsWithTheEquipmentOfTheMostRecentAnalysis()
    {
        IReadOnlyList<TestedEquipment> equipmentList = TestedEquipment.Gather([
            AnalysisBuilder.Valid(
                protocoloLactec: "01417/2026-001",
                serialNumber: "571289",
                analysisEndDate: new DateTime(2026, 6, 23)),
            AnalysisBuilder.Valid(
                protocoloLactec: "01351/2026-004",
                serialNumber: "111.594/1",
                analysisEndDate: new DateTime(2026, 7, 6))
        ]);

        Assert.Equal(
            ["111.594/1", "571289"],
            equipmentList.Select(equipment => equipment.EquipmentKey));
    }
}
