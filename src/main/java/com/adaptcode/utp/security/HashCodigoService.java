package com.adaptcode.utp.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hashea los códigos de verificación antes de guardarlos en la base de datos.
 *
 * <p>Se usa HMAC-SHA256 con un pepper fuera de la base ({@code CODIGO_PEPPER}).
 * Un código de seis dígitos tiene muy poca entropía: si se guardara en texto
 * plano o solo con SHA-256, cualquiera con acceso a la tabla podría probar el
 * millón de combinaciones en milisegundos. El pepper hace inviable ese ataque
 * sin conocer además el secreto de la aplicación.</p>
 *
 * <p>El mismo código produce siempre el mismo hash, de modo que se puede buscar
 * por correo y comparar sin guardar el valor original. Las filas antiguas con
 * seis dígitos en claro se reconocen como {@code legacy} y se migran al hash la
 * primera vez que se verifican correctamente.</p>
 */
@Component
public class HashCodigoService {

	private static final Pattern CODIGO_LEGACY = Pattern.compile("\\d{6}");
	private static final String ALGORITMO = "HmacSHA256";

	private final SecretKeySpec clave;
	private final HexFormat hex = HexFormat.of();

	public HashCodigoService(
			@Value("${seguridad.codigo-pepper}") String pepper) {
		if (pepper == null || pepper.isBlank() || pepper.length() < 16) {
			throw new IllegalStateException(
					"CODIGO_PEPPER debe tener al menos 16 caracteres.");
		}
		this.clave = new SecretKeySpec(
				pepper.getBytes(StandardCharsets.UTF_8), ALGORITMO);
	}

	/** Devuelve el HMAC-SHA256 en hexadecimal (64 caracteres). */
	public String hashear(String codigo) {
		if (codigo == null) {
			throw new IllegalArgumentException("El código es obligatorio.");
		}
		try {
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(clave);
			return hex.formatHex(mac.doFinal(codigo.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("No se pudo hashear el código.", ex);
		}
	}

	/**
	 * Compara en tiempo constante el valor recibido con el almacenado. Acepta
	 * tanto hashes nuevos como códigos legacy de seis dígitos.
	 */
	public boolean coincide(String almacenado, String recibido) {
		if (almacenado == null || recibido == null) {
			return false;
		}
		// Legacy: se compara el código en claro. Nuevo: se compara su HMAC.
		String esperado = almacenado;
		String calculado = esLegacy(almacenado) ? recibido : hashear(recibido);
		return MessageDigest.isEqual(
				esperado.getBytes(StandardCharsets.UTF_8),
				calculado.getBytes(StandardCharsets.UTF_8));
	}

	/** Indica si el valor almacenado está en claro y debe migrarse al hash. */
	public boolean esLegacy(String almacenado) {
		return almacenado != null && CODIGO_LEGACY.matcher(almacenado).matches();
	}
}
