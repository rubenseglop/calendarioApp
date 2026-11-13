# Calendario

Aplicación de calendario nativa para Android, en español, gratuita y sin publicidad. Reúne en una interfaz sencilla los eventos del dispositivo, la agenda diaria, los cumpleaños de tus contactos y la gestión de tareas de Google.

<p align="center">
  <strong>Android 10 (API 29) o superior</strong> · <strong>Versión 0.9.0</strong> · <strong>Interfaz en español</strong>
</p>

## Capturas de pantalla

Próximamente añadiremos capturas reales de la aplicación: vista mensual, agenda, tareas y ajustes.

> Las imágenes se incorporarán cuando dispongamos de capturas tomadas directamente de la app en ejecución. No se utilizan imágenes promocionales que puedan dar una idea distinta de la interfaz real.

## Funcionalidades

### Calendario y eventos
- Vista mensual con navegación entre meses y acceso rápido al día actual.
- Indicadores visuales para los días con eventos y cumpleaños.
- Lectura, creación, edición y eliminación de eventos en los calendarios configurados en Android.
- Selección de calendario al crear eventos y filtros de calendarios visibles.
- Eventos de día completo, descripción, hora de inicio y fin.
- Duración y recordatorio predeterminados configurables.

### Agenda
- Vista de agenda para consultar los eventos del día seleccionado.
- Presentación agrupada por fechas.
- Cumpleaños del día y próximos cumpleaños, diferenciados de los eventos.
- Diseño adaptable a orientación vertical y horizontal.

### Cumpleaños
- Lectura opcional de las fechas de nacimiento guardadas en los contactos del teléfono.
- Visualización de cumpleaños en el calendario mensual y en la agenda.
- Cálculo de edad cuando el contacto incluye el año de nacimiento.
- Compatibilidad con cumpleaños sin año y con el 29 de febrero.
- Los contactos se consultan localmente: la app no los modifica ni los envía a servidores.

### Google Tasks
- Conexión opcional con una cuenta de Google.
- Consulta de tareas pendientes y completadas.
- Creación, edición, eliminación y marcado de tareas como completadas.
- Fecha límite opcional; una tarea no reserva una franja horaria como un evento.
- Las tareas se gestionan en Google Tasks y no se mezclan con los eventos del calendario.

**Configuración necesaria:** para utilizar esta función hay que habilitar Google Tasks API y configurar el cliente OAuth de Android en un proyecto de Google Cloud. La primera autorización se realiza desde la propia aplicación.

### Apariencia y widgets
- Tema automático, claro u oscuro.
- Colores de acento configurables.
- Primer día de la semana configurable.
- Widget de calendario mensual con navegación entre meses.
- Widgets de agenda con horizonte y cantidad de eventos configurables.
- Ajustes de widgets y transparencia individual.
- Restablecimiento de preferencias sin borrar eventos ni contactos.
- Interfaz en español con diseño inspirado en calendarios editoriales.

## Sincronización y almacenamiento

**Eventos:** la aplicación utiliza el proveedor de calendarios de Android (`CalendarContract`). Trabaja con las cuentas y calendarios que ya están configurados en el dispositivo, incluidos los de Google. La sincronización con Google depende de la cuenta y de los ajustes de sincronización de Android.

**Tareas:** se accede a Google Tasks mediante su API oficial y autorización de Google. No se almacenan contraseñas ni se guarda de forma persistente el token de acceso en la aplicación.

**Cumpleaños:** se leen localmente desde los contactos del dispositivo, únicamente si el usuario concede el permiso correspondiente.

La aplicación no utiliza servidores propios para almacenar eventos, tareas o contactos.

## Privacidad y permisos

Los permisos se solicitan cuando se necesitan:

| Permiso | Para qué se utiliza |
| --- | --- |
| `READ_CALENDAR` | Consultar los eventos de los calendarios del dispositivo. |
| `WRITE_CALENDAR` | Crear, modificar y eliminar eventos. |
| `READ_CONTACTS` | Leer las fechas de cumpleaños de los contactos (opcional). |
| Acceso a Internet | Conectar con Google Tasks cuando el usuario decide utilizar esa función. |

Si no se concede el permiso de contactos, el calendario y la agenda siguen funcionando sin mostrar cumpleaños. La aplicación no crea eventos duplicados de cumpleaños en Google Calendar.

## Requisitos

- Android 10 (API 29) o posterior.
- Para desarrollar: JDK 17, Android SDK Platform 36 y Gradle 8.13.
- Para utilizar Google Tasks: cuenta de Google y configuración OAuth/API indicada anteriormente.

## Descargar

Consulta la pestaña [Releases](https://github.com/rubenseglop/calendarioApp/releases) para descargar las versiones publicadas.

También puedes consultar las compilaciones y sus resultados en [GitHub Actions](https://github.com/rubenseglop/calendarioApp/actions).

## Compilar desde el código fuente

Clona el repositorio y ejecuta desde su directorio raíz:

```bash
gradle :app:assembleDebug
```

El APK de depuración se genera en:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Entorno de desarrollo

- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin y Jetpack Compose
- Android SDK 36

## Estado del proyecto

La aplicación continúa en desarrollo. Las funciones existentes se mantienen como prioridad; las nuevas integraciones se incorporan de forma gradual.

## Licencia

Todavía no se ha añadido un archivo de licencia al repositorio. Hasta que se publique una licencia explícita, el código no debe considerarse automáticamente de libre reutilización.
