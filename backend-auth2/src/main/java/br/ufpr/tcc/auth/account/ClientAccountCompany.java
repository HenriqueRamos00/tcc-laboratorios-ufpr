package br.ufpr.tcc.auth.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/**
 * Vinculo entre uma conta de cliente e uma empresa que ela representa.
 *
 * Uma conta representa quantas empresas o administrador informar, e a mesma empresa pode
 * ser representada por contas de aplicacoes diferentes. As duas pontas viajam como
 * identificador e nao como associacao, como no resto do modulo: quem precisa do CNPJ faz a
 * juncao na consulta.
 *
 * A entidade existe para as consultas, e nunca e instanciada pelo codigo: a gravacao e um
 * comando de insercao a partir da tabela de empresa, que cria o vinculo e confere que a
 * empresa existe na mesma ida ao banco.
 */
@Entity
@Table(name = "CLIENT_ACCOUNT_COMPANY",
        indexes = @Index(name = "IX_ACCOUNT_COMPANY_COMPANY", columnList = "COMPANY_ID"))
@IdClass(ClientAccountCompany.Key.class)
class ClientAccountCompany {

    @Id
    @Column(name = "CLIENT_ACCOUNT_ID")
    private Long accountId;

    @Id
    @Column(name = "COMPANY_ID")
    private Long companyId;

    protected ClientAccountCompany() {
    }

    /** Chave composta, na forma que a especificacao de persistencia exige. */
    static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long accountId;
        private Long companyId;

        protected Key() {
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(accountId, key.accountId)
                    && Objects.equals(companyId, key.companyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, companyId);
        }
    }
}
