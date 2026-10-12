package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.ClienteLoginResponse;
import br.ufpr.tcc.backend_lab.application.usecase.LoginClienteUseCase;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class ClienteAuthController {
    private final LoginClienteUseCase login;

    @PostMapping("/login")
    public ClienteLoginResponse login(@Valid @RequestBody LoginRequest request) {
        return login.execute(request);
    }
}
