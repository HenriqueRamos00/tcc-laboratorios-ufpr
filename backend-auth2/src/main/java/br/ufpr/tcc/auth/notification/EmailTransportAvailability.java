package br.ufpr.tcc.auth.notification;

/** Informa se existe transporte SMTP real para uma acao que depende de e-mail. */
public interface EmailTransportAvailability {
    boolean isAvailable();

    boolean isEmailChangeAvailable();
}
