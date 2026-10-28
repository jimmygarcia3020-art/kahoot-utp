package com.adaptcode.utp.util;

import java.util.List;

import com.adaptcode.utp.dto.response.PageResponse;

/**
 * Paginación de listas ya materializadas. Se aplica en la capa de controller
 * para no alterar los contratos existentes: si el cliente no envía
 * {@code page} ni {@code size}, recibe el arreglo de siempre.
 */
public final class Paginacion {

	public static final int TAMANO_POR_DEFECTO = 20;
	public static final int TAMANO_MAXIMO = 100;

	private Paginacion() {
	}

	public static <T> Object responder(List<T> elementos, Integer page, Integer size) {
		if (page == null && size == null) {
			return elementos;
		}
		int tamano = size == null ? TAMANO_POR_DEFECTO
				: Math.min(Math.max(size, 1), TAMANO_MAXIMO);
		int pagina = page == null ? 0 : Math.max(page, 0);
		long total = elementos.size();
		int totalPaginas = total == 0 ? 0
				: (int) Math.ceil(total / (double) tamano);
		int desde = (int) Math.min((long) pagina * tamano, total);
		int hasta = (int) Math.min((long) desde + tamano, total);
		return new PageResponse<>(elementos.subList(desde, hasta), pagina, tamano,
				total, totalPaginas, pagina == 0,
				totalPaginas == 0 || pagina >= totalPaginas - 1);
	}
}
