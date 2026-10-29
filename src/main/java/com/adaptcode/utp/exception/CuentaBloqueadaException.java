package com.adaptcode.utp.exception;

public class CuentaBloqueadaException extends RuntimeException {

	private final long segundosRestantes;

	public CuentaBloqueadaException(String message, long segundosRestantes) {
		super(message);
		this.segundosRestantes = segundosRestantes;
	}

	public long getSegundosRestantes() {
		return segundosRestantes;
	}
}
