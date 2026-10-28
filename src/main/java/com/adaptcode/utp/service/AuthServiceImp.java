package com.adaptcode.utp.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adaptcode.utp.dto.request.LoginRequest;
import com.adaptcode.utp.dto.request.RecuperarContrasenaRequest;
import com.adaptcode.utp.dto.request.ReenviarCodigoRequest;
import com.adaptcode.utp.dto.request.RefreshTokenRequest;
import com.adaptcode.utp.dto.request.RegistroRequest;
import com.adaptcode.utp.dto.request.RestablecerContrasenaRequest;
import com.adaptcode.utp.dto.request.VerificarCodigoRequest;
import com.adaptcode.utp.dto.response.AuthResponse;
import com.adaptcode.utp.dto.response.CodigoResponse;
import com.adaptcode.utp.dto.response.MensajeResponse;
import com.adaptcode.utp.dto.response.RegistroResponse;
import com.adaptcode.utp.exception.AutenticacionException;
import com.adaptcode.utp.exception.CuentaBloqueadaException;
import com.adaptcode.utp.exception.SolicitudInvalidaException;
import com.adaptcode.utp.model.CodigoVerificacion;
import com.adaptcode.utp.model.RolUsuario;
import com.adaptcode.utp.model.TipoCodigoVerificacion;
import com.adaptcode.utp.model.TokenRefresco;
import com.adaptcode.utp.model.Usuario;
import com.adaptcode.utp.repository.CodigoVerificacionRepository;
import com.adaptcode.utp.repository.TokenRefrescoRepository;
import com.adaptcode.utp.repository.UsuarioRepository;
import com.adaptcode.utp.security.JwtService;
import com.adaptcode.utp.security.HashCodigoService;
import com.adaptcode.utp.security.LoginAttemptService;
import com.adaptcode.utp.security.PoliticaContrasena;
import com.adaptcode.utp.security.UsuarioPrincipal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

/**
 * Registro, inicio de sesión y verificación de correo. El rol se deriva del
 * patrón del correo institucional; los correos externos requieren código.
 *
 * <p>Endurecimientos aplicados: política de contraseñas, bloqueo temporal por
 * intentos fallidos, límite de intentos por código, comparación en tiempo
 * constante, cooldown de reenvío y mensajes que no revelan si una cuenta
 * existe en los flujos de verificación/restablecimiento.</p>
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImp implements AuthService {

	private static final Pattern CORREO_ESTUDIANTE = Pattern
			.compile("^u\\d{8}@utp\\.edu\\.pe$");
	private static final Pattern CORREO_DOCENTE = Pattern
			.compile("^c\\d{5}@utp\\.edu\\.pe$");
	private static final String DOMINIO_UTP = "@utp.edu.pe";
	private static final String MENSAJE_NEUTRAL = "Si el correo está registrado, "
			+ "recibirás un código.";
	private static final String MENSAJE_CODIGO_INVALIDO = "El código no es válido "
			+ "o ya expiró.";
	private static final String MENSAJE_DEMASIADOS_INTENTOS = "Demasiados intentos "
			+ "para este código. Solicite uno nuevo.";
	private static final String MENSAJE_CREDENCIALES = "Correo o contraseña "
			+ "incorrectos.";

	private final UsuarioRepository usuarioRepository;
	private final CodigoVerificacionRepository codigoVerificacionRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;
	private final PoliticaContrasena politicaContrasena;
	private final LoginAttemptService loginAttemptService;
	private final HashCodigoService hashCodigoService;
	private final ApplicationEventPublisher eventPublisher;
	private final TokenRefrescoRepository tokenRefrescoRepository;
	private final TokenRefrescoService tokenRefrescoService;
	private final SecureRandom random = new SecureRandom();

	@Value("${seguridad.codigo-expiracion-minutos:10}")
	private long minutosExpiracionCodigo;

	@Value("${seguridad.codigo-max-intentos:5}")
	private int maxIntentosCodigo;

	@Value("${seguridad.codigo-reenvio-segundos:60}")
	private long segundosReenvio;

	@Value("${seguridad.codigo-max-por-dia:10}")
	private int maxCodigosPorDia;

	@Value("${seguridad.refresh-max-sesiones:5}")
	private int maxSesionesActivas;

	// Solo para el modo demo sin servicio de correo. En producción debe ser
	// false y el código debe enviarse por email, nunca en la respuesta HTTP.
	@Value("${seguridad.exponer-codigos:false}")
	private boolean exponerCodigos;

	@Override
	@Transactional
	public RegistroResponse registrar(RegistroRequest request) {
		String correo = normalizarCorreo(request.correo());
		if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
			throw new SolicitudInvalidaException("El correo ya está registrado.");
		}
		politicaContrasena.validar(request.contrasena(), correo, request.nombres(),
				request.apellidos());
		RolUsuario rol = resolverRol(correo);
		String hash = passwordEncoder.encode(request.contrasena());
		Usuario usuario = usuarioRepository.save(new Usuario(correo,
				request.nombres().trim(), request.apellidos().trim(), hash, rol,
				false, request.genero(), request.fechaNacimiento()));
		CodigoGenerado codigo = crearCodigo(usuario,
				TipoCodigoVerificacion.VERIFICACION_CUENTA);
		return new RegistroResponse(usuario.getId(), correo, rol, true,
				codigoParaRespuesta(codigo.codigoPlano()),
				codigo.entidad().getExpiraEn());
	}

	@Override
	@Transactional
	public AuthResponse login(LoginRequest request) {
		String correo = normalizarCorreo(request.correo());
		// Evita que una contraseña gigante llegue a BCrypt (coste/errores interna).
		if (request.contrasena() == null || request.contrasena().isBlank()
				|| request.contrasena().getBytes(StandardCharsets.UTF_8).length > PoliticaContrasena.LONGITUD_MAXIMA) {
			throw new AutenticacionException(MENSAJE_CREDENCIALES);
		}
		long segundosBloqueo = loginAttemptService.segundosRestantes(correo);
		if (segundosBloqueo > 0) {
			throw new CuentaBloqueadaException("Demasiados intentos fallidos. "
					+ "Intente de nuevo en " + segundosBloqueo + " segundos.",
					segundosBloqueo);
		}
		try {
			Authentication authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(correo,
							request.contrasena()));
			UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
			loginAttemptService.registrarExito(correo);
			return emitirRespuesta(principal);
		} catch (DisabledException ex) {
			throw new AutenticacionException("La cuenta no está verificada. "
					+ "Confirme el código enviado a su correo.");
		} catch (BadCredentialsException ex) {
			loginAttemptService.registrarFallo(correo);
			throw new AutenticacionException(MENSAJE_CREDENCIALES);
		} catch (AuthenticationException ex) {
			throw new AutenticacionException("No se pudo iniciar sesión.");
		}
	}

	@Override
	@Transactional
	public AuthResponse refrescar(RefreshTokenRequest request) {
		String token = request.refreshToken();
		Claims claims;
		try {
			claims = jwtService.parseRefreshToken(token);
		} catch (JwtException | IllegalArgumentException ex) {
			throw sesionInvalida();
		}
		TokenRefresco guardado = tokenRefrescoRepository
				.findByTokenHash(hashToken(token))
				.orElseThrow(this::sesionInvalida);
		if (guardado.isRevocado()) {
			// Reutilización de un token ya rotado: se asume robo y se cierra
			// toda la cadena del usuario, no solo esta sesión. La revocación va
			// en transacción aparte para que sobreviva al error que se lanza.
			tokenRefrescoService.revocarTodos(guardado.getUsuario().getId());
			throw sesionInvalida();
		}
		if (guardado.getExpiraEn() == null
				|| guardado.getExpiraEn().isBefore(Instant.now())) {
			revocar(guardado);
			throw sesionInvalida();
		}
		Usuario usuario = guardado.getUsuario();
		if (!usuario.isActivo()
				|| !usuario.getCorreo().equalsIgnoreCase(claims.getSubject())) {
			revocar(guardado);
			throw sesionInvalida();
		}
		// Rotación atómica: solo un proceso puede marcar este token como usado.
		// Si otro llegó primero, se trata como reutilización y se cierra la cadena.
		int revocados = tokenRefrescoRepository.revocarSiActivo(guardado.getId(),
				Instant.now());
		if (revocados == 0) {
			tokenRefrescoService.revocarTodos(usuario.getId());
			throw sesionInvalida();
		}
		return emitirRespuesta(principalDe(usuario));
	}

	@Override
	@Transactional
	public MensajeResponse cerrarSesion(RefreshTokenRequest request) {
		if (request != null && request.refreshToken() != null
				&& !request.refreshToken().isBlank()) {
			tokenRefrescoRepository.findByTokenHash(hashToken(request.refreshToken()))
					.ifPresent(this::revocar);
		}
		// Idempotente y sin filtrar si el token existía.
		return new MensajeResponse("Sesión cerrada.");
	}

	/** Emite el par access/refresh y persiste solo el hash del refresh. */
	private AuthResponse emitirRespuesta(UsuarioPrincipal principal) {
		String accessToken = jwtService.generateToken(principal);
		String refreshToken = jwtService.generateRefreshToken(principal);
		Instant ahora = Instant.now();
		tokenRefrescoRepository.eliminarExpirados(ahora);
		tokenRefrescoRepository.save(new TokenRefresco(
				usuarioRepository.getReferenceById(principal.getId()),
				hashToken(refreshToken),
				ahora.plusMillis(jwtService.getRefreshExpirationMs())));
		limitarSesionesActivas(principal.getId());
		return new AuthResponse(accessToken, "Bearer", jwtService.getExpirationMs(),
				principal.getId(), principal.getCorreo(), principal.getNombres(),
				principal.getApellidos(), principal.getRol(), principal.getGenero(),
				principal.getFechaNacimiento(), refreshToken,
				jwtService.getRefreshExpirationMs());
	}

	/**
	 * Un usuario no puede acumular refresh tokens indefinidamente. Al superar
	 * el máximo se revocan las sesiones más antiguas; la recién emitida es la
	 * última y queda vigente.
	 */
	private void limitarSesionesActivas(Long usuarioId) {
		if (maxSesionesActivas <= 0) {
			return;
		}
		List<TokenRefresco> activos = tokenRefrescoRepository
				.findAllByUsuario_IdAndRevocadoFalseOrderByCreadoEnAscIdAsc(usuarioId);
		int exceso = activos.size() - maxSesionesActivas;
		for (int i = 0; i < exceso; i++) {
			revocar(activos.get(i));
		}
	}

	private UsuarioPrincipal principalDe(Usuario usuario) {
		return new UsuarioPrincipal(usuario.getId(), usuario.getCorreo(),
				usuario.getContrasenaHash(), usuario.getNombres(),
				usuario.getApellidos(), usuario.getRol(), usuario.getGenero(),
				usuario.getFechaNacimiento(), usuario.isActivo(),
				usuario.getCredencialCambiadaEn());
	}

	private void revocar(TokenRefresco token) {
		if (!token.isRevocado()) {
			token.setRevocado(true);
			token.setRevocadoEn(Instant.now());
			tokenRefrescoRepository.save(token);
		}
	}

	private AutenticacionException sesionInvalida() {
		return new AutenticacionException(
				"La sesión no es válida o expiró. Inicie sesión nuevamente.");
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(
					digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no disponible.", ex);
		}
	}

	@Override
	@Transactional(noRollbackFor = SolicitudInvalidaException.class)
	public MensajeResponse verificarCuenta(VerificarCodigoRequest request) {
		String correo = normalizarCorreo(request.correo());
		Optional<Usuario> encontrado = usuarioRepository.findByCorreoIgnoreCase(correo);
		if (encontrado.isEmpty()) {
			// Mismo mensaje que un código inválido: no revela si la cuenta existe.
			throw new SolicitudInvalidaException(MENSAJE_CODIGO_INVALIDO);
		}
		Usuario usuario = encontrado.get();
		// El código se valida siempre: responder "ya verificada" sin comprobarlo
		// permitiría confirmar la existencia y el estado de una cuenta.
		CodigoVerificacion codigo = buscarCodigoValido(correo,
				TipoCodigoVerificacion.VERIFICACION_CUENTA, request.codigo());
		codigo.setUsado(true);
		codigoVerificacionRepository.save(codigo);
		if (usuario.isActivo()) {
			return new MensajeResponse("La cuenta ya está verificada.");
		}
		usuario.setActivo(true);
		usuarioRepository.save(usuario);
		return new MensajeResponse("Cuenta verificada. Ya puede iniciar sesión.");
	}

	@Override
	@Transactional
	public CodigoResponse reenviarCodigoVerificacion(ReenviarCodigoRequest request) {
		String correo = normalizarCorreo(request.correo());
		Optional<Usuario> usuario = usuarioRepository.findByCorreoIgnoreCase(correo);
		if (usuario.isEmpty() || usuario.get().isActivo()) {
			return new CodigoResponse(MENSAJE_NEUTRAL, null, null);
		}
		// En cooldown se responde igual que con un correo inexistente.
		return generarCodigoConCooldown(usuario.get(),
				TipoCodigoVerificacion.VERIFICACION_CUENTA)
				.map(codigo -> new CodigoResponse(MENSAJE_NEUTRAL,
						codigoParaRespuesta(codigo.codigoPlano()),
						codigo.entidad().getExpiraEn()))
				.orElseGet(() -> new CodigoResponse(MENSAJE_NEUTRAL, null, null));
	}

	@Override
	@Transactional
	public CodigoResponse solicitarRecuperacion(RecuperarContrasenaRequest request) {
		String correo = normalizarCorreo(request.correo());
		Optional<Usuario> usuario = usuarioRepository.findByCorreoIgnoreCase(correo);
		if (usuario.isEmpty() || !usuario.get().isActivo()) {
			return new CodigoResponse(MENSAJE_NEUTRAL, null, null);
		}
		return generarCodigoConCooldown(usuario.get(),
				TipoCodigoVerificacion.RECUPERACION)
				.map(codigo -> new CodigoResponse(MENSAJE_NEUTRAL,
						codigoParaRespuesta(codigo.codigoPlano()),
						codigo.entidad().getExpiraEn()))
				.orElseGet(() -> new CodigoResponse(MENSAJE_NEUTRAL, null, null));
	}

	@Override
	@Transactional(noRollbackFor = SolicitudInvalidaException.class)
	public MensajeResponse restablecerContrasena(RestablecerContrasenaRequest request) {
		String correo = normalizarCorreo(request.correo());
		Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
				.orElseThrow(() -> new SolicitudInvalidaException(MENSAJE_CODIGO_INVALIDO));
		// La política se valida antes de consumir el código: un intento con una
		// contraseña inválida no debe gastar uno de los usos disponibles.
		politicaContrasena.validar(request.nuevaContrasena(), correo,
				usuario.getNombres(), usuario.getApellidos());
		if (passwordEncoder.matches(request.nuevaContrasena(),
				usuario.getContrasenaHash())) {
			throw new SolicitudInvalidaException(
					"La nueva contraseña debe ser diferente a la actual.");
		}
		CodigoVerificacion codigo = buscarCodigoValido(correo,
				TipoCodigoVerificacion.RECUPERACION, request.codigo());
		codigo.setUsado(true);
		codigoVerificacionRepository.save(codigo);
		usuario.setContrasenaHash(passwordEncoder.encode(request.nuevaContrasena()));
		usuario.setCredencialCambiadaEn(Instant.now());
		usuarioRepository.save(usuario);
		// Al cambiar la contraseña se cierran todas las sesiones existentes.
		tokenRefrescoRepository.revocarTodos(usuario.getId(), Instant.now());
		return new MensajeResponse("Contraseña actualizada. Inicie sesión nuevamente.");
	}

	private RolUsuario resolverRol(String correo) {
		if (CORREO_ESTUDIANTE.matcher(correo).matches()) {
			return RolUsuario.ESTUDIANTE_UTP;
		}
		if (CORREO_DOCENTE.matcher(correo).matches()) {
			return RolUsuario.DOCENTE_UTP;
		}
		if (correo.endsWith(DOMINIO_UTP)) {
			throw new SolicitudInvalidaException("El formato del correo institucional "
					+ "no es válido. Use U+8 dígitos o C+5 dígitos @utp.edu.pe.");
		}
		return RolUsuario.USUARIO_EXTERNO;
	}

	/**
	 * Genera un código respetando el cooldown de reenvío. Devuelve vacío cuando
	 * aún no se cumple el tiempo mínimo, de modo que los flujos públicos puedan
	 * responder con el mensaje neutral sin revelar la existencia de la cuenta.
	 */
	private Optional<CodigoGenerado> generarCodigoConCooldown(Usuario usuario,
			TipoCodigoVerificacion tipo) {
		Optional<CodigoVerificacion> ultimo = codigoVerificacionRepository
				.findTopByCorreoAndTipoOrderByCreadoEnDesc(usuario.getCorreo(), tipo);
		if (ultimo.isPresent() && ultimo.get().getCreadoEn() != null) {
			long transcurridos = Duration
					.between(ultimo.get().getCreadoEn(), Instant.now()).toSeconds();
			if (transcurridos >= 0 && transcurridos < segundosReenvio) {
				return Optional.empty();
			}
		}
		// Tope diario: frena el abuso de correo sin revelar si la cuenta existe
		// (el flujo público responde el mismo mensaje neutral).
		if (maxCodigosPorDia > 0) {
			Instant inicioDia = LocalDate.now(ZoneId.systemDefault())
					.atStartOfDay(ZoneId.systemDefault()).toInstant();
			long enviadosHoy = codigoVerificacionRepository
					.countByCorreoAndTipoAndCreadoEnAfter(usuario.getCorreo(), tipo,
							inicioDia);
			if (enviadosHoy >= maxCodigosPorDia) {
				return Optional.empty();
			}
		}
		return Optional.of(crearCodigo(usuario, tipo));
	}

	/**
	 * Genera el código, guarda solo su HMAC y lo envía por correo. El valor en
	 * claro se devuelve únicamente para el modo demo; nunca se persiste.
	 */
	private CodigoGenerado crearCodigo(Usuario usuario, TipoCodigoVerificacion tipo) {
		invalidarCodigosPrevios(usuario.getCorreo(), tipo);
		String codigoPlano = String.format("%06d", random.nextInt(1_000_000));
		Instant expiraEn = Instant.now()
				.plus(minutosExpiracionCodigo, ChronoUnit.MINUTES);
		CodigoVerificacion entidad = codigoVerificacionRepository.save(
				new CodigoVerificacion(usuario, hashCodigoService.hashear(codigoPlano),
						tipo, expiraEn));
		// El correo se envía fuera de la transacción y de forma asíncrona: un
		// SMTP lento no bloquea el registro ni provoca el rollback del código.
		eventPublisher.publishEvent(
				new CodigoEmailEvento(usuario.getCorreo(), codigoPlano, tipo,
						expiraEn));
		return new CodigoGenerado(entidad, codigoPlano);
	}

	private void invalidarCodigosPrevios(String correo, TipoCodigoVerificacion tipo) {
		List<CodigoVerificacion> activos = codigoVerificacionRepository
				.findAllByCorreoAndTipo(correo, tipo).stream()
				.filter(codigo -> !codigo.isUsado())
				.toList();
		if (activos.isEmpty()) {
			return;
		}
		activos.forEach(codigo -> codigo.setUsado(true));
		codigoVerificacionRepository.saveAll(activos);
	}

	private CodigoVerificacion buscarCodigoValido(String correo,
			TipoCodigoVerificacion tipo, String valor) {
		CodigoVerificacion codigo = codigoVerificacionRepository
				.findTopByCorreoAndTipoAndUsadoFalseOrderByCreadoEnDesc(correo, tipo)
				.orElseThrow(() -> new SolicitudInvalidaException(MENSAJE_CODIGO_INVALIDO));
		if (codigo.getExpiraEn() == null
				|| codigo.getExpiraEn().isBefore(Instant.now())) {
			codigo.setUsado(true);
			codigoVerificacionRepository.save(codigo);
			throw new SolicitudInvalidaException(MENSAJE_CODIGO_INVALIDO);
		}
		if (codigo.intentosActuales() >= maxIntentosCodigo) {
			codigo.setUsado(true);
			codigoVerificacionRepository.save(codigo);
			throw new SolicitudInvalidaException(MENSAJE_DEMASIADOS_INTENTOS);
		}
		if (!hashCodigoService.coincide(codigo.getCodigo(), valor)) {
			codigo.setIntentos(codigo.intentosActuales() + 1);
			if (codigo.intentosActuales() >= maxIntentosCodigo) {
				codigo.setUsado(true);
			}
			codigoVerificacionRepository.save(codigo);
			throw new SolicitudInvalidaException(codigo.isUsado()
					? MENSAJE_DEMASIADOS_INTENTOS
					: MENSAJE_CODIGO_INVALIDO);
		}
		// Migración transparente: una fila antigua en claro pasa a HMAC al usarse.
		if (hashCodigoService.esLegacy(codigo.getCodigo())) {
			codigo.setCodigo(hashCodigoService.hashear(valor));
			codigoVerificacionRepository.save(codigo);
		}
		return codigo;
	}

	private String normalizarCorreo(String correo) {
		return correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
	}

	private String codigoParaRespuesta(String codigoPlano) {
		return exponerCodigos ? codigoPlano : null;
	}

	private record CodigoGenerado(CodigoVerificacion entidad, String codigoPlano) {
	}
}
