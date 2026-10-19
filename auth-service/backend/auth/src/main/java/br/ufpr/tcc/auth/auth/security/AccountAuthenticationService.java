package br.ufpr.tcc.auth.auth.security;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import br.ufpr.tcc.auth.auth.model.Account;
import br.ufpr.tcc.auth.auth.persistence.AccountRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
class AccountAuthenticationService implements UserDetailsService {
    private final AccountRepository accounts;

    AccountAuthenticationService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        Account account = accounts.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        // The persisted principal uses our stable ID, never an editable email or external ID.
        return User.withUsername(account.id()).password(account.passwordHash())
                .authorities("PORTAL_USER").disabled(!account.enabled())
                .accountLocked(account.lockedUntil() != null
                        && account.lockedUntil().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                .build();
    }

    @EventListener
    void failedLogin(AuthenticationFailureBadCredentialsEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) return;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        accounts.recordFailedLogin(event.getAuthentication().getName(), now, now.plusMinutes(15));
    }

    @EventListener
    void successfulLogin(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) return;
        accounts.resetFailedLogins(event.getAuthentication().getName());
    }
}
