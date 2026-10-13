package br.ufpr.tcc.backend_lab.infrastructure.security;

import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-seconds}") long expirationSeconds) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret deve possuir pelo menos 32 bytes UTF-8");
        }
        if (expirationSeconds < 1) {
            throw new IllegalArgumentException("JWT expiration deve ser positiva");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirationSeconds = expirationSeconds;
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
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Jws<Claims> parseToken(String token) throws JwtException {
        Jws<Claims> parsed =
                Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
        if (!Jwts.SIG.HS256.getId().equals(parsed.getHeader().getAlgorithm())) {
            throw new MalformedJwtException("Algoritmo JWT não permitido");
        }
        return parsed;
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
