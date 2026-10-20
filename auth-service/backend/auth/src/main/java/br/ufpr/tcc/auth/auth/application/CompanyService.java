package br.ufpr.tcc.auth.auth.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.ufpr.tcc.auth.auth.model.Company;
import br.ufpr.tcc.auth.auth.persistence.AuthUserRepository;
import br.ufpr.tcc.auth.auth.persistence.CompanyRepository;
import br.ufpr.tcc.auth.auth.validation.CnpjValidator;
import br.ufpr.tcc.auth.auth.web.dto.CompanyRequest;
import br.ufpr.tcc.auth.auth.web.dto.CompanyResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyService {
    private final CompanyRepository companies;
    private final AuthUserRepository users;

    public CompanyService(CompanyRepository companies, AuthUserRepository users) {
        this.companies = companies;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list(String search) {
        Specification<Company> spec = (root, query, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = likePattern(search);
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '!'),
                    cb.like(root.get("cnpj"), pattern, '!')));
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AuthUserRepository.CompanyUserCount count : users.countUsersByCompany()) {
            counts.put(count.getCompanyId(), count.getTotal());
        }
        return companies.findAll(spec, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")))
                .stream()
                .map(company -> toResponse(company,
                        counts.getOrDefault(company.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        String name = cleanName(request.name());
        String cnpj = cleanCnpj(request.cnpj());
        if (companies.existsByCnpj(cnpj)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este CNPJ já está cadastrado.");
        }
        Company company = companies.save(new Company(UUID.randomUUID().toString(), name, cnpj));
        return toResponse(company, 0);
    }

    @Transactional
    public CompanyResponse update(String id, CompanyRequest request) {
        Company company = find(id);
        String name = cleanName(request.name());
        String cnpj = cleanCnpj(request.cnpj());
        if (companies.existsByCnpjAndIdNot(cnpj, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este CNPJ já está cadastrado.");
        }
        company.setName(name);
        company.setCnpj(cnpj);
        int count = (int) users.countUsersByCompanyId(id);
        return toResponse(company, count);
    }

    @Transactional
    public void delete(String id) {
        Company company = find(id);
        if (users.countUsersByCompanyId(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Remova os vínculos dos usuários antes de excluir esta empresa.");
        }
        companies.delete(company);
    }

    private Company find(String id) {
        return companies.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));
    }

    private static String cleanName(String name) {
        String value = name == null ? "" : name.strip();
        if (value.isEmpty() || value.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe o nome da empresa, com até 200 caracteres.");
        }
        return value;
    }

    private static String cleanCnpj(String cnpj) {
        String digits = CnpjValidator.normalize(cnpj);
        if (!CnpjValidator.isValid(digits)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe um CNPJ válido, com 14 dígitos.");
        }
        return digits;
    }

    private static CompanyResponse toResponse(Company company, int userCount) {
        return new CompanyResponse(company.getId(), company.getName(), company.getCnpj(),
                userCount, company.getCreatedAt());
    }

    private static String likePattern(String value) {
        String escaped = value.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
