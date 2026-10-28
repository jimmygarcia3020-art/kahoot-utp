package com.adaptcode.utp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public record RespuestaSalaResponse(
		Long id,
		Long participacionId,
		Long preguntaId,
		@JsonInclude(JsonInclude.Include.NON_NULL) Boolean acierto,
		boolean omitida,
		Long tiempoMs) {
}
