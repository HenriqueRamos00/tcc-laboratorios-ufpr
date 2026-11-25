package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.Locale;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "usuarios",
        indexes =
                @Index(
                        name = "uk_usuarios_email_normalizado",
                        columnList = "email_normalizado",
                        unique = true),
        uniqueConstraints =
                @UniqueConstraint(name = "uk_usuarios_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String nome;

	@Column(nullable = false, length = 254)
	private String email;

	// Índice único adicional para garantir unicidade sem diferenciar caixa.
	@Column(name = "email_normalizado", length = 254)
	private String emailNormalizado;

	@Column(name = "senha_hash", nullable = false)
	private String senhaHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PerfilUsuario perfil;

	@Column(nullable = false)
	private boolean ativo = true;

	@Column(name = "credential_version", nullable = false, columnDefinition = "bigint default 0")
	private long credentialVersion;

	@Column(nullable = false, columnDefinition = "integer default 0")
	private int falhasSenhaAtual;

	private OffsetDateTime limiteSenhaAtualAte;

	@Column(length = 120)
	private String unidade;

	@Column(length = 160)
	private String especialidade;

	public void invalidateCredentials() {
		credentialVersion++;
	}

	@PrePersist
	@PreUpdate
	private void atualizarEmailNormalizado() {
		if (email != null) {
			emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
		}
	}
}
