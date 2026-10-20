package br.ufpr.tcc.auth.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.ufpr.tcc.auth.auth.model.ApiKey;
import br.ufpr.tcc.auth.auth.persistence.ApiKeyRepository;
import br.ufpr.tcc.auth.auth.web.dto.ApiKeyCreateRequest;
import br.ufpr.tcc.auth.auth.web.dto.ApiKeyCreateResponse;
import br.ufpr.tcc.auth.auth.web.dto.ApiKeyResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApiKeyService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "auth_";
    private static final int KEY_BYTES = 32;

    private final ApiKeyRepository apiKeys;

    public ApiKeyService(ApiKeyRepository apiKeys) {
        this.apiKeys = apiKeys;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list() {
        return apiKeys.findAllByOrderByNameAscCreatedAtAsc().stream()
                .map(ApiKeyService::toResponse).toList();
    }

    @Transactional
    public ApiKeyCreateResponse create(ApiKeyCreateRequest request) {
        String name = request.name() == null ? "" : request.name().strip();
        if (name.isEmpty() || name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe o nome da integração, com até 100 caracteres.");
        }
        LocalDateTime expiresAt = request.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A data de expiração deve estar no futuro.");
        }

        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        String rawKey = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String prefix = rawKey.substring(0, Math.min(12, rawKey.length()));
        ApiKey apiKey = apiKeys.save(new ApiKey(UUID.randomUUID().toString(), name, prefix,
                hash(rawKey), expiresAt));
        return new ApiKeyCreateResponse(rawKey, toResponse(apiKey));
    }

    @Transactional
    public ApiKeyResponse setEnabled(String id, boolean enabled) {
        ApiKey apiKey = find(id);
        apiKey.setEnabled(enabled);
        return toResponse(apiKey);
    }

    @Transactional
    public void delete(String id) {
        apiKeys.delete(find(id));
    }

    @Transactional
    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();
        Optional<ApiKey> found = apiKeys.findByKeyHash(hash(rawKey.strip()));
        if (found.isEmpty()) return Optional.empty();

        ApiKey apiKey = found.get();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!apiKey.isEnabled() || apiKey.isExpired(now)) return Optional.empty();

        if (apiKey.getLastUsedAt() == null
                || apiKey.getLastUsedAt().isBefore(now.minusMinutes(5))) {
            apiKey.setLastUsedAt(now);
        }
        return Optional.of(apiKey);
    }

    private ApiKey find(String id) {
        return apiKeys.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Chave de API não encontrada."));
    }

    private static ApiKeyResponse toResponse(ApiKey apiKey) {
        return new ApiKeyResponse(apiKey.getId(), apiKey.getName(), apiKey.getKeyPrefix(),
                apiKey.isEnabled(), apiKey.getCreatedAt(), apiKey.getExpiresAt(),
                apiKey.getLastUsedAt());
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
