# ADR 001: Implementación de Sincronización de Portapapeles mediante Servicio de Accesibilidad

## Estatus
Aceptado

## Contexto
A partir de Android 10 (API 29), el sistema prohíbe que las aplicaciones lean el portapapeles mientras se encuentran en segundo plano. Esto rompe la funcionalidad de sincronización automática de KDE Connect, ya que el servicio de fondo no tiene la ventana de foco necesaria para acceder a los datos del portapapeles.

## Decisión
Implementar un `AccessibilityService` dedicado para actuar como puente entre la interacción del usuario y la sincronización del portapapeles.

### Justificación Técnica
1. **Superación de Restricciones de Fondo**: Los servicios de accesibilidad tienen privilegios especiales que permiten interactuar con el sistema y, al lanzar una actividad invisible (`ClipboardFloatingActivity`), se puede obtener el foco necesario para leer el portapapeles legalmente según las directrices de Android.
2. **Disparadores Basados en Estado**: Debido a que el "Botón de Accesibilidad" de Android funciona como un interruptor de estado y no como un evento de botón tradicional, se decidió vincular la acción de sincronización a los métodos de ciclo de vida del servicio:
   - `onServiceConnected()`: Dispara la sincronización al activar el servicio o usar el botón flotante.
   - `onUnbind()`: Asegura que el contenido actual se sincronice antes de que el servicio se desactive.
3. **Eficiencia Energética**: Se eliminó la monitorización constante de eventos de interfaz (`onAccessibilityEvent` vacío y limpieza de `accessibilityEventTypes` en el XML) para evitar el consumo excesivo de batería y CPU, delegando la acción exclusivamente a los triggers de conexión y desconexión.

## Consecuencias

### Positivas
- **Compatibilidad**: Funciona en versiones modernas de Android (10+) donde la lectura de portapapeles en segundo plano está restringida.
- **Control Manual**: El usuario puede disparar la sincronización a través del botón de accesibilidad del sistema.
- **Rendimiento**: El impacto en el sistema es mínimo al no procesar cada evento de la interfaz de usuario.

### Negativas / Limitaciones
- **Configuración Manual**: El usuario debe activar obligatoriamente la opción de accesibilidad en los Ajustes del sistema para que la funcionalidad opere.
- **Dependencia del Sistema**: La experiencia del usuario depende de cómo el fabricante del dispositivo implemente la interfaz de accesibilidad (ej. botón flotante vs. menú de ajustes).

## Alternativas Consideradas
- **READ_LOGS**: Se consideró leer los logs del sistema para detectar cambios en el portapapeles, pero requiere permisos de nivel de sistema o activación manual vía ADB, lo cual no es viable para la distribución general de la app.
- **Sincronización Automática por Selección**: Se implementó inicialmente, pero se descartó por ser demasiado intrusiva y generar demasiados disparos de sincronización.
