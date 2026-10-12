package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "empresas",
        uniqueConstraints = @UniqueConstraint(name = "uk_empresas_cnpj", columnNames = "cnpj"))
@Getter
@Setter
@NoArgsConstructor
public class Empresa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String legalName;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 2)
    private String state;

    @Column(nullable = false, length = 150)
    private String contactName;

    @Column(nullable = false, length = 30)
    private String contactPhone;

    @Column(nullable = false, length = 254)
    private String contactEmail;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false, length = 10, updatable = false)
    private String origin = "LOCAL";
}
