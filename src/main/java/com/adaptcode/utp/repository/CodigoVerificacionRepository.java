package com.adaptcode.utp.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.adaptcode.utp.model.CodigoVerificacion;
import com.adaptcode.utp.model.TipoCodigoVerificacion;

public interface CodigoVerificacionRepository extends JpaRepository<CodigoVerificacion, Long> {

	Optional<CodigoVerificacion> findTopByCorreoAndTipoAndUsadoFalseOrderByCreadoEnDesc(
			String correo, TipoCodigoVerificacion tipo);

	Optional<CodigoVerificacion> findTopByCorreoAndTipoOrderByCreadoEnDesc(
			String correo, TipoCodigoVerificacion tipo);

	List<CodigoVerificacion> findAllByCorreoAndTipo(String correo, TipoCodigoVerificacion tipo);

	long countByCorreoAndTipoAndCreadoEnAfter(String correo,
			TipoCodigoVerificacion tipo, Instant desde);

	void deleteAllByCorreo(String correo);
}
