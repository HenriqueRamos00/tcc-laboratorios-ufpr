package br.ufpr.tcc.auth.auth.web;

import java.util.List;

import br.ufpr.tcc.auth.auth.application.ApiKeyService;
import br.ufpr.tcc.auth.auth.web.dto.ApiKeyCreateRequest;
import br.ufpr.tcc.auth.auth.web.dto.ApiKeyCreateResponse;
import br.ufpr.tcc.auth.auth.web.dto.ApiKeyResponse;
import br.ufpr.tcc.auth.auth.web.dto.ApiKeyStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/api-keys")
class AdminApiKeyController {
    private final ApiKeyService apiKeys;

    AdminApiKeyController(ApiKeyService apiKeys) {
        this.apiKeys = apiKeys;
    }

    @GetMapping
    List<ApiKeyResponse> list() {
        return apiKeys.list();
    }

    @PostMapping
    ResponseEntity<ApiKeyCreateResponse> create(@Valid @RequestBody ApiKeyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeys.create(request));
    }

    @PatchMapping("/{id}/status")
    ApiKeyResponse status(@PathVariable String id, @Valid @RequestBody ApiKeyStatusRequest request) {
        return apiKeys.setEnabled(id, request.enabled());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        apiKeys.delete(id);
    }
}
