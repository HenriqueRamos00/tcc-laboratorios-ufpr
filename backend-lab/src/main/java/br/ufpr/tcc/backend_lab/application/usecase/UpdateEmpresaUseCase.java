package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.UpdateEmpresaRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.EmpresaResponse;
import br.ufpr.tcc.backend_lab.application.validation.ValidadorEmpresa;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.ViolacaoUnicidade;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateEmpresaUseCase {
    private final EmpresaRepository repository;
    private final ValidadorEmpresa validadorEmpresa;

    @Transactional
    public EmpresaResponse execute(Long id, UpdateEmpresaRequest request) {
        var empresa =
                repository
                        .findById(id)
                        .orElseThrow(
                                () -> RegraNegocioException.naoEncontrado("Empresa não encontrada"));
        String cnpj = validadorEmpresa.normalizarCnpj(request.cnpj());
        validadorEmpresa.validarUf(request.state());
        if (repository.existsByCnpjAndIdNot(cnpj, empresa.getId())) {
            throw RegraNegocioException.conflito("CNPJ já cadastrado");
        }

        empresa.setCnpj(cnpj);
        empresa.setLegalName(request.legalName());
        empresa.setAddress(request.address());
        empresa.setCity(request.city());
        empresa.setState(request.state());
        empresa.setContactName(request.contactName());
        empresa.setContactPhone(request.contactPhone());
        empresa.setContactEmail(request.contactEmail());
        empresa.setAtivo(request.status().toAtivo());
        try {
            return EmpresaResponse.from(repository.saveAndFlush(empresa));
        } catch (DataIntegrityViolationException exception) {
            if (ViolacaoUnicidade.cnpj(exception)) {
                throw RegraNegocioException.conflito("CNPJ já cadastrado");
            }
            throw exception;
        }
    }
}
