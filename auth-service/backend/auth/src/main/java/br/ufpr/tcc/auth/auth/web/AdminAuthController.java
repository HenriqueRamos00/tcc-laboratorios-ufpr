package br.ufpr.tcc.auth.auth.web;

import java.util.Map;

import br.ufpr.tcc.auth.auth.model.AuthUser;
import br.ufpr.tcc.auth.auth.model.Role;
import br.ufpr.tcc.auth.auth.persistence.AuthUserRepository;
import br.ufpr.tcc.auth.auth.web.dto.AdminSessionResponse;
import br.ufpr.tcc.auth.auth.web.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/auth")
class AdminAuthController {
    private final AuthenticationManager authenticationManager;
    private final AuthUserRepository users;
    private final SecurityContextRepository securityContextRepository;

    AdminAuthController(AuthenticationManager authenticationManager, AuthUserRepository users,
                        SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/login")
    AdminSessionResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest servletRequest,
                               HttpServletResponse servletResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            AuthUser.normalizeEmail(request.email()), request.password()));
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        }

        if (!(authentication.getPrincipal() instanceof UserDetails principal)
                || authentication.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
            SecurityContextHolder.clearContext();
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esta conta não possui acesso administrativo.");
        }

        HttpSession existingSession = servletRequest.getSession(false);
        if (existingSession == null) {
            servletRequest.getSession(true);
        }
        servletRequest.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        AuthUser user = users.findById(principal.getUsername()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas."));
        return new AdminSessionResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name());
    }

    @GetMapping("/session")
    AdminSessionResponse session() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDetails principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado.");
        }
        AuthUser user = users.findById(principal.getUsername()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado."));
        if (!user.isEnabled() || user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado.");
        }
        return new AdminSessionResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name());
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }
}
