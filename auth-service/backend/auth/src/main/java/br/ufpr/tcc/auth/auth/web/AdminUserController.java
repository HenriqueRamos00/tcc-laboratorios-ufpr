package br.ufpr.tcc.auth.auth.web;

import java.util.List;

import br.ufpr.tcc.auth.auth.application.UserService;
import br.ufpr.tcc.auth.auth.web.dto.UserRequest;
import br.ufpr.tcc.auth.auth.web.dto.UserResponse;
import br.ufpr.tcc.auth.auth.web.dto.UserStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
class AdminUserController {
    private final UserService users;

    AdminUserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    List<UserResponse> list(@RequestParam(required = false) String search,
                            @RequestParam(required = false) String role,
                            @RequestParam(required = false) Boolean enabled) {
        return users.list(search, role, enabled);
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(users.create(request));
    }

    @PutMapping("/{id}")
    UserResponse update(@PathVariable String id, @Valid @RequestBody UserRequest request,
                        Authentication authentication) {
        return users.update(id, request, currentAdminId(authentication));
    }

    @PatchMapping("/{id}/status")
    UserResponse status(@PathVariable String id, @Valid @RequestBody UserStatusRequest request,
                        Authentication authentication) {
        return users.setEnabled(id, request.enabled(), currentAdminId(authentication));
    }

    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id, Authentication authentication) {
        users.delete(id, currentAdminId(authentication));
    }

    private static String currentAdminId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Não autenticado.");
        }
        return authentication.getName();
    }
}
