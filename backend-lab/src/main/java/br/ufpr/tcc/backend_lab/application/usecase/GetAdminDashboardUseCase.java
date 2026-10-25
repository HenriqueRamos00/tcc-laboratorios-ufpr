package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.AdminDashboardResponse;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetAdminDashboardUseCase {
    private final OrcamentoRepository orcamentos;
    private final ClienteRepository clientes;

    @Transactional(readOnly = true)
    public AdminDashboardResponse execute() {
        BigDecimal totalQuotedValue =
                Optional.ofNullable(orcamentos.sumTotalPrice()).orElse(BigDecimal.ZERO);

        return new AdminDashboardResponse(
                orcamentos.count(),
                orcamentos.countByStatus(StatusComercialOrcamento.PENDING_ACCEPTANCE),
                clientes.countByUsuarioPerfilAndUsuarioAtivoTrue(PerfilUsuario.CLIENTE),
                totalQuotedValue);
    }
}
