using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Portal.Adapters.PortalStore.Migrations
{
    /// <inheritdoc />
    public partial class ProjecaoDosLaudos : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "EquipmentStatuses",
                schema: "portal",
                columns: table => new
                {
                    Cnpj = table.Column<string>(type: "char(14)", unicode: false, fixedLength: true, maxLength: 14, nullable: false),
                    EquipmentKey = table.Column<string>(type: "nvarchar(250)", maxLength: 250, nullable: false),
                    SerialNumber = table.Column<string>(type: "nvarchar(250)", maxLength: 250, nullable: true),
                    DisplayLabel = table.Column<string>(type: "nvarchar(250)", maxLength: 250, nullable: false),
                    Tag = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: true),
                    EquipmentType = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: true),
                    NameplateKnown = table.Column<bool>(type: "bit", nullable: false),
                    Company = table.Column<string>(type: "nvarchar(250)", maxLength: 250, nullable: false),
                    Identification = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    ReportCount = table.Column<int>(type: "int", nullable: false),
                    MostRecentReportDate = table.Column<DateTime>(type: "datetime2", nullable: false),
                    LastReportDate = table.Column<DateTime>(type: "datetime2", nullable: true),
                    ChromatographyConformity = table.Column<string>(type: "varchar(20)", unicode: false, maxLength: 20, nullable: true),
                    ChromatographyLabel = table.Column<string>(type: "nvarchar(300)", maxLength: 300, nullable: true),
                    PhysicalChemicalConformity = table.Column<string>(type: "varchar(20)", unicode: false, maxLength: 20, nullable: true),
                    PhysicalChemicalLabel = table.Column<string>(type: "nvarchar(300)", maxLength: 300, nullable: true),
                    HasChromatography = table.Column<bool>(type: "bit", nullable: false),
                    HasPhysicalChemical = table.Column<bool>(type: "bit", nullable: false),
                    OverallStatus = table.Column<string>(type: "varchar(20)", unicode: false, maxLength: 20, nullable: false),
                    UpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_EquipmentStatuses", x => new { x.Cnpj, x.EquipmentKey });
                });

            migrationBuilder.CreateTable(
                name: "LoadScopes",
                schema: "portal",
                columns: table => new
                {
                    Cnpj = table.Column<string>(type: "char(14)", unicode: false, fixedLength: true, maxLength: 14, nullable: false),
                    Active = table.Column<bool>(type: "bit", nullable: false),
                    ProcessedUntilIssueDate = table.Column<DateTime>(type: "datetime2", nullable: true),
                    ProcessedUntilReportId = table.Column<Guid>(type: "uniqueidentifier", nullable: true),
                    LastRunAt = table.Column<DateTime>(type: "datetime2", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_LoadScopes", x => x.Cnpj);
                });

            migrationBuilder.CreateTable(
                name: "ReportReadings",
                schema: "portal",
                columns: table => new
                {
                    SignedFileId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    ReportId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    Identification = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    Cnpj = table.Column<string>(type: "char(14)", unicode: false, fixedLength: true, maxLength: 14, nullable: false),
                    EquipmentKey = table.Column<string>(type: "nvarchar(250)", maxLength: 250, nullable: true),
                    IssueDate = table.Column<DateTime>(type: "datetime2", nullable: true),
                    Outcome = table.Column<string>(type: "varchar(20)", unicode: false, maxLength: 20, nullable: false),
                    ExtractorVersion = table.Column<int>(type: "int", nullable: false),
                    ReadAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                    Attempts = table.Column<int>(type: "int", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_ReportReadings", x => x.SignedFileId);
                });

            migrationBuilder.CreateTable(
                name: "ReportSectionResults",
                schema: "portal",
                columns: table => new
                {
                    SignedFileId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    Section = table.Column<string>(type: "varchar(20)", unicode: false, maxLength: 20, nullable: false),
                    ResultLabel = table.Column<string>(type: "nvarchar(300)", maxLength: 300, nullable: true),
                    Conformity = table.Column<string>(type: "varchar(20)", unicode: false, maxLength: 20, nullable: true),
                    ResultColor = table.Column<string>(type: "char(7)", unicode: false, fixedLength: true, maxLength: 7, nullable: true),
                    Title = table.Column<string>(type: "nvarchar(300)", maxLength: 300, nullable: true),
                    Paragraphs = table.Column<string>(type: "nvarchar(max)", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_ReportSectionResults", x => new { x.SignedFileId, x.Section });
                    table.ForeignKey(
                        name: "FK_ReportSectionResults_ReportReadings_SignedFileId",
                        column: x => x.SignedFileId,
                        principalSchema: "portal",
                        principalTable: "ReportReadings",
                        principalColumn: "SignedFileId",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_EquipmentStatuses_Cnpj_OverallStatus_LastReportDate",
                schema: "portal",
                table: "EquipmentStatuses",
                columns: new[] { "Cnpj", "OverallStatus", "LastReportDate" });

            migrationBuilder.CreateIndex(
                name: "IX_ReportReadings_Cnpj_Identification",
                schema: "portal",
                table: "ReportReadings",
                columns: new[] { "Cnpj", "Identification" });

            migrationBuilder.CreateIndex(
                name: "IX_ReportReadings_Cnpj_Outcome",
                schema: "portal",
                table: "ReportReadings",
                columns: new[] { "Cnpj", "Outcome" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "EquipmentStatuses",
                schema: "portal");

            migrationBuilder.DropTable(
                name: "LoadScopes",
                schema: "portal");

            migrationBuilder.DropTable(
                name: "ReportSectionResults",
                schema: "portal");

            migrationBuilder.DropTable(
                name: "ReportReadings",
                schema: "portal");
        }
    }
}
