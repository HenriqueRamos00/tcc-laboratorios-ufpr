package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.response.AdminDashboardResponse;
import br.ufpr.tcc.backend_lab.application.usecase.GetAdminDashboardUseCase;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final GetAdminDashboardUseCase getAdminDashboard;

    @GetMapping
    public AdminDashboardResponse obterIndicadores() {
        return getAdminDashboard.execute();
    }
}
