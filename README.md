# Calendario

Calendario es una aplicación Android nativa, gratuita y sin publicidad, pensada para consultar y gestionar tus eventos sin depender de aplicaciones llenas de anuncios.

## Enfoque de sincronización

La aplicación utiliza el **proveedor de calendarios de Android (CalendarContract)**. Lee y escribe en las cuentas y calendarios que ya están configurados en el dispositivo, incluidos los calendarios de Google.

- No necesita iniciar sesión en Google dentro de la aplicación.
- No gestiona contraseñas, claves OAuth ni tokens propios.
- La sincronización con Google la realiza Android mediante la cuenta configurada y sus ajustes de sincronización.
- Los permisos de lectura y escritura se solicitan al usuario en tiempo de ejecución.
- Los eventos se guardan directamente en el calendario que el usuario elija.

La aplicación no envía los eventos a servidores propios.

## Funcionalidades implementadas

- Calendario mensual con navegación entre meses.
- Indicadores en los días que tienen eventos.
- Consulta de eventos reales del dispositivo.
- Vista de agenda para el día seleccionado.
- Creación, edición y eliminación de eventos.
- Selección entre calendarios visibles para filtrar lo que aparece en la app y en los widgets.
- Eventos de día completo.
- Campos de fecha, hora y descripción.
- Lectura opcional de cumpleaños desde los contactos del teléfono, con control para mostrarlos u ocultarlos.
- Indicadores especiales de cumpleaños en el calendario mensual.
- Cumpleaños del día en la vista de agenda y listado de próximos cumpleaños.
- Cálculo de edad cuando el contacto incluye el año de nacimiento.
- Compatibilidad con fechas de cumpleaños que no incluyen año (incluidos los 29 de febrero).
- Tema automático, claro u oscuro y cinco colores de acento configurables.
- Calendario mensual con mes en turquesa, selección circular, días adyacentes atenuados e indicadores de eventos y cumpleaños.
- Primer día de la semana configurable (lunes o domingo).
- Duración y recordatorio predeterminados para los eventos nuevos.
- Widgets de agenda configurables: horizonte de 7, 14, 30 o 60 días y cantidad máxima de eventos.
- Restablecimiento de preferencias sin borrar eventos ni contactos.
- Vista vertical con calendario y agenda, y vista horizontal dividida en dos paneles.
- Interfaz en español con estética oscura inspirada en Moleskine Timepage.
- Compatible con Android 10 (API 29) y posteriores.

## Próximas funcionalidades

- Notificaciones propias y búsqueda de eventos.
- Vistas semanal y diaria más completas.
- Reglas de recurrencia y mejoras de edición.
- Tareas y listas persistentes.

## Requisitos de desarrollo

- JDK 17
- Android SDK Platform 36
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Android Studio reciente (recomendado)

## Compilar

Desde la raíz del proyecto:

```bash
gradle :app:assembleDebug
```

El APK se genera en:

```
app/build/outputs/apk/debug/app-debug.apk
```

También se puede descargar el APK de cada compilación desde la sección **Actions** de GitHub. Al publicar una etiqueta `v*`, el workflow crea una Release y adjunta el APK.

## Privacidad y permisos

Para mostrar y modificar eventos, Android solicitará los permisos `READ_CALENDAR` y `WRITE_CALENDAR`. Si se deniegan, la aplicación no puede acceder a los calendarios. Los calendarios visibles y editables dependen de las cuentas y permisos configurados en el propio dispositivo.

La lectura de cumpleaños es opcional y solicita por separado el permiso `READ_CONTACTS`. Si no se concede, el calendario y la agenda siguen funcionando normalmente, pero no se muestran cumpleaños de contactos. Los datos de contactos se consultan localmente y no se copian a servidores ni se modifican los contactos. Los cumpleaños se representan dentro de esta aplicación; no se crean automáticamente eventos duplicados en Google Calendar.

## Licencia

Proyecto personal de código abierto. Se publicará la licencia del proyecto en una próxima revisión.
