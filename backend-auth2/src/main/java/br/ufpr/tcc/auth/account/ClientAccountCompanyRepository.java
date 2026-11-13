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
     * Cria os vinculos a partir da tabela de empresa, e devolve quantos criou.
     *
     * A insercao sai de uma selecao sobre a empresa, e nao de valores soltos, para que a
     * existencia da empresa seja conferida no mesmo comando: identificador que nao existe
     * simplesmente nao gera linha, e quem chama compara a contagem com o que pediu. Conferir
     * antes, numa consulta separada, abriria janela entre conferir e gravar.
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

    /**
     * Os vinculos de todas as contas de uma aplicacao, numa viagem so.
     *
     * A aplicacao entra pela conta, que e quem a tem: empresa nao pertence a aplicacao
     * nenhuma, entao filtrar por ela nao restringiria nada.
     */
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
