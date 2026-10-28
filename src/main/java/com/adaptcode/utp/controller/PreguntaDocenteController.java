package com.adaptcode.utp.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.adaptcode.utp.dto.request.PreguntaDocenteRequest;
import com.adaptcode.utp.dto.response.PreguntaDocenteResponse;
import com.adaptcode.utp.model.OrigenPregunta;
import com.adaptcode.utp.service.PreguntaDocenteService;
import com.adaptcode.utp.util.Paginacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/docentes/{docenteId}/preguntas")
@Validated
@RequiredArgsConstructor
public class PreguntaDocenteController {

	private final PreguntaDocenteService preguntaDocenteService;

	@PostMapping
	public ResponseEntity<PreguntaDocenteResponse> crear(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Valid @RequestBody PreguntaDocenteRequest request) {
		PreguntaDocenteResponse response = preguntaDocenteService.crearPreguntaPropia(
				docenteId, request);
		return new ResponseEntity<>(response, HttpStatus.CREATED);
	}

	@PostMapping("/{microRetoId}/clonar")
	public ResponseEntity<PreguntaDocenteResponse> clonar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long microRetoId) {
		PreguntaDocenteResponse response = preguntaDocenteService.clonarPreguntaInstitucional(
				docenteId, microRetoId);
		return new ResponseEntity<>(response, HttpStatus.CREATED);
	}

	@GetMapping
	public ResponseEntity<?> listar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@RequestParam(required = false) OrigenPregunta origen,
			@RequestParam(required = false) @PositiveOrZero(message = "La página no puede ser negativa.") Integer page,
			@RequestParam(required = false) @Positive(message = "El tamaño debe ser positivo.") @Max(value = 100, message = "El tamaño máximo es 100.") Integer size) {
		return ResponseEntity.ok(Paginacion.responder(
				preguntaDocenteService.listarPorDocente(docenteId, origen), page,
				size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<PreguntaDocenteResponse> mostrar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id) {
		return ResponseEntity.ok(preguntaDocenteService.mostrarPorId(docenteId, id));
	}

	@PutMapping("/{id}")
	public ResponseEntity<PreguntaDocenteResponse> actualizar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id,
			@Valid @RequestBody PreguntaDocenteRequest request) {
		return ResponseEntity.ok(
				preguntaDocenteService.actualizarPregunta(docenteId, id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<String> eliminar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long docenteId,
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long id) {
		preguntaDocenteService.eliminarPregunta(docenteId, id);
		return ResponseEntity.ok("Pregunta eliminada.");
	}
}
