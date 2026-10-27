package br.ufpr.tcc.backend_lab.infrastructure.config;

import java.util.List;
import javax.sql.DataSource;

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

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public DemoSeedRunner(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS demo_seed_runs (seed_key VARCHAR(100) PRIMARY KEY)");
        if (!seedVersionExists()) {
            new ResourceDatabasePopulator(new ClassPathResource("database/demo-seed.sql"))
                    .execute(dataSource);
            inserirPdfDeDemonstracaoSeAusente();
            jdbc.update("INSERT INTO demo_seed_runs (seed_key) VALUES (?)", DEMO_SEED_VERSION);
        }
        if (!seedVersionExists(DEMO_EXECUTION_SEED_VERSION)) {
            new ResourceDatabasePopulator(
                            new ClassPathResource("database/demo-execution-seed.sql"))
                    .execute(dataSource);
            jdbc.update(
                    "INSERT INTO demo_seed_runs (seed_key) VALUES (?)",
                    DEMO_EXECUTION_SEED_VERSION);
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

    private boolean seedVersionExists() {
        return seedVersionExists(DEMO_SEED_VERSION);
    }

    private boolean seedVersionExists(String seedVersion) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM demo_seed_runs WHERE seed_key = ?",
                        Integer.class,
                        seedVersion);
        return count != null && count > 0;
    }
}
