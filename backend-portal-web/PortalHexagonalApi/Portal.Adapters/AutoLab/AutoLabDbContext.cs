using Microsoft.EntityFrameworkCore;

namespace Portal.Adapters.AutoLab;

public sealed class AutoLabDbContext : DbContext
{
    public const string Schema = "dbo";

    /// <summary>
    /// O nivel de compatibilidade do banco do AutoLAB em producao. Abaixo de 130 o SQL Server
    /// nao tem OPENJSON, que o EF usaria para listas grandes; declarado, o EF escreve a lista
    /// dentro do IN.
    /// </summary>
    public const int SqlServerCompatibilityLevel = 120;

    public AutoLabDbContext(DbContextOptions<AutoLabDbContext> options)
        : base(options)
    {
    }

    public DbSet<VwDadosEnsaiosRecord> DadosEnsaios => Set<VwDadosEnsaiosRecord>();

    public DbSet<EquipamentosRecord> Equipamentos => Set<EquipamentosRecord>();

    public DbSet<SituaProcessRecord> SituaProcess => Set<SituaProcessRecord>();

    public DbSet<VwRelatoriosEnsaiosRecord> RelatoriosEnsaios => Set<VwRelatoriosEnsaiosRecord>();

    public DbSet<RelatorioEmissaoRecord> RelatorioEmissao => Set<RelatorioEmissaoRecord>();

    public DbSet<FileDataRecord> FileData => Set<FileDataRecord>();

    public DbSet<ClienteRecord> Clientes => Set<ClienteRecord>();

    public DbSet<SolicitacaoServicoRecord> SolicitacoesServico => Set<SolicitacaoServicoRecord>();

    public DbSet<AmostraObjetoRecord> AmostrasObjeto => Set<AmostraObjetoRecord>();

    public DbSet<ProtocoloAmostraRecord> ProtocolosAmostra => Set<ProtocoloAmostraRecord>();

    public override int SaveChanges(bool acceptAllChangesOnSuccess)
    {
        throw new NotSupportedException("O banco do AutoLAB e somente leitura para o portal.");
    }

    public override Task<int> SaveChangesAsync(
        bool acceptAllChangesOnSuccess,
        CancellationToken cancellationToken = default)
    {
        throw new NotSupportedException("O banco do AutoLAB e somente leitura para o portal.");
    }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<VwDadosEnsaiosRecord>(view =>
        {
            view.HasNoKey().ToView("vwDadosEnsaios", Schema);
            view.Property(row => row.Identificacao).HasColumnName("Identificacao").HasMaxLength(100);
            view.Property(row => row.ProtocoloLactec).HasColumnName("ProtocoloLactec").HasMaxLength(100);
            view.Property(row => row.NumSerie).HasColumnName("NumSerie").HasMaxLength(250);
            view.Property(row => row.Name).HasColumnName("Name").HasMaxLength(250);
            view.Property(row => row.Resultado).HasColumnName("Resultado");
            view.Property(row => row.EmpresaTrafo).HasColumnName("EmpresaTrafo");
            view.Property(row => row.DataEmissao).HasColumnName("DataEmissao");
            view.Property(row => row.DataFinalAnalise).HasColumnName("DataFinalAnalise");
        });

        modelBuilder.Entity<EquipamentosRecord>(view =>
        {
            view.HasNoKey().ToView("Equipamentos", Schema);
            view.Property(row => row.NumSerie).HasColumnName("NumSerie").HasMaxLength(250);
            view.Property(row => row.Tag).HasColumnName("Tag").HasMaxLength(100);
            view.Property(row => row.AnoFabricacao).HasColumnName("AnoFabricacao").HasMaxLength(100);
            view.Property(row => row.Potencia).HasColumnName("Potencia").HasMaxLength(100);
            view.Property(row => row.Tensao).HasColumnName("Tensao").HasMaxLength(100);
            view.Property(row => row.Fabricante).HasColumnName("Fabricante").HasMaxLength(250);
            view.Property(row => row.VolumeOleo).HasColumnName("VolumeOleo").HasMaxLength(100);
            view.Property(row => row.UnidadePotencia).HasColumnName("UnidadePotencia").HasMaxLength(20);
            view.Property(row => row.TipoDeOleo).HasColumnName("TipoDeOleo").HasMaxLength(100);
            view.Property(row => row.TipoEquipamento).HasColumnName("TipoEquipamento").HasMaxLength(100);
        });

        modelBuilder.Entity<SituaProcessRecord>(view =>
        {
            view.HasNoKey().ToView("SituaProcess", Schema);
            view.Property(row => row.OrcamentoCRM).HasColumnName("OrcamentoCRM").HasMaxLength(100);
            view.Property(row => row.Recebido).HasColumnName("Recebido");
            view.Property(row => row.Protocolado).HasColumnName("Protocolado");
            view.Property(row => row.SituacaoEnsaios).HasColumnName("SituacaoEnsaios");
            view.Property(row => row.SolicitacaoRelatorio).HasColumnName("SolicitacaoRelatorio");
        });

        modelBuilder.Entity<VwRelatoriosEnsaiosRecord>(view =>
        {
            view.HasNoKey().ToView("vwRelatoriosEnsaios", Schema);
            view.Property(row => row.Oid).HasColumnName("Oid");
            view.Property(row => row.IDAmostra).HasColumnName("IDAmostra");
            view.Property(row => row.Nome).HasColumnName("Nome").HasMaxLength(42).IsUnicode(false);
            view.Property(row => row.ClienteSolicitante).HasColumnName("ClienteSolicitante").HasMaxLength(250);
            view.Property(row => row.DataFinalProgramadaCliente).HasColumnName("DataFinalProgramadaCliente");
            view.Property(row => row.SituacaoRelatorio).HasColumnName("SituacaoRelatorio").HasMaxLength(11).IsUnicode(false);
        });

        modelBuilder.Entity<RelatorioEmissaoRecord>(table =>
        {
            table.ToTable("RelatorioEmissao", Schema);
            table.HasKey(row => row.Oid);
            table.Property(row => row.Oid).HasColumnName("Oid").ValueGeneratedNever();
            table.Property(row => row.Identificacao).HasColumnName("Identificacao").HasMaxLength(100);
            table.Property(row => row.PdfAssinado).HasColumnName("PdfAssinado");
            table.Property(row => row.GCRecord).HasColumnName("GCRecord");
            table.Property(row => row.Revisao).HasColumnName("Revisao");
            table.Property(row => row.RelatorioEnsaioSubstituto).HasColumnName("RelatorioEnsaioSubstituto");
            table.Property(row => row.DataEmissao).HasColumnName("DataEmissao");
            table.Property(row => row.DataPublicacao).HasColumnName("DataPublicacao");
            table.Property(row => row.Solicitacao).HasColumnName("Solicitacao");
        });

        modelBuilder.Entity<FileDataRecord>(table =>
        {
            table.ToTable("FileData", Schema);
            table.HasKey(row => row.Oid);
            table.Property(row => row.Oid).HasColumnName("Oid").ValueGeneratedNever();
            table.Property(row => row.FileName).HasColumnName("FileName").HasMaxLength(260);
            table.Property(row => row.Content).HasColumnName("Content");
            table.Property(row => row.GCRecord).HasColumnName("GCRecord");
        });

        modelBuilder.Entity<ClienteRecord>(table =>
        {
            table.ToTable("Cliente", Schema);
            table.HasKey(row => row.Oid);
            table.Property(row => row.Oid).HasColumnName("Oid").ValueGeneratedNever();
            table.Property(row => row.CadastroNacional).HasColumnName("cli_cadastronacional_a").HasMaxLength(30);
            table.Property(row => row.GCRecord).HasColumnName("GCRecord");
        });

        modelBuilder.Entity<SolicitacaoServicoRecord>(table =>
        {
            table.ToTable("SolicitacaoServico", Schema);
            table.HasKey(row => row.Oid);
            table.Property(row => row.Oid).HasColumnName("Oid").ValueGeneratedNever();
            table.Property(row => row.NumeroSolicitacao).HasColumnName("NumeroSolicitacao").HasMaxLength(15);
            table.Property(row => row.OrcamentoCRM).HasColumnName("OrcamentoCRM");
            table.Property(row => row.Cliente).HasColumnName("Cliente");
            table.Property(row => row.GCRecord).HasColumnName("GCRecord");
        });

        modelBuilder.Entity<AmostraObjetoRecord>(table =>
        {
            table.ToTable("AmostraObjeto", Schema);
            table.HasKey(row => row.Oid);
            table.Property(row => row.Oid).HasColumnName("Oid").ValueGeneratedNever();
            table.Property(row => row.ProtocoloAmostra).HasColumnName("ProtocoloAmostra");
            table.Property(row => row.SolicitacaoServico).HasColumnName("SolicitacaoServico");
            table.Property(row => row.GCRecord).HasColumnName("GCRecord");
        });

        modelBuilder.Entity<ProtocoloAmostraRecord>(table =>
        {
            table.ToTable("ProtocoloAmostra", Schema);
            table.HasKey(row => row.Oid);
            table.Property(row => row.Oid).HasColumnName("Oid").ValueGeneratedNever();
            table.Property(row => row.Identificacao).HasColumnName("Identificacao").HasMaxLength(100);
            table.Property(row => row.GCRecord).HasColumnName("GCRecord");
        });
    }
}
