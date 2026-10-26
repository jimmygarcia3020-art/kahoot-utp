# Frontend de AdaptCode UTP

El frontend es una aplicación **Angular 21** (componentes standalone, señales, plantillas estrictas) que vive en `frontend/`. Su compilación se publica en `src/main/resources/static`, desde donde Spring Boot la sirve. Las rutas usan hash (`#/retos`, `#/reto/1`), igual que la versión anterior, para conservar enlaces y filtros.

## Preparar y compilar

Requisitos: Node.js 22.12 o posterior y npm.

```powershell
npm --prefix frontend install --legacy-peer-deps
npm run build:frontend
```

`--legacy-peer-deps` es necesario por una dependencia opcional de `jsdom` que npm no resuelve bien. El build reemplaza el contenido de `src/main/resources/static`; no edites esos archivos a mano.

Para ejecutar la aplicación con el backend:

```powershell
./scripts/start-local.ps1
```

Abre `http://127.0.0.1:8080/`. Las solicitudes usan `/api` en el mismo origen y el JWT de la cuenta. La configuración de seguridad permite el documento inicial, el favicon y los archivos JS/CSS de la raíz generados por Angular (`/*.js`, `/*.css`) y `/assets/**`. Las reglas de la API no cambian.

Para revisar sin levantar Spring Boot:

```powershell
npm run dev
```

- Interfaz con API real: `http://127.0.0.1:4173/`.
- Demo: `http://127.0.0.1:4173/?demo=1#/acceso`, con botones de estudiante y docente.
- PIN de demostración: `482913`.

El servidor de vista previa escucha solo en loopback, reenvía `/api` a `http://localhost:8080` y permite cambiar el puerto con `FRONTEND_PORT` y el destino con `ADAPTCODE_API_ORIGIN`.

## Pruebas

```powershell
npm run test:frontend
```

Ejecuta las pruebas de Angular (Vitest) sobre la lógica de utilidades y el adaptador de demostración: 8 pruebas, incluidas las de perfil obligatorio, fecha máxima de nacimiento, validación del registro y persistencia del perfil.

`npm run test:integration` ejecuta `scripts/verify-live.mjs` contra la API real con Spring Boot levantado. Crea cuentas y datos de prueba: úsalo sobre una base de desarrollo.

## Estructura

| Ruta | Responsabilidad |
| --- | --- |
| `src/app/app.ts`, `app.html` | Shell: barra lateral, barra superior, menú móvil, pie, aviso de demo y carga inicial del catálogo y del progreso. |
| `src/app/app.routes.ts`, `app.config.ts` | Rutas, guardas y configuración del router (hash y paso de parámetros a componentes). |
| `src/app/core/api.service.ts` | Cliente HTTP: JWT, tiempo de espera de 12 s, mensajes de error del servidor e invalidación por 401. |
| `src/app/core/session.service.ts` | Sesión en `sessionStorage` con expiración, login, cierre, progreso e insignias. |
| `src/app/core/catalog.service.ts` | Cursos, temas y micro-retos públicos (se cargan una vez). |
| `src/app/core/preferences.service.ts` | Accesibilidad: contraste, tamaño de texto, movimiento, color y sonido, aplicados en `<html>`. |
| `src/app/core/ui.service.ts` | Avisos (toast) y ventana modal genérica. |
| `src/app/core/icon.directive.ts` | Iconos SVG (`<svg appIcon="nombre">`) con los mismos trazos del diseño original. |
| `src/app/core/demo.ts` | Adaptador de demostración. Solo se activa con `?demo=1`. |
| `src/app/core/util.ts` | Etiquetas, insignias, cálculo de porcentajes, validaciones de sesión y perfil, cuerpo de evaluación. |
| `src/app/pages/` | Inicio, cursos, catálogo, práctica, progreso, insignias, acceso y completar perfil, ajustes, salas del estudiante y vistas docentes. |
| `src/app/modals/modals.ts` | Ventanas de formularios docentes, historial, perfil y ayuda. |
| `src/styles.css` | Diseño global original, sin cambios de estilo. |

## Comportamiento

- La sesión real se conserva en `sessionStorage` con expiración. Se elimina al cerrar sesión o al recibir 401. No se guardan contraseñas.
- Una cuenta sin género o fecha de nacimiento se redirige a `#/completar-perfil` antes de usar el resto de la aplicación. Esto cubre cuentas anteriores al cambio del 24 de octubre.
- La práctica se califica con `POST /api/usuarios/{id}/intentos/evaluar`. El catálogo público no contiene respuestas correctas.
- Las vistas docentes solo aparecen para cuentas con rol `DOCENTE_UTP`; las rutas `docente/*` exigen sesión.
- Los textos de formularios y de la API se insertan con plantillas de Angular, que escapan el contenido automáticamente.
- Las preferencias pueden guardarse antes de iniciar sesión, en el navegador, y se cargan desde la cuenta cuando hay sesión.

## Límites de la API que se mantienen visibles

- **Salas:** el backend expone PIN, participantes, estado, ranking y anulaciones, pero no un estado de pregunta actual para el estudiante. Por eso la vista de salas permite ingresar, consultar y controlar la sala, pero no simula una partida sincronizada.
- La actualización de salas y clasificación es manual (botón de actualizar). No hay WebSocket ni SSE.
- El clonado institucional crea la copia privada; el docente debe completar sus alternativas y explicaciones.
- Los códigos de verificación y recuperación se devuelven en las respuestas del backend actual. La interfaz los muestra como códigos recibidos de la API y no afirma que se enviaron correos.

## Verificación de esta versión

Lo comprobado en esta entrega:

- El build de producción de Angular compila sin errores ni advertencias.
- Las 8 pruebas unitarias de Angular pasan.
- Una prueba de humo en Chromium con la demo recorrió: inicio de sesión de estudiante y docente, inicio con datos, catálogo, práctica con retroalimentación, ordenar bloques, cursos, progreso, insignias, ajustes, salas, listados y detalles docentes, modales de creación y el formulario de registro con género y fecha. No hubo errores de ejecución.

Lo que **no** se ha comprobado todavía con esta versión:

- Ejecución contra Spring Boot y PostgreSQL con el build de Angular (`verify-live.mjs`).
- Compilación del backend Java después de cambiar `SecurityConfig` (permiso de `/*.js` y `/*.css`).
- Revisión visual en móvil y auditoría de accesibilidad formal.
