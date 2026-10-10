package br.ufpr.tcc.backend_lab.infrastructure.security;

import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import org.springframework.web.filter.OncePerRequestFilter;

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
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (authorization != null && authorization.startsWith("Bearer ")) {
			String token = authorization.substring(7);
			try {
				Claims claims = jwtService.parseToken(token).getPayload();
				Object userIdClaim = claims.get("userId");
				String subject = claims.getSubject();
				String roleClaim = claims.get("role", String.class);
				var issuedAt = claims.getIssuedAt();

				boolean hasSubject = subject != null && !subject.isBlank();
				boolean hasExpiration = claims.getExpiration() != null;
				boolean hasInternalRole = "ADMIN".equals(roleClaim) || "TECNICO".equals(roleClaim);

				// Tolera pequenas diferenças entre os relógios ao conferir a data de emissão.
				Instant latestAllowedIssuedAt = Instant.now().plusSeconds(jwtService.getClockSkewSeconds());
				boolean hasValidIssuedAt = issuedAt != null
						&& !issuedAt.toInstant().isAfter(latestAllowedIssuedAt);

				boolean validClaims = hasSubject && hasExpiration && hasInternalRole && hasValidIssuedAt;
				if (validClaims && userIdClaim instanceof Number userIdNumber && userIdNumber.longValue() > 0) {
					Usuario usuario = usuarioRepository.findById(userIdNumber.longValue())
							.filter(Usuario::isAtivo)
							.orElse(null);
					if (usuario != null) {
						var authorities = java.util.List.of(
								new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name())
						);
						var authentication = new UsernamePasswordAuthenticationToken(
								usuario.getEmail(), null, authorities
						);
						SecurityContextHolder.getContext().setAuthentication(authentication);
					} else {
						SecurityContextHolder.clearContext();
					}
				} else {
					// Tokens anteriores ao contrato JWT interno exigem novo login.
					SecurityContextHolder.clearContext();
				}
			} catch (JwtException | IllegalArgumentException ignored) {
				SecurityContextHolder.clearContext();
			}
		}

		filterChain.doFilter(request, response);
	}
}
