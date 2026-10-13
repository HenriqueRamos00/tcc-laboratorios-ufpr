package br.ufpr.tcc.backend_lab.application.dto.response;

import java.util.List;

public record ClienteLoginResponse(
        String accessToken, String tokenType, long expiresIn, User user) {
    public record Company(String id, String name) {}

    public record User(String id, String name, String email, String role, List<Company> companies) {}
}
