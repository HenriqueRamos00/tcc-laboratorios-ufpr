package br.ufpr.tcc.backend_lab.infrastructure.security;

import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private final SecretKey signingKey;
	private final long expirationSeconds;

	public JwtService(
			@Value("${security.jwt.secret}") String secret,
			@Value("${security.jwt.expiration-seconds}") long expirationSeconds
	) {
		if (secret.length() < 32) {
			throw new IllegalArgumentException("JWT secret deve possuir pelo menos 32 caracteres");
		}
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationSeconds = expirationSeconds;
	}

	public String generateToken(Usuario usuario) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);

		return Jwts.builder()
				.subject(usuario.getEmail())
				.claim("role", usuario.getPerfil().name())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey)
				.compact();
	}

	public Jws<Claims> parseToken(String token) throws JwtException {
		return Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token);
	}

	public long getExpirationSeconds() {
		return expirationSeconds;
	}
}
