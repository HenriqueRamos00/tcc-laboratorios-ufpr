package br.ufpr.tcc.backend_lab.infrastructure.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Preenche a chave normalizada em contas anteriores à gestão de técnicos. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UsuarioEmailNormalizadoConfig implements ApplicationRunner {
	private final JdbcTemplate jdbcTemplate;

	public UsuarioEmailNormalizadoConfig(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		jdbcTemplate.update("UPDATE usuarios SET email_normalizado = LOWER(TRIM(email)) WHERE email_normalizado IS NULL");
	}
}
