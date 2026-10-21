package br.ufpr.tcc.backend_auth.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "usuarios", uniqueConstraints =
        @UniqueConstraint(name = "uk_usuarios_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(nullable = false, length = 150)
    private String nome;

    @Nationalized
    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 60)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PerfilUsuario perfil;

    @Column(name = "administrador_servico", nullable = false, columnDefinition = "bit default 0")
    private boolean administradorServico;

    @Column(nullable = false)
    private boolean ativo = true;

    @PrePersist
    @PreUpdate
    private void normalizarEmail() {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
