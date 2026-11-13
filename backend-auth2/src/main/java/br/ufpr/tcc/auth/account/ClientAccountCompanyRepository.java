package br.ufpr.tcc.auth.account;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Leitura e gravacao dos vinculos entre conta de cliente e empresa. */
interface ClientAccountCompanyRepository
        extends Repository<ClientAccountCompany, ClientAccountCompany.Key> {

    /**
     * Insere vinculos somente para empresas existentes e devolve a quantidade criada. O chamador
     * compara com a quantidade solicitada.
     */
    @Modifying
    @Query("""
            insert into ClientAccountCompany (accountId, companyId)
            select cast(:accountId as Long), company.id
              from Company company
             where company.id in :companyIds
            """)
    int link(@Param("accountId") long accountId,
            @Param("companyIds") Collection<Long> companyIds);

    @Modifying
    @Query("delete from ClientAccountCompany link where link.accountId = :accountId")
    void unlinkAllOf(@Param("accountId") long accountId);

    @Query("""
            select new br.ufpr.tcc.auth.account.CompanyLinkRow(
                   link.accountId, link.companyId, company.cnpj)
              from ClientAccountCompany link, Company company
             where company.id = link.companyId
               and link.accountId = :accountId
            """)
    List<CompanyLinkRow> findOfAccount(@Param("accountId") long accountId);

    /** Busca todos os vinculos de uma aplicacao em uma consulta, filtrando pela conta. */
    @Query("""
            select new br.ufpr.tcc.auth.account.CompanyLinkRow(
                   link.accountId, link.companyId, company.cnpj)
              from ClientAccountCompany link, Company company, ClientAccount account
             where company.id = link.companyId
               and account.id = link.accountId
               and account.applicationId = :applicationId
            """)
    List<CompanyLinkRow> findWithinApplication(@Param("applicationId") long applicationId);

    @Query("""
            select new br.ufpr.tcc.auth.account.CompanyLinkRow(
                   link.accountId, link.companyId, company.cnpj)
              from ClientAccountCompany link, Company company
             where company.id = link.companyId
            """)
    List<CompanyLinkRow> findAcrossApplications();
}
