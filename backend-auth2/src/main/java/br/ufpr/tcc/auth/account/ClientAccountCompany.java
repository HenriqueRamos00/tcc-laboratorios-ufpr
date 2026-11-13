package br.ufpr.tcc.auth.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Vinculo entre conta e empresa, identificado pelo par de IDs. Uma conta pode representar
 * varias empresas. A insercao ocorre por consulta no repositorio, que verifica a existencia
 * das empresas.
 */
@Entity
@Table(name = "CLIENT_ACCOUNT_COMPANY",
        indexes = @Index(name = "IX_ACCOUNT_COMPANY_COMPANY", columnList = "COMPANY_ID"))
@IdClass(ClientAccountCompany.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ClientAccountCompany {

    @Id
    @Column(name = "CLIENT_ACCOUNT_ID")
    private Long accountId;

    @Id
    @Column(name = "COMPANY_ID")
    private Long companyId;

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
