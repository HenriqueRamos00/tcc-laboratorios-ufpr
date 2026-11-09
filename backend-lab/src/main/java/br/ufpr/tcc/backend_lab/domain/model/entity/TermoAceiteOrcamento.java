package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Dados do Termo de Aceite (Anexo II da proposta) preenchido pelo cliente no portal.
 * Só existe quando o aceite foi feito pelo termo; os campos são os do formulário.
 */
@Entity
@Table(name = "termos_aceite_orcamentos")
@Getter
@Setter
@NoArgsConstructor
public class TermoAceiteOrcamento {
    @Id private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "aceite_id")
    private AceiteOrcamento aceite;

    // Dados de cobrança, usados na emissão da nota fiscal.
    @Column(nullable = false, length = 200)
    private String company;

    @Column(nullable = false, length = 18)
    private String cnpj;

    @Column(length = 30)
    private String stateRegistration;

    @Column(nullable = false, length = 255)
    private String street;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 60)
    private String state;

    @Column(length = 120)
    private String district;

    @Column(nullable = false, length = 20)
    private String postalCode;

    @Column(length = 150)
    private String contact;

    @Column(length = 30)
    private String phone;

    @Column(length = 30)
    private String fax;

    @Column(length = 500)
    private String invoiceEmails;

    // Dados de quem aprovou.
    @Column(nullable = false, length = 150)
    private String requesterName;

    @Column(nullable = false, length = 150)
    private String responsibleName;

    @Column(nullable = false, length = 120)
    private String jobTitle;

    @Column(length = 120)
    private String department;

    @Column(nullable = false)
    private LocalDate approvalDate;

    @Column(nullable = false)
    private boolean allowsResultsByEmail;

    @Column(length = 254)
    private String resultsEmail;
}
