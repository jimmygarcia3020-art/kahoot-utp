# AdaptCode UTP

Plataforma con frontend web y API REST. La interfaz está integrada en Spring Boot:
abre `http://localhost:8080/` después de iniciar la aplicación.

Entrega de la última sesión para continuar con otro compañero o IA:
[23 de octubre de 2026](ENTREGA_2026-10-23.md).

## Arranque local en Windows

```powershell
./scripts/start-local.ps1
```

Prepara Java 25 y PostgreSQL dentro del proyecto, inicia la base local en el puerto
5433 y ejecuta Spring Boot en [127.0.0.1:8080](http://127.0.0.1:8080/).
No requiere instalar un servicio de Windows ni modificar el Java global.
Las credenciales locales se generan y guardan cifradas para tu cuenta de Windows.
Los perfiles `local,demo` cargan los cuatro retos iniciales en PostgreSQL;
la interfaz usa la API real y no necesita `?demo=1`.

Maven usa automáticamente el JDK de `.tools/java` y su caché dentro de `.tools/m2`:

```powershell
./mvnw.cmd -version
./mvnw.cmd test
npm run test:integration
```

La última orden requiere el backend activo y crea registros de prueba en la base
local. Consulta [la guía del entorno local](docs/ENTORNO_LOCAL.md).

## Frontend independiente

Para revisar únicamente el frontend con Node.js 22+, sin instalar dependencias:

```powershell
npm run dev
```

Abre `http://127.0.0.1:4173/?demo=1#/` para la demostración, o
`http://127.0.0.1:4173/` para conectarte a la API local en el puerto 8080.
La demo incluye vistas de estudiante y docente; el PIN de ejemplo es `482913`.

Pruebas unitarias: `npm run check:frontend` y `npm run test:frontend`.
Consulta [la guía del frontend](docs/FRONTEND.md) para arquitectura, pantallas y
límites actuales de las salas en vivo.

## Backend

API REST de la plataforma educativa AdaptCode UTP, construida con Spring Boot
4.1.1, Java 25, Spring Security + JWT y PostgreSQL.

## Alcance implementado

- **Auth con JWT** (`/api/auth`): registro por patrón de correo (`U` estudiante,
  `C` docente, resto externo), login, verificación con código de 6 dígitos,
  recuperación y restablecimiento con invalidación de sesiones.
- **Catálogo público**: cursos, temas y micro-retos (selección múltiple,
  ordenamiento de bloques, predicción de salida y selección de fragmentos).
  Sin token. No expone la correcta, el orden esperado ni la retroalimentación.
- **Docente** (rol `DOCENTE_UTP`): preguntas propias y copias, alternativas,
  retroalimentaciones (Q19), quizzes `BORRADOR`/`LISTO`, salas con PIN de 6
  dígitos y máquina de estados (`BORRADOR` → `ESPERA` → `EN_CURSO` ⇄
  `PAUSADA` → `FINALIZADA`), participaciones, anulaciones y ranking
  (`1, 1, 3`, tiempo como desempate).
- **Práctica autónoma** (autenticado): evaluación en servidor con
  retroalimentación causal sin revelar la correcta, solución explicada desde
  el 3.er intento, intentos ilimitados, progreso con punto de dominio único,
  insignias, preferencias de accesibilidad e historial.
- Los datos de demostración se cargan únicamente con el perfil `demo`.

## Requisitos locales

- Java 25.
- PostgreSQL disponible en `localhost:5433`.
- Base de datos `adaptcode_utp`.
- Variables de entorno `ADAPTCODE_DB_USERNAME` y `ADAPTCODE_DB_PASSWORD`.

En IntelliJ, configura estas dos variables en la configuración
`AdaptCodeApplication` y agrega `demo` a **Active profiles** (o define
`SPRING_PROFILES_ACTIVE=demo`) para cargar temas y micro-retos de
demostración. El perfil no cambia la URL de la API (`http://localhost:8080`).

## Ejecutar

```powershell
./mvnw.cmd spring-boot:run
```

## Probar

```powershell
./mvnw.cmd test
```

5 pruebas de servicio (quiz + Q19, intento + punto, evaluación, ranking).
La colección Postman del flujo de exposición (auth → catálogo → docente →
sala → autónoma, con variables auto-encadenadas) está en
[`docs/AdaptCode_APF2.postman_collection.json`](docs/AdaptCode_APF2.postman_collection.json)
y la referencia completa de endpoints en
[`docs/AdaptCode_API_REST.md`](docs/AdaptCode_API_REST.md).
