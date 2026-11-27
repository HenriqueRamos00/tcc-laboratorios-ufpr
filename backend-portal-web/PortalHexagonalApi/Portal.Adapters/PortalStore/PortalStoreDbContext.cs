using Microsoft.EntityFrameworkCore;

using Portal.Domain.Quotes;

namespace Portal.Adapters.PortalStore;

public sealed class PortalStoreDbContext : DbContext
{
    public const string Schema = "portal";

    public const int IdentificationLength = 100;
    public const int EquipmentKeyLength = 250;
    public const int LabelLength = 300;
    public const int ShortCodeLength = 20;

    public PortalStoreDbContext(DbContextOptions<PortalStoreDbContext> options)
        : base(options)
    {
    }

    public DbSet<ClientDecisionRow> ClientDecisions => Set<ClientDecisionRow>();

    public DbSet<ReportReadingRow> ReportReadings => Set<ReportReadingRow>();

    public DbSet<EquipmentStatusRow> EquipmentStatuses => Set<EquipmentStatusRow>();

    public DbSet<LoadScopeRow> LoadScopes => Set<LoadScopeRow>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.HasDefaultSchema(Schema);

        modelBuilder.Entity<ClientDecisionRow>(decision =>
        {
            decision.ToTable("ClientDecisions");
            decision.HasKey(row => row.Id);
            decision.Property(row => row.QuoteId).HasMaxLength(FieldLimits.SalesforceId).IsUnicode(false).IsRequired();
            decision.Property(row => row.QuoteCode).HasMaxLength(FieldLimits.QuoteCode).IsUnicode(false).IsRequired();
            decision.Property(row => row.Kind).HasMaxLength(20).IsUnicode(false).IsRequired();
            decision.Property(row => row.Slot).HasMaxLength(10).IsUnicode(false).IsRequired();
            decision.Property(row => row.AuthorUserId).HasMaxLength(FieldLimits.UserId).IsUnicode(false).IsRequired();
            decision.Property(row => row.AuthorName).HasMaxLength(FieldLimits.PersonName).IsRequired();
            decision.Property(row => row.AuthorEmail).HasMaxLength(FieldLimits.Email).IsUnicode(false).IsRequired();
            decision.HasIndex(row => new { row.QuoteId, row.Slot }).IsUnique();

            decision.HasOne(row => row.Terms).WithOne()
                .HasForeignKey<AcceptanceTermsRow>(row => row.DecisionId).OnDelete(DeleteBehavior.Cascade);
            decision.HasOne(row => row.PurchaseOrder).WithOne()
                .HasForeignKey<PurchaseOrderRow>(row => row.DecisionId).OnDelete(DeleteBehavior.Cascade);
            decision.HasOne(row => row.Rejection).WithOne()
                .HasForeignKey<RejectionRow>(row => row.DecisionId).OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<AcceptanceTermsRow>(terms =>
        {
            terms.ToTable("AcceptanceTerms");
            terms.HasKey(row => row.DecisionId);
            terms.Property(row => row.BillingCompany).HasMaxLength(FieldLimits.CompanyName);
            terms.Property(row => row.BillingCnpj).HasMaxLength(FieldLimits.Cnpj).IsFixedLength().IsUnicode(false);
            terms.Property(row => row.BillingStateRegistration).HasMaxLength(FieldLimits.StateRegistration).IsUnicode(false);
            terms.Property(row => row.BillingStreet).HasMaxLength(FieldLimits.Street);
            terms.Property(row => row.BillingCity).HasMaxLength(FieldLimits.City);
            terms.Property(row => row.BillingState).HasMaxLength(FieldLimits.State).IsFixedLength().IsUnicode(false);
            terms.Property(row => row.BillingDistrict).HasMaxLength(FieldLimits.District);
            terms.Property(row => row.BillingPostalCode).HasMaxLength(FieldLimits.PostalCode).IsFixedLength().IsUnicode(false);
            terms.Property(row => row.BillingContact).HasMaxLength(FieldLimits.PersonName);
            terms.Property(row => row.BillingPhone).HasMaxLength(FieldLimits.Phone).IsUnicode(false);
            terms.Property(row => row.RequesterName).HasMaxLength(FieldLimits.PersonName);
            terms.Property(row => row.ResponsibleName).HasMaxLength(FieldLimits.PersonName);
            terms.Property(row => row.JobTitle).HasMaxLength(FieldLimits.JobTitle);
            terms.Property(row => row.Department).HasMaxLength(FieldLimits.Department);
            terms.Property(row => row.ResultsEmail).HasMaxLength(FieldLimits.Email).IsUnicode(false);

            terms.HasMany(row => row.InvoiceEmails).WithOne()
                .HasForeignKey(row => row.DecisionId).OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<InvoiceEmailRow>(email =>
        {
            email.ToTable("AcceptanceInvoiceEmails");
            email.HasKey(row => new { row.DecisionId, row.Position });
            email.Property(row => row.Email).HasMaxLength(FieldLimits.Email).IsUnicode(false).IsRequired();
            email.HasIndex(row => new { row.DecisionId, row.Email }).IsUnique();
        });

        modelBuilder.Entity<PurchaseOrderRow>(order =>
        {
            order.ToTable("PurchaseOrders");
            order.HasKey(row => row.DecisionId);
            order.Property(row => row.FileName).HasMaxLength(FieldLimits.FileName).IsRequired();
            order.Property(row => row.Sha256).HasMaxLength(64).IsFixedLength().IsUnicode(false).IsRequired();
            order.Property(row => row.Content).IsRequired();
        });

        modelBuilder.Entity<RejectionRow>(rejection =>
        {
            rejection.ToTable("Rejections");
            rejection.HasKey(row => row.DecisionId);
            rejection.Property(row => row.Justification).HasMaxLength(FieldLimits.Justification).IsRequired();
        });

        modelBuilder.Entity<ReportReadingRow>(reading =>
        {
            reading.ToTable("ReportReadings");
            reading.HasKey(row => row.SignedFileId);
            reading.Property(row => row.SignedFileId).ValueGeneratedNever();
            reading.Property(row => row.Identification).HasMaxLength(IdentificationLength).IsRequired();
            reading.Property(row => row.Cnpj).HasMaxLength(FieldLimits.Cnpj).IsFixedLength().IsUnicode(false).IsRequired();
            reading.Property(row => row.EquipmentKey).HasMaxLength(EquipmentKeyLength);
            reading.Property(row => row.Outcome).HasMaxLength(ShortCodeLength).IsUnicode(false).IsRequired();
            reading.HasIndex(row => new { row.Cnpj, row.Identification });
            reading.HasIndex(row => new { row.Cnpj, row.Outcome });

            reading.HasMany(row => row.Sections).WithOne()
                .HasForeignKey(row => row.SignedFileId).OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<ReportSectionResultRow>(section =>
        {
            section.ToTable("ReportSectionResults");
            section.HasKey(row => new { row.SignedFileId, row.Section });
            section.Property(row => row.Section).HasMaxLength(ShortCodeLength).IsUnicode(false).IsRequired();
            section.Property(row => row.ResultLabel).HasMaxLength(LabelLength);
            section.Property(row => row.Conformity).HasMaxLength(ShortCodeLength).IsUnicode(false);
            section.Property(row => row.ResultColor).HasMaxLength(7).IsFixedLength().IsUnicode(false);
            section.Property(row => row.Title).HasMaxLength(LabelLength);
            section.Property(row => row.Paragraphs).IsRequired();
        });

        modelBuilder.Entity<EquipmentStatusRow>(status =>
        {
            status.ToTable("EquipmentStatuses");
            status.HasKey(row => new { row.Cnpj, row.EquipmentKey });
            status.Property(row => row.Cnpj).HasMaxLength(FieldLimits.Cnpj).IsFixedLength().IsUnicode(false).IsRequired();
            status.Property(row => row.EquipmentKey).HasMaxLength(EquipmentKeyLength).IsRequired();
            status.Property(row => row.SerialNumber).HasMaxLength(EquipmentKeyLength);
            status.Property(row => row.DisplayLabel).HasMaxLength(EquipmentKeyLength).IsRequired();
            status.Property(row => row.Tag).HasMaxLength(IdentificationLength);
            status.Property(row => row.EquipmentType).HasMaxLength(IdentificationLength);
            status.Property(row => row.Company).HasMaxLength(EquipmentKeyLength).IsRequired();
            status.Property(row => row.Identification).HasMaxLength(IdentificationLength).IsRequired();
            status.Property(row => row.ChromatographyConformity).HasMaxLength(ShortCodeLength).IsUnicode(false);
            status.Property(row => row.ChromatographyLabel).HasMaxLength(LabelLength);
            status.Property(row => row.PhysicalChemicalConformity).HasMaxLength(ShortCodeLength).IsUnicode(false);
            status.Property(row => row.PhysicalChemicalLabel).HasMaxLength(LabelLength);
            status.Property(row => row.OverallStatus).HasMaxLength(ShortCodeLength).IsUnicode(false).IsRequired();
            status.HasIndex(row => new { row.Cnpj, row.OverallStatus, row.LastReportDate });
        });

        modelBuilder.Entity<LoadScopeRow>(scope =>
        {
            scope.ToTable("LoadScopes");
            scope.HasKey(row => row.Cnpj);
            scope.Property(row => row.Cnpj).HasMaxLength(FieldLimits.Cnpj).IsFixedLength().IsUnicode(false).IsRequired();
        });
    }
}
