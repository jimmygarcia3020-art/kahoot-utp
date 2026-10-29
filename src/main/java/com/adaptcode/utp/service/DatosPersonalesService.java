package com.adaptcode.utp.service;

import com.adaptcode.utp.dto.request.EliminarCuentaRequest;
import com.adaptcode.utp.dto.response.DatosPersonalesResponse;
import com.adaptcode.utp.dto.response.MensajeResponse;

public interface DatosPersonalesService {

	/** Exporta perfil y actividad del usuario autenticado. */
	DatosPersonalesResponse exportar(Long usuarioId);

	/**
	 * Baja de cuenta: anonimiza los datos personales, desactiva el acceso y
	 * revoca todas las sesiones. La actividad académica se conserva anónima
	 * para no romper rankings ni historiales de otros usuarios.
	 */
	MensajeResponse eliminar(Long usuarioId, EliminarCuentaRequest request);
}
