package com.adaptcode.utp.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Token de refresco emitido tras un inicio de sesión. Solo se guarda su hash
 * SHA-256, nunca el token en claro: si la base se filtra, los atacantes no
 * pueden reutilizarlo. Cada token es de un solo uso (rotación): al refrescar se
 * marca como revocado y se emite uno nuevo. Reutilizar uno revocado revoca toda
 * la cadena del usuario, señal de robo.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tokens_refresco", indexes = {
		@Index(name = "idx_tokens_refresco_usuario", columnList = "usuario_id"),
		@Index(name = "idx_tokens_refresco_expira", columnList = "expira_en")
})
public class TokenRefresco {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@NotBlank
	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@NotNull
	@Column(name = "expira_en", nullable = false)
	private Instant expiraEn;

	@Column(name = "revocado", nullable = false)
	private boolean revocado;

	@Column(name = "revocado_en")
	private Instant revocadoEn;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	public TokenRefresco(Usuario usuario, String tokenHash, Instant expiraEn) {
		this.usuario = usuario;
		this.tokenHash = tokenHash;
		this.expiraEn = expiraEn;
		this.revocado = false;
	}

	@PrePersist
	private void fijarFecha() {
		if (creadoEn == null) {
			creadoEn = Instant.now();
		}
	}
}
