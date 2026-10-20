package br.ufpr.tcc.auth.auth.web;

import java.util.List;

import br.ufpr.tcc.auth.auth.application.CompanyService;
import br.ufpr.tcc.auth.auth.web.dto.CompanyRequest;
import br.ufpr.tcc.auth.auth.web.dto.CompanyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/companies")
class AdminCompanyController {
    private final CompanyService companies;

    AdminCompanyController(CompanyService companies) {
        this.companies = companies;
    }

    @GetMapping
    List<CompanyResponse> list(@RequestParam(required = false) String search) {
        return companies.list(search);
    }

    @PostMapping
    ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companies.create(request));
    }

    @PutMapping("/{id}")
    CompanyResponse update(@PathVariable String id, @Valid @RequestBody CompanyRequest request) {
        return companies.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        companies.delete(id);
    }
}
