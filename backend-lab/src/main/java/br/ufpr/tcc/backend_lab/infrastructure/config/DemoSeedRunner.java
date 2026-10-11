package br.ufpr.tcc.backend_lab.infrastructure.config;

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

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public DemoSeedRunner(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        new ResourceDatabasePopulator(new ClassPathResource("database/demo-seed.sql"))
                .execute(dataSource);

        Long quoteId =
                jdbc.queryForObject(
                        "SELECT id FROM orcamentos WHERE code = ?", Long.class, DEMO_QUOTE_CODE);
        byte[] pdf = new ClassPathResource("database/demo-quote-lactec.pdf").getContentAsByteArray();
        int updated =
                jdbc.update(
                        "UPDATE documentos_orcamentos SET content = ? WHERE orcamento_id = ?",
                        pdf,
                        quoteId);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO documentos_orcamentos (orcamento_id, content) VALUES (?, ?)",
                    quoteId,
                    pdf);
        }
    }
}
