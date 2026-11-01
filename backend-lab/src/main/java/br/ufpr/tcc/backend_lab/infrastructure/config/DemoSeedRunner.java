package br.ufpr.tcc.backend_lab.infrastructure.config;

import java.util.List;
import javax.sql.DataSource;

import br.ufpr.tcc.backend_lab.application.service.GeradorDocumentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "demo.seed.enabled", havingValue = "true")
public class DemoSeedRunner implements ApplicationRunner {
    private static final String DEMO_QUOTE_CODE = "DEMO-LACTEC-001";
    private static final String DEMO_SEED_VERSION = "development-catalog-v2";
    private static final String DEMO_EXECUTION_SEED_VERSION = "development-technical-executions-v1";
    private static final String HU25_SEED_VERSION = "hu25-ensaios-v1";
    private static final String CLIENT_PIPELINE_SEED_VERSION = "development-client-pipeline-v1";
    private static final String CLIENT_PIPELINE_QUOTE_PREFIX = "DEMO-PIPE-2026-%";
    private static final List<String> ETAPAS_SEM_PROPOSTA =
            List.of("Qualificação", "Em análise pela área", "Elaborando proposta");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final OrcamentoRepository orcamentoRepository;
    private final DocumentoOrcamentoRepository documentoOrcamentoRepository;
    private final GeradorDocumentoOrcamento geradorDocumentoOrcamento;

    public DemoSeedRunner(
            DataSource dataSource,
            JdbcTemplate jdbc,
            OrcamentoRepository orcamentoRepository,
            DocumentoOrcamentoRepository documentoOrcamentoRepository,
            GeradorDocumentoOrcamento geradorDocumentoOrcamento) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.orcamentoRepository = orcamentoRepository;
        this.documentoOrcamentoRepository = documentoOrcamentoRepository;
        this.geradorDocumentoOrcamento = geradorDocumentoOrcamento;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS demo_seed_runs (seed_key VARCHAR(100) PRIMARY KEY)");
        if (!seedVersionExists(DEMO_SEED_VERSION)) {
            new ResourceDatabasePopulator(new ClassPathResource("database/demo-seed.sql"))
                    .execute(dataSource);
            inserirPdfDeDemonstracaoSeAusente();
            jdbc.update("INSERT INTO demo_seed_runs (seed_key) VALUES (?)", DEMO_SEED_VERSION);
        }
        if (!seedVersionExists(HU25_SEED_VERSION)) {
            new ResourceDatabasePopulator(new ClassPathResource("database/demo-seed-hu25.sql"))
                    .execute(dataSource);
            jdbc.update("INSERT INTO demo_seed_runs (seed_key) VALUES (?)", HU25_SEED_VERSION);
        }
        if (!seedVersionExists(DEMO_EXECUTION_SEED_VERSION)) {
            new ResourceDatabasePopulator(new ClassPathResource("database/demo-execution-seed.sql"))
                    .execute(dataSource);
            jdbc.update("INSERT INTO demo_seed_runs (seed_key) VALUES (?)", DEMO_EXECUTION_SEED_VERSION);
        }
        if (!seedVersionExists(CLIENT_PIPELINE_SEED_VERSION)) {
            new ResourceDatabasePopulator(
                            new ClassPathResource("database/demo-client-pipeline-seed.sql"))
                    .execute(dataSource);
            inserirPdfsDasPropostasDemonstrativas();
            jdbc.update("INSERT INTO demo_seed_runs (seed_key) VALUES (?)", CLIENT_PIPELINE_SEED_VERSION);
        }
    }

    private void inserirPdfsDasPropostasDemonstrativas() {
        List<Long> quoteIds =
                jdbc.query(
                        "SELECT id FROM orcamentos WHERE code LIKE ? AND stage NOT IN (?, ?, ?) ORDER BY code",
                        (resultSet, rowNum) -> resultSet.getLong("id"),
                        CLIENT_PIPELINE_QUOTE_PREFIX,
                        ETAPAS_SEM_PROPOSTA.get(0),
                        ETAPAS_SEM_PROPOSTA.get(1),
                        ETAPAS_SEM_PROPOSTA.get(2));

        for (Long quoteId : quoteIds) {
            if (documentoOrcamentoRepository.existsById(quoteId)) {
                continue;
            }
            var orcamento =
                    orcamentoRepository
                            .buscarDetalhePorId(quoteId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Orçamento de demonstração não encontrado: " + quoteId));
            var documento = new DocumentoOrcamento();
            documento.setOrcamento(orcamento);
            documento.setContent(geradorDocumentoOrcamento.gerar(orcamento));
            documentoOrcamentoRepository.save(documento);
        }
    }

    private void inserirPdfDeDemonstracaoSeAusente() throws Exception {
        List<Long> quoteIds =
                jdbc.query(
                        "SELECT id FROM orcamentos WHERE code = ?",
                        (resultSet, rowNum) -> resultSet.getLong("id"),
                        DEMO_QUOTE_CODE);
        if (!quoteIds.isEmpty()) {
            Long quoteId = quoteIds.get(0);
            Integer documentCount =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM documentos_orcamentos WHERE orcamento_id = ?",
                            Integer.class,
                            quoteId);
            if (documentCount != null && documentCount == 0) {
                byte[] pdf =
                        new ClassPathResource("database/demo-quote-lactec.pdf")
                                .getContentAsByteArray();
                jdbc.update(
                        "INSERT INTO documentos_orcamentos (orcamento_id, content) VALUES (?, ?)",
                        quoteId,
                        pdf);
            }
        }
    }

    private boolean seedVersionExists(String version) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM demo_seed_runs WHERE seed_key = ?",
                        Integer.class,
                        version);
        return count != null && count > 0;
    }
}
