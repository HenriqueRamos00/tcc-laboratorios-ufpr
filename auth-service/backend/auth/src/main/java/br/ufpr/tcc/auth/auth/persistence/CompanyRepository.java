package br.ufpr.tcc.auth.auth.persistence;

import java.util.Collection;
import java.util.List;

import br.ufpr.tcc.auth.auth.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CompanyRepository extends JpaRepository<Company, String>,
        JpaSpecificationExecutor<Company> {

    boolean existsByCnpj(String cnpj);

    boolean existsByCnpjAndIdNot(String cnpj, String id);

    List<Company> findAllByIdIn(Collection<String> ids);
}
