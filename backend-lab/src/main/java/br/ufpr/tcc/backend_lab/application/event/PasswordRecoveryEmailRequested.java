package br.ufpr.tcc.backend_lab.application.event;

public record PasswordRecoveryEmailRequested(String recipient, String recoveryLink) {
}
