package br.ufpr.tcc.backend_lab.infrastructure.security;

import java.sql.SQLException;

public final class EmailUniqueConstraint {
	private EmailUniqueConstraint() {
	}

	public static boolean isViolation(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sqlException
					&& "23505".equals(sqlException.getSQLState())) {
				String details = String.valueOf(sqlException.getMessage()).toLowerCase();
				if (details.contains("email")) return true;
			}
			String details = String.valueOf(cause.getMessage()).toLowerCase();
			if (details.contains("uk_usuarios_email_normalizado")
					|| details.contains("usuarios_email_key")) return true;
		}
		return false;
	}
}
