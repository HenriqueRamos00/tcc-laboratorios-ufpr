package br.ufpr.tcc.auth.auth.security;

import java.io.IOException;

import br.ufpr.tcc.auth.auth.model.AuthUser;
import br.ufpr.tcc.auth.auth.model.Role;
import br.ufpr.tcc.auth.auth.persistence.AuthUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Revalidates the administrator on every admin request so disabling, deleting,
 * or demoting an account ends its session immediately.
 */
public class AdminSessionValidationFilter extends OncePerRequestFilter {
    private final AuthUserRepository users;

    public AdminSessionValidationFilter(AuthUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            AuthUser user = users.findById(authentication.getName()).orElse(null);
            if (user == null || !user.isEnabled() || user.getRole() != Role.ADMIN) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
            }
        }
        filterChain.doFilter(request, response);
    }
}
