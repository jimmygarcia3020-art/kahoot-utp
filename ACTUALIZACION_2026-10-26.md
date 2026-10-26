# Actualización del frontend al backend del 24 de octubre

Fecha: **26 de octubre de 2026**, zona horaria America/Lima.

Fuente del backend: repositorio `Johan409IA/kahoot-utp`, commits del 24 de octubre
(`be051bf`, `4e7644b`, `136431b`). Esta carpeta ya incluye el código Java de esa versión.

## Cambios del backend que afectan al frontend

- **Registro:** `POST /api/auth/registro` exige `genero` (`MASCULINO` o `FEMENINO`) y
  `fechaNacimiento` (pasada). Sin estos campos la API responde 400.
- **Inicio de sesión:** `AuthResponse` incluye `genero` y `fechaNacimiento`, que pueden ser
  `null` en cuentas creadas antes del cambio.
- **Perfil:** nuevo endpoint `PUT /api/usuarios/{usuarioId}/perfil` para completar esos datos.
  Solo funciona con el token del propio usuario.

## Cambios en el frontend

- `app.js`: el formulario de registro pide género y fecha de nacimiento. Una cuenta sin
  esos datos va a `#/completar-perfil` después de iniciar sesión, y una sesión anterior al
  cambio también se redirige ahí antes de usar el resto de la aplicación.
- `core.js`: `needsProfile(session)` y `maxBirthDate()`. La fecha máxima es ayer en hora
  de Lima, porque el backend exige una fecha pasada.
- `demo.js`: el adaptador de demostración acepta el registro con género y fecha, devuelve
  esos datos en el login y expone `PUT /usuarios/{id}/perfil`.
- `scripts/verify-live.mjs`: el registro envía género y fecha, y se añadieron comprobaciones
  del perfil (registro sin género rechazado, validación de `PUT /perfil`, y que un usuario
  no pueda modificar el perfil de otro).
- `frontend-tests/perfil.test.mjs`: pruebas de la lógica nueva.

## Backend incluido en esta carpeta

- Se copiaron `src/main/java` y `src/test/java` desde el repositorio (versión del 24 de octubre).
- **No** se copió `src/main/resources/application.properties` del repositorio: contiene la
  contraseña de la base de Supabase escrita en texto plano. Esta carpeta conserva la
  configuración local del ZIP.

## Verificación

| Comprobación | Resultado |
| --- | --- |
| `node --test frontend-tests/*.test.mjs` | 16 pruebas aprobadas (12 previas y 4 nuevas). |
| `npm run check:frontend` | Sintaxis e imports de los 7 módulos correctos. |
| `node --check` de `verify-live.mjs` | Sintaxis correcta. |

No se pudo compilar ni ejecutar el backend en este entorno (faltan Maven y las
dependencias), así que `verify-live.mjs` **no se ha ejecutado contra la API**. Debe
ejecutarse con Spring Boot levantado para confirmar el comportamiento real.

## Pendientes

- Ejecutar `./mvnw.cmd test` y `npm run test:integration` con el backend activo.
- Revisar en el navegador el flujo de cuenta antigua sin perfil (completar datos y
  continuar).
- Rotar la contraseña de Supabase que aparece en `application.properties` del repositorio
  y mover esa configuración a variables de entorno.
- Ejecutar `npm run build:frontend` después de cualquier cambio en `frontend/`, porque la salida se publica en `src/main/resources/static`.

## Frontend migrado a Angular

El frontend ahora es una aplicación Angular 21 en `frontend/`, con las mismas pantallas, textos, rutas `#/` y reglas de negocio que la versión anterior. El detalle de estructura y ejecución está en `docs/FRONTEND.md`.

Verificado en esta entrega: build de producción sin errores, 8 pruebas unitarias de Angular y una prueba de humo en Chromium contra el modo demo (login, catálogo, práctica, progreso, salas, vistas docentes, modales y registro).

Pendiente de verificar: `verify-live.mjs` con Spring Boot y PostgreSQL usando el build de Angular, y la compilación de Maven tras el cambio en `SecurityConfig` (`/*.js` y `/*.css` ahora son públicos).
