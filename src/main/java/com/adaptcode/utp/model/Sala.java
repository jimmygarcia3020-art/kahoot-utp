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
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
@Table(name = "salas", indexes = {
		@Index(name = "idx_salas_docente", columnList = "docente_id")
})
public class Sala {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "docente_id", nullable = false)
	private Usuario docente;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "quiz_origen_id", nullable = false)
	private Quiz quizOrigen;

	@NotBlank(message = "El PIN es obligatorio")
	@Pattern(regexp = "\\d{6}", message = "El PIN debe tener seis dígitos numéricos")
	@Column(name = "pin", nullable = false, unique = true, length = 6)
	private String pin;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 16)
	private EstadoSala estado = EstadoSala.BORRADOR;

	@Positive(message = "El tiempo por pregunta debe ser positivo")
	@Column(name = "tiempo_por_pregunta", nullable = false)
	private Integer tiempoPorPregunta = 60;

	@Enumerated(EnumType.STRING)
	@Column(name = "ampliacion", nullable = false, length = 16)
	private AmpliacionSala ampliacion = AmpliacionSala.ESTANDAR;

	@Column(name = "quiz_version_congelada_en")
	private Instant quizVersionCongeladaEn;

	/**
	 * Pregunta que el docente tiene habilitada en este momento. Las respuestas
	 * solo se aceptan para esta pregunta y el servidor mide el tiempo desde
	 * {@link #preguntaIniciadaEn}; el cliente no decide ni el acierto ni el tiempo.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pregunta_actual_id")
	private PreguntaDocente preguntaActual;

	@Column(name = "pregunta_iniciada_en")
	private Instant preguntaIniciadaEn;

	/**
	 * Momento en que el docente pausó la sala. Al reanudar se desplaza
	 * {@link #preguntaIniciadaEn} por el tiempo pausado para no castigar a los
	 * estudiantes con un cronómetro detenido.
	 */
	@Column(name = "pausada_en")
	private Instant pausadaEn;

	/** Bloqueo optimista: dos docentes/acciones simultáneas no se pisan. */
	@Version
	@Column(name = "version")
	private Long version;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	public Sala(Usuario docente, Quiz quizOrigen, String pin, Integer tiempoPorPregunta) {
		this.docente = docente;
		this.quizOrigen = quizOrigen;
		this.pin = pin;
		this.tiempoPorPregunta = tiempoPorPregunta;
		this.estado = EstadoSala.BORRADOR;
		this.ampliacion = AmpliacionSala.ESTANDAR;
	}

	/** Tiempo máximo efectivo de una pregunta, considerando la ampliación. */
	public int segundosLimitePregunta() {
		int base = tiempoPorPregunta == null || tiempoPorPregunta <= 0 ? 60
				: tiempoPorPregunta;
		if (ampliacion == AmpliacionSala.MAS_100) {
			return base * 2;
		}
		if (ampliacion == AmpliacionSala.MAS_50) {
			return base + base / 2;
		}
		return base;
	}

	@PrePersist
	private void fijarFechaCreacion() {
		if (creadoEn == null) {
			creadoEn = Instant.now();
		}
	}
}
