package br.ufpr.tcc.backend_lab.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(
        properties = {
            "demo.seed.enabled=true",
            "spring.datasource.url=jdbc:h2:mem:demo_seed_idempotency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class DemoSeedRunnerTests {
    private static final String EMAIL_CLIENTE_DEMO = "demo.cliente.mariana@example.test";
    private static final String EMAIL_LACTEC = "lactec@lactec.com";
    private static final String CNPJ_EMPRESA_DEMO = "98765432000198";
    private static final String CODIGO_ORCAMENTO_DEMO = "DEMO-SEED-2026-001";
    private static final String CODIGO_ORCAMENTO_REMOVIDO = "DEMO-SEED-2026-002";
    private static final String CODIGO_ORCAMENTO_LACTEC = "DEMO-LACTEC-001";

    @Autowired private DemoSeedRunner runner;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("preserva alterações e não marca uma carga incompleta como concluída")
    void preservaAlteracoesEPermiteTentarNovamenteAposFalha() throws Exception {
        assertEquals(6, contar("empresas"));
        assertEquals(13, contar("usuarios"));
        assertEquals(7, contar("clientes"));
        assertEquals(26, contar("orcamentos"));
        assertEquals(44, contar("itens_orcamento"));
        assertEquals(10, contar("cliente_empresas"));
        assertEquals(1, contar("documentos_orcamentos"));
        assertEquals(1, contar("demo_seed_runs"));
        assertTrue(
                passwordEncoder.matches(
                        "Demo@12345",
                        buscarTexto(
                                "usuarios",
                                "senha_hash",
                                "email_normalizado",
                                "demo.tecnico.helena@example.test")));
        assertTrue(
                passwordEncoder.matches(
                        "Demo@12345",
                        buscarTexto("usuarios", "senha_hash", "email_normalizado", EMAIL_CLIENTE_DEMO)));
        assertEquals(
                5,
                jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT status) FROM orcamentos "
                                + "WHERE code LIKE 'DEMO-SEED-2026-%' AND status IN "
                                + "('PendingAcceptance', 'InProgress', 'Finished', 'Refused', 'Cancelled')",
                        Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM orcamentos o "
                                + "WHERE o.code LIKE 'DEMO-SEED-2026-%' "
                                + "AND o.code NOT IN ('DEMO-SEED-2026-021', 'DEMO-SEED-2026-022', 'DEMO-SEED-2026-023') "
                                + "AND o.total_price <> (SELECT SUM(i.sample_count * i.unit_price) "
                                + "FROM itens_orcamento i WHERE i.orcamento_id = o.id)",
                        Integer.class));

        String senhaAtualizada = "Senha atualizada no cadastro";
        String hashSenhaAtualizada = passwordEncoder.encode(senhaAtualizada);
        jdbc.update(
                "UPDATE usuarios SET nome = ?, senha_hash = ?, ativo = FALSE WHERE email_normalizado = ?",
                "Lactec editado",
                hashSenhaAtualizada,
                EMAIL_LACTEC);
        jdbc.update(
                "UPDATE usuarios SET nome = ?, senha_hash = ?, ativo = FALSE WHERE email_normalizado = ?",
                "Cliente demo editado",
                passwordEncoder.encode("Outra senha demo"),
                EMAIL_CLIENTE_DEMO);
        jdbc.update(
                "UPDATE empresas SET legal_name = ?, ativo = FALSE WHERE cnpj = ?",
                "Empresa demo editada",
                CNPJ_EMPRESA_DEMO);
        jdbc.update(
                "UPDATE orcamentos SET name = ?, status = ?, external_contact_email = ? WHERE code = ?",
                "Orçamento editado",
                "Refused",
                "contato-atualizado@example.test",
                CODIGO_ORCAMENTO_DEMO);
        jdbc.update(
                "UPDATE itens_orcamento SET test_type = ?, unit_price = ?, sample_count = 1 "
                        + "WHERE orcamento_id = (SELECT id FROM orcamentos WHERE code = ?) AND position = 1",
                "Item editado",
                new BigDecimal("777.77"),
                CODIGO_ORCAMENTO_DEMO);
        byte[] pdfAtualizado = {37, 80, 68, 70, 45, 99, 117, 115, 116, 111, 109};
        jdbc.update(
                "UPDATE documentos_orcamentos SET content = ? "
                        + "WHERE orcamento_id = (SELECT id FROM orcamentos WHERE code = ?)",
                pdfAtualizado,
                CODIGO_ORCAMENTO_LACTEC);

        runner.run(new DefaultApplicationArguments(new String[0]));

        assertEquals("Lactec editado", buscarTexto("usuarios", "nome", "email_normalizado", EMAIL_LACTEC));
        assertEquals(false, buscarBoolean("usuarios", "ativo", "email_normalizado", EMAIL_LACTEC));
        assertTrue(
                passwordEncoder.matches(
                        senhaAtualizada,
                        buscarTexto("usuarios", "senha_hash", "email_normalizado", EMAIL_LACTEC)));
        assertEquals("Cliente demo editado", buscarTexto("usuarios", "nome", "email_normalizado", EMAIL_CLIENTE_DEMO));
        assertEquals(false, buscarBoolean("usuarios", "ativo", "email_normalizado", EMAIL_CLIENTE_DEMO));
        assertEquals("Empresa demo editada", buscarTexto("empresas", "legal_name", "cnpj", CNPJ_EMPRESA_DEMO));
        assertEquals(false, buscarBoolean("empresas", "ativo", "cnpj", CNPJ_EMPRESA_DEMO));
        assertEquals("Orçamento editado", buscarTexto("orcamentos", "name", "code", CODIGO_ORCAMENTO_DEMO));
        assertEquals("Refused", buscarTexto("orcamentos", "status", "code", CODIGO_ORCAMENTO_DEMO));
        assertEquals(
                "Item editado",
                jdbc.queryForObject(
                        "SELECT test_type FROM itens_orcamento "
                                + "WHERE orcamento_id = (SELECT id FROM orcamentos WHERE code = ?) AND position = 1",
                        String.class,
                        CODIGO_ORCAMENTO_DEMO));
        assertArrayEquals(
                pdfAtualizado,
                jdbc.queryForObject(
                        "SELECT content FROM documentos_orcamentos "
                                + "WHERE orcamento_id = (SELECT id FROM orcamentos WHERE code = ?)",
                        byte[].class,
                        CODIGO_ORCAMENTO_LACTEC));
        assertEquals(26, contar("orcamentos"));
        assertEquals(44, contar("itens_orcamento"));

        long quoteId =
                jdbc.queryForObject(
                        "SELECT id FROM orcamentos WHERE code = ?",
                        Long.class,
                        CODIGO_ORCAMENTO_REMOVIDO);
        long clienteId =
                jdbc.queryForObject(
                        "SELECT c.id FROM clientes c JOIN usuarios u ON u.id = c.usuario_id "
                                + "WHERE u.email_normalizado = ?",
                        Long.class,
                        EMAIL_CLIENTE_DEMO);
        long empresaId =
                jdbc.queryForObject(
                        "SELECT id FROM empresas WHERE cnpj = ?",
                        Long.class,
                        "87654321000198");
        jdbc.update(
                "DELETE FROM cliente_empresas WHERE cliente_id = ? AND empresa_id = ?",
                clienteId,
                empresaId);
        jdbc.update("DELETE FROM itens_orcamento WHERE orcamento_id = ?", quoteId);
        jdbc.update("DELETE FROM orcamentos WHERE id = ?", quoteId);

        runner.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(25, contar("orcamentos"));
        assertEquals(42, contar("itens_orcamento"));
        assertEquals(9, contar("cliente_empresas"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM orcamentos WHERE code = ?",
                        Integer.class,
                        CODIGO_ORCAMENTO_REMOVIDO));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM cliente_empresas WHERE cliente_id = ? AND empresa_id = ?",
                        Integer.class,
                        clienteId,
                        empresaId));

        jdbc.update("DELETE FROM demo_seed_runs WHERE seed_key = 'development-catalog-v2'");
        jdbc.execute(
                "ALTER TABLE demo_seed_runs ADD CONSTRAINT ck_demo_seed_runs_fail "
                        + "CHECK (seed_key <> 'development-catalog-v2')");

        assertThrows(
                RuntimeException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));

        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM orcamentos WHERE code = ?",
                        Integer.class,
                        CODIGO_ORCAMENTO_REMOVIDO));
        assertEquals(25, contar("orcamentos"));
        assertEquals(42, contar("itens_orcamento"));
        assertEquals(9, contar("cliente_empresas"));
        assertEquals(0, contar("demo_seed_runs"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM cliente_empresas WHERE cliente_id = ? AND empresa_id = ?",
                        Integer.class,
                        clienteId,
                        empresaId));

        jdbc.execute("ALTER TABLE demo_seed_runs DROP CONSTRAINT ck_demo_seed_runs_fail");
        runner.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(26, contar("orcamentos"));
        assertEquals(44, contar("itens_orcamento"));
        assertEquals(9, contar("cliente_empresas"));
        assertEquals(1, contar("demo_seed_runs"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM cliente_empresas WHERE cliente_id = ? AND empresa_id = ?",
                        Integer.class,
                        clienteId,
                        empresaId));
        assertEquals("Lactec editado", buscarTexto("usuarios", "nome", "email_normalizado", EMAIL_LACTEC));
        assertEquals(false, buscarBoolean("usuarios", "ativo", "email_normalizado", EMAIL_LACTEC));
        assertTrue(
                passwordEncoder.matches(
                        senhaAtualizada,
                        buscarTexto("usuarios", "senha_hash", "email_normalizado", EMAIL_LACTEC)));
        assertEquals("Empresa demo editada", buscarTexto("empresas", "legal_name", "cnpj", CNPJ_EMPRESA_DEMO));
        assertEquals("Orçamento editado", buscarTexto("orcamentos", "name", "code", CODIGO_ORCAMENTO_DEMO));
        assertEquals(
                "Item editado",
                jdbc.queryForObject(
                        "SELECT test_type FROM itens_orcamento "
                                + "WHERE orcamento_id = (SELECT id FROM orcamentos WHERE code = ?) AND position = 1",
                        String.class,
                        CODIGO_ORCAMENTO_DEMO));
        assertArrayEquals(
                pdfAtualizado,
                jdbc.queryForObject(
                        "SELECT content FROM documentos_orcamentos "
                                + "WHERE orcamento_id = (SELECT id FROM orcamentos WHERE code = ?)",
                        byte[].class,
                        CODIGO_ORCAMENTO_LACTEC));
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Integer.class);
    }

    private String buscarTexto(String tabela, String coluna, String chave, String valor) {
        return jdbc.queryForObject(
                "SELECT " + coluna + " FROM " + tabela + " WHERE " + chave + " = ?",
                String.class,
                valor);
    }

    private boolean buscarBoolean(String tabela, String coluna, String chave, String valor) {
        return jdbc.queryForObject(
                "SELECT " + coluna + " FROM " + tabela + " WHERE " + chave + " = ?",
                Boolean.class,
                valor);
    }
}
