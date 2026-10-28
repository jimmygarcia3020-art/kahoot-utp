package com.adaptcode.utp.exception;

/**
 * El usuario está autenticado pero no es propietario del recurso solicitado.
 * Se traduce a HTTP 403 con un mensaje que no revela la existencia del recurso.
 */
public class AccesoDenegadoException extends RuntimeException {

	public AccesoDenegadoException(String message) {
		super(message);
	}

	public AccesoDenegadoException() {
		super("No tiene permiso para acceder a este recurso.");
	}
}
