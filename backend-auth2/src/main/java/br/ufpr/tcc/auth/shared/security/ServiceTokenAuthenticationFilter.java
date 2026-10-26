package br.ufpr.tcc.auth.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Transforma o token apresentado em identidade, de aplicacao ou de administrador.
 *
 * O filtro nunca recusa: quando nao reconhece a credencial ele simplesmente nao publica
 * identidade nenhuma, e quem responde e o ponto de recusa da cadeia. Assim existe um lugar
 * so decidindo a forma da recusa, e token ausente, malformado, desconhecido, revogado,
 * vencido e de aplicacao desativada saem iguais sem ninguem precisar lembrar.
 *
 * O prefixo escolhe onde procurar, e nada alem disso: o tipo efetivo sai do registro
 * encontrado. Adulterar o prefixo nao promove ninguem, so faz a busca nao achar nada.
 */
@Component
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    public static final String APPLICATION_AUTHORITY = "ROLE_APPLICATION";

    public static final String ADMINISTRATOR_AUTHORITY = "ROLE_ADMINISTRATOR";

    private final ApplicationCallerDirectory applications;
    private final AdministratorCallerDirectory administrators;

    ServiceTokenAuthenticationFilter(ApplicationCallerDirectory applications,
            AdministratorCallerDirectory administrators) {
        this.applications = applications;
        this.administrators = administrators;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        presentedToken(request).ifPresent(this::publishIdentityFor);
        filterChain.doFilter(request, response);
    }

    private void publishIdentityFor(String presentedToken) {
        Optional<AdministratorCaller> administrator = administrators.resolve(presentedToken);
        if (administrator.isPresent()) {
            publish(administrator.get(), ADMINISTRATOR_AUTHORITY);
            return;
        }
        applications.resolve(presentedToken)
                .ifPresent(caller -> publish(caller, APPLICATION_AUTHORITY));
    }

    private static Optional<String> presentedToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()));
    }

    /**
     * Contexto novo, em vez de mutar o que o detentor devolve.
     *
     * O filtro de contexto da cadeia entrega um contexto preguicoso, e mutar aquele objeto
     * obriga a resolucao antecipada do repositorio e, com estrategia herdavel, passa a mesma
     * identidade para thread filha. A referencia do Spring Security manda criar vazio e
     * publicar, e nao chamar setAuthentication no contexto corrente.
     */
    private static void publish(Object principal, String authority) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,
                null, List.of(new SimpleGrantedAuthority(authority))));
        SecurityContextHolder.setContext(context);
    }
}
