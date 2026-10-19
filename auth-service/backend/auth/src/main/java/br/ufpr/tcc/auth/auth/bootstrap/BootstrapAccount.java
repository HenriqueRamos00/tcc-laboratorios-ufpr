package br.ufpr.tcc.auth.auth.bootstrap;

import java.util.UUID;

import br.ufpr.tcc.auth.auth.model.Account;
import br.ufpr.tcc.auth.auth.persistence.AccountRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class BootstrapAccount implements ApplicationRunner {
    private final Environment environment;
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;

    public BootstrapAccount(Environment environment, AccountRepository accounts, PasswordEncoder encoder) {
        this.environment = environment;
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = Account.normalizeEmail(environment.getProperty("auth.bootstrap.email", ""));
        if (email.isEmpty() || accounts.findByEmail(email).isPresent()) return;
        Assert.isTrue(email.length() <= 254 && email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),
                "AUTH_BOOTSTRAP_EMAIL must be a valid email");
        String username = required("username", 100);
        String service = required("service", 50);
        String serviceId = required("service-id", 200);
        String password = required("password", 1024);
        Assert.isTrue(password.length() >= 15, "Bootstrap password must contain at least 15 characters");
        accounts.insert(UUID.randomUUID().toString(), username, email, encoder.encode(password), service, serviceId);
    }

    private String required(String field, int maxLength) {
        String value = environment.getProperty("auth.bootstrap." + field, "");
        Assert.isTrue(!value.isBlank() && value.length() <= maxLength, "Invalid auth.bootstrap." + field);
        return value;
    }
}
