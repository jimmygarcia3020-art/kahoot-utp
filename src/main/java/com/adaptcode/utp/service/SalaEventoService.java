package com.adaptcode.utp.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.adaptcode.utp.dto.response.SalaEventoTicketResponse;
import com.adaptcode.utp.exception.AutenticacionException;
import com.adaptcode.utp.exception.CuentaBloqueadaException;

import jakarta.annotation.PreDestroy;

/**
 * Bus de eventos en vivo (Server-Sent Events) por sala.
 *
 * <p>Los clientes no envían el JWT en la conexión SSE porque
 * {@code EventSource} no permite cabeceras personalizadas y poner el token en
 * la URL lo filtraría en logs y proxies. En su lugar solicitan un ticket de un
 * solo uso y vida corta (60 s) con {@code POST /api/salas/{id}/eventos/ticket}
 * y lo canjean en {@code GET /api/salas/{id}/eventos?ticket=...}.</p>
 *
 * <p>Los eventos se publican después del commit para que un cliente que
 * reaccione al evento no lea un estado anterior. Los payloads nunca incluyen
 * alternativas correctas ni retroalimentación: solo estado de la sala y
 * contadores agregados.</p>
 */
@Service
public class SalaEventoService {

	public static final String EVENTO_SALA = "sala";
	public static final String EVENTO_PARTICIPANTES = "participantes";
	public static final String EVENTO_RESPUESTAS = "respuestas";
	public static final String EVENTO_CONECTADO = "conectado";

	private static final long VIGENCIA_TICKET_MS = 60_000L;
	private static final int MAXIMO_TICKETS = 10_000;

	private final long timeoutMs;
	private final int maximoConexionesPorSala;
	private final int maximoTicketsPorUsuario;
	private final long heartbeatMs;

	private final ConcurrentHashMap<Long, Set<SseEmitter>> emisores =
			new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Ticket> tickets =
			new ConcurrentHashMap<>();
	private final SecureRandom random = new SecureRandom();
	private final ScheduledExecutorService latido;

	public SalaEventoService(
			@Value("${sala.sse-timeout-ms:1800000}") long timeoutMs,
			@Value("${sala.sse-max-conexiones-por-sala:500}") int maximoConexionesPorSala,
			@Value("${sala.sse-max-tickets-por-usuario:10}") int maximoTicketsPorUsuario,
			@Value("${sala.sse-heartbeat-ms:20000}") long heartbeatMs) {
		this.timeoutMs = timeoutMs;
		this.maximoConexionesPorSala = Math.max(1, maximoConexionesPorSala);
		this.maximoTicketsPorUsuario = Math.max(1, maximoTicketsPorUsuario);
		this.heartbeatMs = Math.max(5_000L, heartbeatMs);
		this.latido = Executors.newSingleThreadScheduledExecutor(tarea -> {
			Thread hilo = new Thread(tarea, "sse-heartbeat");
			hilo.setDaemon(true);
			return hilo;
		});
		this.latido.scheduleAtFixedRate(this::enviarLatido, this.heartbeatMs,
				this.heartbeatMs, TimeUnit.MILLISECONDS);
	}

	/** Emite un ticket de un solo uso para abrir el flujo SSE de la sala. */
	public SalaEventoTicketResponse crearTicket(Long salaId, Long usuarioId) {
		limpiarTicketsExpirados();
		// Un usuario no puede inundar la tabla de tickets: cada ticket vive 60 s
		// y el tope global no impide que una sola cuenta lo agote.
		long vigentesUsuario = tickets.values().stream()
				.filter(ticket -> usuarioId.equals(ticket.usuarioId()))
				.filter(ticket -> !ticket.expiraEn().isBefore(Instant.now()))
				.count();
		if (vigentesUsuario >= maximoTicketsPorUsuario) {
			throw new CuentaBloqueadaException(
					"Demasiadas solicitudes de conexión en vivo. "
							+ "Espere unos segundos e intente de nuevo.",
					5);
		}
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String valor = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(bytes);
		Instant expiraEn = Instant.now().plusMillis(VIGENCIA_TICKET_MS);
		tickets.put(valor, new Ticket(salaId, usuarioId, expiraEn));
		return new SalaEventoTicketResponse(valor, expiraEn,
				VIGENCIA_TICKET_MS);
	}

	/**
	 * Canjea el ticket. Se elimina en la misma operación: un ticket no puede
	 * reutilizarse aunque el cliente reconecte; deberá pedir otro.
	 */
	public Ticket canjearTicket(String valor, Long salaId) {
		if (valor == null || valor.isBlank() || salaId == null) {
			throw ticketInvalido();
		}
		Ticket ticket = tickets.remove(valor);
		if (ticket == null || !salaId.equals(ticket.salaId())
				|| ticket.expiraEn().isBefore(Instant.now())) {
			throw ticketInvalido();
		}
		return ticket;
	}

	/** Abre el flujo SSE y registra el emisor para los eventos de la sala. */
	public SseEmitter suscribir(Long salaId) {
		Set<SseEmitter> sala = emisores.computeIfAbsent(salaId,
				id -> ConcurrentHashMap.newKeySet());
		if (sala.size() >= maximoConexionesPorSala) {
			throw new CuentaBloqueadaException(
					"Demasiadas conexiones en vivo para esta sala. "
							+ "Intente de nuevo en unos segundos.",
					5);
		}
		SseEmitter emitter = new SseEmitter(timeoutMs);
		sala.add(emitter);
		Runnable quitar = () -> quitar(salaId, emitter, sala);
		emitter.onCompletion(quitar);
		emitter.onTimeout(() -> {
			emitter.complete();
			quitar.run();
		});
		emitter.onError(error -> quitar.run());
		try {
			emitter.send(SseEmitter.event().name(EVENTO_CONECTADO)
					.data(Map.of("salaId", salaId)));
		} catch (IOException ex) {
			quitar.run();
			emitter.completeWithError(ex);
		}
		return emitter;
	}

	/**
	 * Publica un evento cuando la transacción actual confirme. Si no hay
	 * transacción se publica de inmediato.
	 */
	public void publicarTrasCommit(Long salaId, String evento, Object datos) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(
					new TransactionSynchronization() {
						@Override
						public void afterCommit() {
							publicar(salaId, evento, datos);
						}
					});
			return;
		}
		publicar(salaId, evento, datos);
	}

	/** Publica de inmediato. Se usa solo desde {@link #publicarTrasCommit}. */
	public void publicar(Long salaId, String evento, Object datos) {
		Set<SseEmitter> sala = emisores.get(salaId);
		if (sala == null || sala.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : sala) {
			try {
				emitter.send(SseEmitter.event().name(evento).data(datos));
			} catch (IOException | IllegalStateException ex) {
				quitar(salaId, emitter, sala);
				try {
					emitter.completeWithError(ex);
				} catch (IllegalStateException ignorada) {
					// El emisor ya estaba cerrado.
				}
			}
		}
	}

	public int conexionesActivas(Long salaId) {
		Set<SseEmitter> sala = emisores.get(salaId);
		return sala == null ? 0 : sala.size();
	}

	private void enviarLatido() {
		emisores.forEach((salaId, sala) -> {
			for (SseEmitter emitter : sala) {
				try {
					emitter.send(SseEmitter.event().comment("keep-alive"));
				} catch (IOException | IllegalStateException ex) {
					quitar(salaId, emitter, sala);
				}
			}
		});
	}

	private void quitar(Long salaId, SseEmitter emitter, Set<SseEmitter> sala) {
		sala.remove(emitter);
		if (sala.isEmpty()) {
			emisores.remove(salaId, sala);
		}
	}

	private void limpiarTicketsExpirados() {
		if (tickets.size() < MAXIMO_TICKETS) {
			return;
		}
		Instant ahora = Instant.now();
		tickets.entrySet().removeIf(entrada ->
				entrada.getValue().expiraEn().isBefore(ahora));
	}

	private AutenticacionException ticketInvalido() {
		return new AutenticacionException(
				"El ticket de conexión no es válido o expiró. Solicite uno nuevo.");
	}

	@PreDestroy
	void detenerLatido() {
		latido.shutdownNow();
		emisores.values().forEach(sala -> sala.forEach(emitter -> {
			try {
				emitter.complete();
			} catch (IllegalStateException ignorada) {
				// Ya cerrado.
			}
		}));
		emisores.clear();
		tickets.clear();
	}

	/** Ticket ya canjeado: se usa para auditoría en el controlador. */
	public record Ticket(Long salaId, Long usuarioId, Instant expiraEn) {
	}
}
