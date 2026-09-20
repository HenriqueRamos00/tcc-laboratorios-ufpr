package br.ufpr.tcc.backend_lab.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
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
				String role = claims.get("role", String.class);
				var authorities = role == null
						? java.util.List.<SimpleGrantedAuthority>of()
						: java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role));
				var authentication = new UsernamePasswordAuthenticationToken(
						claims.getSubject(), null, authorities
				);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (JwtException | IllegalArgumentException ignored) {
				SecurityContextHolder.clearContext();
			}
		}

		filterChain.doFilter(request, response);
	}
}
