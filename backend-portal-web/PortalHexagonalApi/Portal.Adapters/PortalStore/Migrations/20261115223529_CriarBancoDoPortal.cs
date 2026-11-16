using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Portal.Adapters.PortalStore.Migrations
{
    /// <inheritdoc />
    public partial class CriarBancoDoPortal : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.EnsureSchema(
                name: "portal");

            migrationBuilder.CreateTable(
                name: "ClientDecisions",
                schema: "portal",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    QuoteId = table.Column<string>(type: "varchar(18)", unicode: false, maxLength: 18, nullable: false),
                    QuoteCode = table.Column<string>(type: "varchar(32)", unicode: false, maxLength: 32, nullable: false),
                    Kind = table.Column<string>(type: "varchar(20)", unicode: false, maxLength: 20, nullable: false),
                    Slot = table.Column<string>(type: "varchar(10)", unicode: false, maxLength: 10, nullable: false),
                    RecordedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    AuthorUserId = table.Column<string>(type: "varchar(36)", unicode: false, maxLength: 36, nullable: false),
                    AuthorName = table.Column<string>(type: "nvarchar(150)", maxLength: 150, nullable: false),
                    AuthorEmail = table.Column<string>(type: "varchar(254)", unicode: false, maxLength: 254, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_ClientDecisions", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "AcceptanceTerms",
                schema: "portal",
                columns: table => new
                {
                    DecisionId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    BillingCompany = table.Column<string>(type: "nvarchar(150)", maxLength: 150, nullable: false),
                    BillingCnpj = table.Column<string>(type: "char(14)", unicode: false, fixedLength: true, maxLength: 14, nullable: false),
                    BillingStateRegistration = table.Column<string>(type: "varchar(14)", unicode: false, maxLength: 14, nullable: false),
                    BillingStreet = table.Column<string>(type: "nvarchar(150)", maxLength: 150, nullable: false),
                    BillingCity = table.Column<string>(type: "nvarchar(60)", maxLength: 60, nullable: false),
                    BillingState = table.Column<string>(type: "char(2)", unicode: false, fixedLength: true, maxLength: 2, nullable: false),
                    BillingDistrict = table.Column<string>(type: "nvarchar(60)", maxLength: 60, nullable: false),
                    BillingPostalCode = table.Column<string>(type: "char(8)", unicode: false, fixedLength: true, maxLength: 8, nullable: false),
                    BillingContact = table.Column<string>(type: "nvarchar(150)", maxLength: 150, nullable: false),
                    BillingPhone = table.Column<string>(type: "varchar(15)", unicode: false, maxLength: 15, nullable: false),
                    BillingInvoiceEmails = table.Column<string>(type: "varchar(500)", unicode: false, maxLength: 500, nullable: false),
                    RequesterName = table.Column<string>(type: "nvarchar(150)", maxLength: 150, nullable: false),
                    ResponsibleName = table.Column<string>(type: "nvarchar(150)", maxLength: 150, nullable: false),
                    JobTitle = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    Department = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    ApprovalDate = table.Column<DateOnly>(type: "date", nullable: false),
                    AllowsResultsByEmail = table.Column<bool>(type: "bit", nullable: false),
                    ResultsEmail = table.Column<string>(type: "varchar(254)", unicode: false, maxLength: 254, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_AcceptanceTerms", x => x.DecisionId);
                    table.ForeignKey(
                        name: "FK_AcceptanceTerms_ClientDecisions_DecisionId",
                        column: x => x.DecisionId,
                        principalSchema: "portal",
                        principalTable: "ClientDecisions",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "PurchaseOrders",
                schema: "portal",
                columns: table => new
                {
                    DecisionId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    FileName = table.Column<string>(type: "nvarchar(255)", maxLength: 255, nullable: false),
                    SizeBytes = table.Column<int>(type: "int", nullable: false),
                    Sha256 = table.Column<string>(type: "char(64)", unicode: false, fixedLength: true, maxLength: 64, nullable: false),
                    Content = table.Column<byte[]>(type: "varbinary(max)", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_PurchaseOrders", x => x.DecisionId);
                    table.ForeignKey(
                        name: "FK_PurchaseOrders_ClientDecisions_DecisionId",
                        column: x => x.DecisionId,
                        principalSchema: "portal",
                        principalTable: "ClientDecisions",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "Rejections",
                schema: "portal",
                columns: table => new
                {
                    DecisionId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    Justification = table.Column<string>(type: "nvarchar(1000)", maxLength: 1000, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Rejections", x => x.DecisionId);
                    table.ForeignKey(
                        name: "FK_Rejections_ClientDecisions_DecisionId",
                        column: x => x.DecisionId,
                        principalSchema: "portal",
                        principalTable: "ClientDecisions",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_ClientDecisions_QuoteId_Slot",
                schema: "portal",
                table: "ClientDecisions",
                columns: new[] { "QuoteId", "Slot" },
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "AcceptanceTerms",
                schema: "portal");

            migrationBuilder.DropTable(
                name: "PurchaseOrders",
                schema: "portal");

            migrationBuilder.DropTable(
                name: "Rejections",
                schema: "portal");

            migrationBuilder.DropTable(
                name: "ClientDecisions",
                schema: "portal");
        }
    }
}
