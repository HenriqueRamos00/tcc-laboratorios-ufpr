package br.ufpr.tcc.backend_lab.infrastructure.security;

import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                boolean clientRoute =
                        request.getRequestURI()
                                .substring(request.getContextPath().length())
                                .startsWith("/api/client/");
                Claims claims = jwtService.parseToken(token).getPayload();
                Object userIdClaim = claims.get("userId");
                String subject = claims.getSubject();
                String roleClaim = claims.get("role", String.class);
                var issuedAt = claims.getIssuedAt();

                boolean hasSubject = subject != null && !subject.isBlank();
                boolean hasExpiration = claims.getExpiration() != null;
                boolean hasExpectedRole =
                        clientRoute
                                ? "CLIENTE".equals(roleClaim)
                                : "ADMIN".equals(roleClaim) || "TECNICO".equals(roleClaim);

                boolean hasValidIssuedAt =
                        issuedAt != null && !issuedAt.toInstant().isAfter(Instant.now());

                boolean validClaims =
                        hasSubject && hasExpiration && hasExpectedRole && hasValidIssuedAt;
                if (validClaims
                        && userIdClaim instanceof Number userIdNumber
                        && userIdNumber.longValue() > 0) {
                    Usuario usuario =
                            usuarioRepository
                                    .findById(userIdNumber.longValue())
                                    .filter(Usuario::isAtivo)
                                    .orElse(null);
                    if (usuario != null
                            && (clientRoute
                                    ? usuario.getPerfil().name().equals("CLIENTE")
                                    : !usuario.getPerfil().name().equals("CLIENTE"))) {
                        var authorities =
                                List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_" + usuario.getPerfil().name()));
                        var authentication =
                                new UsernamePasswordAuthenticationToken(
                                        usuario.getEmail(), null, authorities);
                        authentication.setDetails(usuario.getId());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        SecurityContextHolder.clearContext();
                    }
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
