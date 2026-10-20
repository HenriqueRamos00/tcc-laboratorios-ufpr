package br.ufpr.tcc.auth.auth.persistence;

import java.util.List;
import java.util.Optional;

import br.ufpr.tcc.auth.auth.model.AuthUser;
import br.ufpr.tcc.auth.auth.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthUserRepository
        extends JpaRepository<AuthUser, String>, JpaSpecificationExecutor<AuthUser> {

    Optional<AuthUser> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, String id);

    boolean existsByRole(Role role);

    long countByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    @Query("select u from AuthUser u left join fetch u.companies where u.id = :id")
    Optional<AuthUser> findWithCompaniesById(@Param("id") String id);

    @Query("select count(u) from AuthUser u join u.companies c where c.id = :companyId")
    long countUsersByCompanyId(@Param("companyId") String companyId);

    @Query("""
            select c.id as companyId, count(u) as total
            from AuthUser u join u.companies c
            group by c.id
            """)
    List<CompanyUserCount> countUsersByCompany();

    interface CompanyUserCount {
        String getCompanyId();

        long getTotal();
    }
}
