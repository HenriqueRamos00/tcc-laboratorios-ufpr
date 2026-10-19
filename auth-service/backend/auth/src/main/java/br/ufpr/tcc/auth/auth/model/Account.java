package br.ufpr.tcc.auth.auth.model;

import java.time.LocalDateTime;
import java.util.Locale;

public record Account(String id, String username, String email, String passwordHash,
                      String service, String serviceId, boolean enabled, LocalDateTime lockedUntil) {
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
