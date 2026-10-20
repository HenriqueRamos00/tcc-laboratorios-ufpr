package br.ufpr.tcc.auth.auth.security;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import br.ufpr.tcc.auth.auth.model.AuthUser;
import br.ufpr.tcc.auth.auth.persistence.AuthUserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountAuthenticationService implements UserDetailsService {
    private final AuthUserRepository users;

    AccountAuthenticationService(AuthUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AuthUser user = users.findByEmail(AuthUser.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        // The persisted principal uses our stable ID, never an editable email or external ID.
        return User.withUsername(user.getId()).password(user.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .disabled(!user.isEnabled())
                .accountLocked(user.getLockedUntil() != null
                        && user.getLockedUntil().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                .build();
    }

    @EventListener
    @Transactional
    public void failedLogin(AuthenticationFailureBadCredentialsEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) return;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        users.findByEmail(event.getAuthentication().getName()).ifPresent(user -> {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) return;
            int attempts = user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)
                    ? 1 : user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= 5) {
                user.setLockedUntil(now.plusMinutes(15));
            }
        });
    }

    @EventListener
    @Transactional
    public void successfulLogin(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) return;
        users.findById(event.getAuthentication().getName()).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
        });
    }
}
