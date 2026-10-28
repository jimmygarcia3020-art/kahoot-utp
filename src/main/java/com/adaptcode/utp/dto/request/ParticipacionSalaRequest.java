package com.adaptcode.utp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ParticipacionSalaRequest(
		@NotNull(message = "El estudiante es obligatorio.") @Positive(message = "El identificador debe ser positivo.") Long estudianteId,
		@NotBlank(message = "El PIN es obligatorio.") @Pattern(regexp = "\\d{6}", message = "El PIN debe tener seis dígitos numéricos.") String pin) {
}
