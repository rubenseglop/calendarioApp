# Calendario

Aplicación Android nativa de calendario personal. Base inicial preparada para abrir con Android Studio o compilar con Gradle desde PowerShell.

## Requisitos
- JDK 17
- Android SDK Platform 35
- Android Gradle Plugin 8.13.2
- Android Studio reciente (recomendado) o Gradle desde terminal

## Abrir
1. Descomprime el proyecto.
2. En Android Studio: **Open** y selecciona la carpeta `Calendario`.
3. Espera a que termine la sincronización de Gradle.
4. Ejecuta la configuración `app` en un emulador o móvil Android.

## Compilar desde PowerShell
Cuando tengas instalado Android SDK y Gradle:
```powershell
gradle :app:assembleDebug
```
El APK se generará en `app\build\outputs\apk\debug\app-debug.apk`.

## Estado
Esta es la base funcional inicial: navegación inferior, pantalla mensual, agenda, tareas y ajustes; datos locales de demostración. Room, widgets Glance, alarmas y sincronización Google Calendar se incorporarán en las siguientes iteraciones.
