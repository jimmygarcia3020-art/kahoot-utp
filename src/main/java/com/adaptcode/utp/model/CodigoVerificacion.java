package com.adaptcode.utp.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "codigos_verificacion", indexes = {
		@Index(name = "idx_codigos_correo", columnList = "correo")
})
public class CodigoVerificacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@NotBlank(message = "El correo es obligatorio")
	@Email(message = "El formato del correo no es válido")
	@Size(max = 254, message = "El correo no debe superar los 254 caracteres")
	@Column(name = "correo", nullable = false, length = 254)
	private String correo;

	// HMAC-SHA256 en hexadecimal (64 caracteres). Las filas antiguas guardaban
	// seis dígitos en claro; se reconocen como legacy y se migran al verificar.
	@NotBlank(message = "El código es obligatorio")
	@Column(name = "codigo", nullable = false, length = 64)
	private String codigo;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 32)
	private TipoCodigoVerificacion tipo;

	@NotNull(message = "La expiración es obligatoria")
	@Column(name = "expira_en", nullable = false)
	private Instant expiraEn;

	@Column(name = "usado", nullable = false)
	private boolean usado;

	// Nulo para filas creadas antes de existir la columna; se interpreta como 0.
	@Column(name = "intentos")
	private Integer intentos;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	public int intentosActuales() {
		return intentos == null ? 0 : intentos;
	}

	public CodigoVerificacion(Usuario usuario, String codigo,
			TipoCodigoVerificacion tipo, Instant expiraEn) {
		this.usuario = usuario;
		this.correo = usuario.getCorreo();
		this.codigo = codigo;
		this.tipo = tipo;
		this.expiraEn = expiraEn;
	}

	@PrePersist
	private void fijarFechaCreacion() {
		if (creadoEn == null) {
			creadoEn = Instant.now();
		}
	}
}
