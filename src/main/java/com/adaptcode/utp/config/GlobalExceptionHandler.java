package com.adaptcode.utp.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.adaptcode.utp.dto.response.ApiErrorResponse;
import com.adaptcode.utp.exception.AccesoDenegadoException;
import com.adaptcode.utp.exception.AutenticacionException;
import com.adaptcode.utp.exception.CuentaBloqueadaException;
import com.adaptcode.utp.exception.ResourceNotFoundException;
import com.adaptcode.utp.exception.SolicitudInvalidaException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Traduce las excepciones a un contrato único ({@link ApiErrorResponse}) con
 * código estable, ruta, marca de tiempo y {@code traceId}. Los detalles
 * internos nunca se exponen al cliente: se registran en el log con el traceId.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidationExceptions(
			MethodArgumentNotValidException ex, HttpServletRequest request) {
		Map<String, String> errors = campos(ex.getBindingResult().getAllErrors());
		return ResponseEntity.badRequest().body(ApiErrorResponse.de(
				HttpStatus.BAD_REQUEST.value(), "VALIDACION",
				"La solicitud contiene datos inválidos.", request, errors));
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiErrorResponse> handleBindException(
			BindException ex, HttpServletRequest request) {
		Map<String, String> errors = campos(ex.getBindingResult().getAllErrors());
		if (errors.isEmpty()) {
			errors.put("error", "La solicitud contiene parámetros inválidos o faltantes.");
		}
		return ResponseEntity.badRequest().body(ApiErrorResponse.de(
				HttpStatus.BAD_REQUEST.value(), "VALIDACION",
				"La solicitud contiene datos inválidos.", request, errors));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
			ConstraintViolationException ex, HttpServletRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getConstraintViolations().forEach(violation -> {
			String path = violation.getPropertyPath().toString();
			String field = path.contains(".")
					? path.substring(path.lastIndexOf('.') + 1) : path;
			errors.put(field, violation.getMessage());
		});
		return ResponseEntity.badRequest().body(ApiErrorResponse.de(
				HttpStatus.BAD_REQUEST.value(), "VALIDACION",
				"La solicitud contiene datos inválidos.", request, errors));
	}

	@ExceptionHandler({ MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ApiErrorResponse> handleBadRequest(HttpServletRequest request) {
		return ResponseEntity.badRequest().body(ApiErrorResponse.de(
				HttpStatus.BAD_REQUEST.value(), "SOLICITUD_INVALIDA",
				"La solicitud contiene parámetros inválidos o faltantes.", request));
	}

	@ExceptionHandler(SolicitudInvalidaException.class)
	public ResponseEntity<ApiErrorResponse> handleSolicitudInvalida(
			SolicitudInvalidaException ex, HttpServletRequest request) {
		return ResponseEntity.badRequest().body(ApiErrorResponse.de(
				HttpStatus.BAD_REQUEST.value(), "SOLICITUD_INVALIDA",
				ex.getMessage(), request));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(
			ResourceNotFoundException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.de(
				HttpStatus.NOT_FOUND.value(), "NO_ENCONTRADO", ex.getMessage(), request));
	}

	@ExceptionHandler(AutenticacionException.class)
	public ResponseEntity<ApiErrorResponse> handleAutenticacion(
			AutenticacionException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.de(
				HttpStatus.UNAUTHORIZED.value(), "NO_AUTENTICADO", ex.getMessage(),
				request));
	}

	@ExceptionHandler(CuentaBloqueadaException.class)
	public ResponseEntity<ApiErrorResponse> handleCuentaBloqueada(
			CuentaBloqueadaException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER,
						String.valueOf(Math.max(1, ex.getSegundosRestantes())))
				.body(ApiErrorResponse.de(HttpStatus.TOO_MANY_REQUESTS.value(),
						"DEMASIADOS_INTENTOS", ex.getMessage(), request));
	}

	@ExceptionHandler({ AccessDeniedException.class, AccesoDenegadoException.class })
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(
			RuntimeException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiErrorResponse.de(
				HttpStatus.FORBIDDEN.value(), "ACCESO_DENEGADO",
				ex.getMessage() != null ? ex.getMessage()
						: "No tiene permiso para acceder a este recurso.",
				request));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleCuerpoIlegible(
			HttpServletRequest request) {
		return ResponseEntity.badRequest().body(ApiErrorResponse.de(
				HttpStatus.BAD_REQUEST.value(), "CUERPO_ILEGIBLE",
				"El cuerpo de la solicitud no es válido.", request));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleTipoNoSoportado(
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(ApiErrorResponse.de(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
						"TIPO_NO_SOPORTADO",
						"El tipo de contenido no está soportado.", request));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoEncontrado(
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.de(
				HttpStatus.NOT_FOUND.value(), "NO_ENCONTRADO",
				"El recurso solicitado no existe.", request));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
				.body(ApiErrorResponse.de(HttpStatus.METHOD_NOT_ALLOWED.value(),
						"METODO_NO_PERMITIDO",
						"Método no permitido en esta ruta de consulta.", request));
	}

	/**
	 * Carreras de escritura (p. ej. dos envíos simultáneos de la misma pregunta).
	 * El cliente puede reintentar con el estado actual del recurso.
	 */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ApiErrorResponse> handleConflictoConcurrencia(
			OptimisticLockingFailureException ex, HttpServletRequest request) {
		log.warn("Conflicto de concurrencia en {}: {}", request.getRequestURI(),
				ex.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.de(
				HttpStatus.CONFLICT.value(), "CONFLICTO_CONCURRENCIA",
				"El recurso cambió mientras se procesaba la solicitud. "
						+ "Vuelva a intentarlo.",
				request));
	}

	/**
	 * Restricciones únicas/foráneas. La violación puede venir de una carrera
	 * (dos inscripciones a la vez, PIN duplicado), no de un error del servidor.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConflictoDatos(
			DataIntegrityViolationException ex, HttpServletRequest request) {
		log.warn("Conflicto de integridad en {}: {}", request.getRequestURI(),
				ex.getMostSpecificCause().getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.de(
				HttpStatus.CONFLICT.value(), "CONFLICTO_DATOS",
				"La operación entra en conflicto con el estado actual del recurso.",
				request));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex,
			HttpServletRequest request) {
		log.error("Error no controlado en {}", request.getRequestURI(), ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiErrorResponse.de(HttpStatus.INTERNAL_SERVER_ERROR.value(),
						"ERROR_INTERNO", "Error interno del servidor.", request));
	}

	private Map<String, String> campos(
			Iterable<org.springframework.validation.ObjectError> errores) {
		Map<String, String> mensajes = new LinkedHashMap<>();
		errores.forEach(error -> {
			String fieldName = error instanceof FieldError fieldError
					? fieldError.getField() : error.getObjectName();
			mensajes.put(fieldName, error.getDefaultMessage());
		});
		return mensajes;
	}
}
