package br.ufpr.tcc.backend_auth.domain.model.entity;

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
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "servicos", uniqueConstraints = @UniqueConstraint(name = "uk_servicos_codigo", columnNames = "codigo"))
@Getter
@Setter
@NoArgsConstructor
public class Servico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 80)
    private String codigo;

    @Column(nullable = false)
    private boolean ativo = true;
}
