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
	private final String issuer;
	private final String audience;
	private final long clockSkewSeconds;

	public JwtService(
			@Value("${security.jwt.secret}") String secret,
			@Value("${security.jwt.expiration-seconds}") long expirationSeconds,
			// Identifica quem emite o token interno.
			@Value("${security.jwt.issuer}") String issuer,
			// Identifica o destino previsto do token interno.
			@Value("${security.jwt.audience}") String audience,
			// Tolerância entre relógios dos serviços, em segundos.
			@Value("${security.jwt.clock-skew-seconds}") long clockSkewSeconds
	) {
		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length < 32) {
			throw new IllegalArgumentException("JWT secret deve possuir pelo menos 32 bytes UTF-8");
		}
		if (expirationSeconds < 1) {
			throw new IllegalArgumentException("JWT expiration deve ser positiva");
		}
		if (issuer.isBlank() || audience.isBlank()) {
			throw new IllegalArgumentException("JWT issuer e audience são obrigatórios");
		}
		if (clockSkewSeconds < 0 || clockSkewSeconds > 60) {
			throw new IllegalArgumentException("JWT clock skew deve estar entre 0 e 60 segundos");
		}
		this.signingKey = Keys.hmacShaKeyFor(secretBytes);
		this.expirationSeconds = expirationSeconds;
		this.issuer = issuer;
		this.audience = audience;
		this.clockSkewSeconds = clockSkewSeconds;
	}

	public String generateToken(Usuario usuario) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);

		return Jwts.builder()
				.subject(usuario.getEmail())
				.claim("userId", usuario.getId())
				.claim("role", usuario.getPerfil().name())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(expiresAt))
				.issuer(issuer)
				.audience().add(audience).and()
				.signWith(signingKey, Jwts.SIG.HS256)
				.compact();
	}

	public Jws<Claims> parseToken(String token) throws JwtException {
		Jws<Claims> parsed = Jwts.parser()
				.verifyWith(signingKey)
				.requireIssuer(issuer)
				.requireAudience(audience)
				.clockSkewSeconds(clockSkewSeconds)
				.build()
				.parseSignedClaims(token);
		if (!Jwts.SIG.HS256.getId().equals(parsed.getHeader().getAlgorithm())) {
			throw new io.jsonwebtoken.MalformedJwtException("Algoritmo JWT interno não permitido");
		}
		return parsed;
	}

	public long getExpirationSeconds() {
		return expirationSeconds;
	}

	public long getClockSkewSeconds() {
		return clockSkewSeconds;
	}
}
