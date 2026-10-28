package com.adaptcode.utp.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.adaptcode.utp.dto.request.SalaRequest;
import com.adaptcode.utp.dto.response.SalaResponse;
import com.adaptcode.utp.model.EstadoSala;
import com.adaptcode.utp.service.SalaService;
import com.adaptcode.utp.util.Paginacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/docentes/{docenteId}/salas")
@Validated
@RequiredArgsConstructor
public class SalaController {

	private final SalaService salaService;

	@PostMapping
	public ResponseEntity<SalaResponse> crear(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Valid @RequestBody SalaRequest request) {
		SalaResponse response = salaService.crearSala(docenteId, request);
		return new ResponseEntity<>(response, HttpStatus.CREATED);
	}

	@GetMapping
	public ResponseEntity<?> listar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@RequestParam(required = false) EstadoSala estado,
			@RequestParam(required = false) @PositiveOrZero(message = "La página no puede ser negativa.") Integer page,
			@RequestParam(required = false) @Positive(message = "El tamaño debe ser positivo.") @Max(value = 100, message = "El tamaño máximo es 100.") Integer size) {
		return ResponseEntity.ok(Paginacion.responder(
				salaService.listarPorDocente(docenteId, estado), page, size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<SalaResponse> mostrar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id) {
		return ResponseEntity.ok(salaService.mostrarPorId(docenteId, id));
	}

	@PostMapping("/{id}/abrir-espera")
	public ResponseEntity<SalaResponse> abrirEspera(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id) {
		return ResponseEntity.ok(salaService.abrirEspera(docenteId, id));
	}

	@PostMapping("/{id}/iniciar")
	public ResponseEntity<SalaResponse> iniciar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id) {
		return ResponseEntity.ok(salaService.iniciarSala(docenteId, id));
	}

	/**
	 * Habilita la siguiente pregunta del quiz y reinicia el cronómetro en el
	 * servidor. Sin esta llamada los estudiantes no pueden responder.
	 */
	@PostMapping("/{id}/siguiente-pregunta")
	public ResponseEntity<SalaResponse> siguientePregunta(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id) {
		return ResponseEntity.ok(
				salaService.habilitarSiguientePregunta(docenteId, id));
	}

	@PostMapping("/{id}/pausar")
	public ResponseEntity<SalaResponse> pausar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id) {
		return ResponseEntity.ok(salaService.pausarSala(docenteId, id));
	}

	@PostMapping("/{id}/reanudar")
	public ResponseEntity<SalaResponse> reanudar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id) {
		return ResponseEntity.ok(salaService.reanudarSala(docenteId, id));
	}

	@PostMapping("/{id}/finalizar")
	public ResponseEntity<SalaResponse> finalizar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id) {
		return ResponseEntity.ok(salaService.finalizarSala(docenteId, id));
	}
}
