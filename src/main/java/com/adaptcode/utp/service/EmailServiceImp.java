package com.adaptcode.utp.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.adaptcode.utp.model.TipoCodigoVerificacion;

import lombok.extern.slf4j.Slf4j;

/**
 * Envío de códigos por SMTP. Si no hay {@code spring.mail.host} configurado
 * (entorno demo) no se intenta conectar: se registra un aviso sin incluir el
 * código en el log, porque los logs también son un canal de fuga.
 */
@Slf4j
@Service
public class EmailServiceImp implements EmailService {

	private static final DateTimeFormatter FORMATO = DateTimeFormatter
			.ofPattern("HH:mm")
			.withZone(ZoneId.systemDefault());

	private final ObjectProvider<JavaMailSender> mailSenderProvider;
	private final String remitente;
	private final boolean smtpConfigurado;

	public EmailServiceImp(ObjectProvider<JavaMailSender> mailSenderProvider,
			@Value("${spring.mail.host:}") String host,
			@Value("${spring.mail.username:${ADAPTCODE_MAIL_USERNAME:}}") String remitente) {
		this.mailSenderProvider = mailSenderProvider;
		this.remitente = remitente == null || remitente.isBlank()
				? "no-reply@adaptcode.utp"
				: remitente;
		this.smtpConfigurado = host != null && !host.isBlank();
	}

	@Override
	public void enviarCodigo(String correo, String codigo, TipoCodigoVerificacion tipo,
			Instant expiraEn) {
		if (!smtpConfigurado) {
			// Sin código en el log: solo el hecho y el destinatario ofuscado.
			log.info("SMTP no configurado; código {} generado para {} (no enviado).",
					tipo, ofuscar(correo));
			return;
		}
		JavaMailSender sender = mailSenderProvider.getIfAvailable();
		if (sender == null) {
			log.warn("JavaMailSender no disponible; no se pudo enviar el código a {}.",
					ofuscar(correo));
			return;
		}
		SimpleMailMessage mensaje = new SimpleMailMessage();
		mensaje.setFrom(remitente);
		mensaje.setTo(correo);
		mensaje.setSubject(asunto(tipo));
		mensaje.setText(cuerpo(codigo, tipo, expiraEn));
		try {
			sender.send(mensaje);
			log.info("Código {} enviado a {}.", tipo, ofuscar(correo));
		} catch (MailException ex) {
			// No se propaga: el flujo de registro no debe fallar por el correo.
			// El usuario puede pedir un reenvío; el detalle queda en el log.
			log.error("No se pudo enviar el código {} a {}: {}", tipo,
					ofuscar(correo), ex.getMessage());
		}
	}

	private String asunto(TipoCodigoVerificacion tipo) {
		return tipo == TipoCodigoVerificacion.RECUPERACION
				? "AdaptCode UTP - Recupera tu contraseña"
				: "AdaptCode UTP - Verifica tu cuenta";
	}

	private String cuerpo(String codigo, TipoCodigoVerificacion tipo, Instant expiraEn) {
		String accion = tipo == TipoCodigoVerificacion.RECUPERACION
				? "restablecer tu contraseña"
				: "verificar tu cuenta";
		String hora = expiraEn == null ? "en unos minutos"
				: "antes de las " + FORMATO.format(expiraEn);
		return "Hola:\n\n"
				+ "Tu código para " + accion + " es: " + codigo + "\n\n"
				+ "El código vence " + hora + ". Si no solicitaste este correo, "
				+ "ignóralo: tu cuenta sigue segura.\n\n"
				+ "Equipo AdaptCode UTP";
	}

	private String ofuscar(String correo) {
		if (correo == null || correo.isBlank()) {
			return "(sin correo)";
		}
		int arroba = correo.indexOf('@');
		if (arroba <= 1) {
			return "***" + (arroba >= 0 ? correo.substring(arroba) : "");
		}
		return correo.charAt(0) + "***" + correo.substring(arroba);
	}
}
