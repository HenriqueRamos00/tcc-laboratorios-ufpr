package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.response.TechnicianDashboardResponse;
import br.ufpr.tcc.backend_lab.application.usecase.GetTechnicianDashboardUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/technician/dashboard")
@RequiredArgsConstructor
public class TechnicianDashboardController {
    private final GetTechnicianDashboardUseCase getTechnicianDashboard;

    @GetMapping
    public TechnicianDashboardResponse obterDashboard() {
        return getTechnicianDashboard.execute();
    }
}
