package com.adaptcode.utp.dto.response;

import java.util.List;

/**
 * Envoltorio de paginación. Se devuelve solo cuando el cliente envía
 * {@code page} o {@code size}; sin esos parámetros los endpoints conservan el
 * arreglo simple que ya consume el frontend.
 */
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last) {
}
