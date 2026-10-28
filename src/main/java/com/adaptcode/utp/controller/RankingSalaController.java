package com.adaptcode.utp.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adaptcode.utp.dto.response.RankingSalaResponse;
import com.adaptcode.utp.security.UsuarioPrincipal;
import com.adaptcode.utp.service.AutorizacionSalaService;
import com.adaptcode.utp.service.RankingSalaService;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/salas/{salaId}/ranking")
@Validated
@RequiredArgsConstructor
public class RankingSalaController {

	private final RankingSalaService rankingSalaService;
	private final AutorizacionSalaService autorizacionSalaService;

	@GetMapping
	public ResponseEntity<List<RankingSalaResponse>> mostrar(
			@Positive(message = "El identificador debe ser positivo.") @PathVariable Long salaId,
			@AuthenticationPrincipal UsuarioPrincipal principal) {
		// Docente propietario o estudiante con participación en la sala.
		autorizacionSalaService.validarLectura(salaId, principal);
		return ResponseEntity.ok(rankingSalaService.mostrarRanking(salaId));
	}
}
