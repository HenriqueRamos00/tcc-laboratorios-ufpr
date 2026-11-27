package br.ufpr.tcc.auth.notification;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ConfiguredEmailTransport implements EmailTransportAvailability {

    private final Optional<MessageTransport> transport;
    private final String emailChangeLink;

    ConfiguredEmailTransport(Optional<MessageTransport> transport,
            @Value("${auth.notification.email-change-link:}") String emailChangeLink) {
        this.transport = transport;
        this.emailChangeLink = emailChangeLink == null ? "" : emailChangeLink;
    }

    @Override
    public boolean isAvailable() {
        return transport.isPresent();
    }

    @Override
    public boolean isEmailChangeAvailable() {
        return isAvailable() && emailChangeLink.contains("{token}");
    }
}
