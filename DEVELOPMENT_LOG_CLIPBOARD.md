# 📝 Bitácora de Desarrollo: Sincronización de Portapapeles vía Accesibilidad
**Proyecto:** KDE Connect Android
**Fecha:** 16 de abril de 2026
**Estado:** Implementado y Verificado

## 1. Objetivo del Desarrollo
Implementar un sistema de sincronización del portapapeles que pueda ser disparado manualmente a través del **Botón de Accesibilidad** de Android y la configuración de servicios de accesibilidad, superando las restricciones de Android 10+ que impiden leer el portapapeles en segundo plano sin un contexto visual.

---

## 2. Cambios Técnicos Realizados

### A. Configuración del Entorno
- **Java SDK**: Se actualizó la versión de Java a **21 (`21.0.10-amzn`)** mediante SDKMAN para cumplir con los requisitos de Gradle 9.1.0.
- **Persistencia**: Se creó/actualizó el archivo `.sdkmanrc` en la raíz del proyecto para asegurar la consistencia del JDK entre sesiones.

### B. Implementación del Servicio de Accesibilidad
Se desarrolló el `ClipboardAccessibilityService.kt` con la siguiente lógica:
- **Triggers de Sincronización**:
    - **`onServiceConnected()`**: Dispara la sincronización inmediatamente al activar el servicio (ideal para el botón flotante de accesibilidad).
    - **`onUnbind()`**: Realiza una sincronización final antes de que el servicio se desactive.
- **Integración con UI**: El servicio lanza la `ClipboardFloatingActivity`, una actividad invisible que obtiene la ventana de foco necesaria para leer el portapapeles y enviarlo al PC mediante el `ClipboardListener`.
- **Optimización**: Se eliminó la respuesta automática a eventos de selección de texto (`onAccessibilityEvent` dejado vacío) para evitar disparos involuntarios y reducir el consumo de batería.

### C. Configuración de Recursos (XML)
- Se corrigió la declaración de `clipboard_accessibility_service.xml` eliminando atributos inválidos y limpiando los `accessibilityEventTypes` para que el sistema no envíe eventos innecesarios al servicio.

---

## 3. Guía de Operaciones

### 🛠️ Cómo Compilar
El proyecto utiliza el Gradle Wrapper. Para generar la APK de depuración, ejecuta:
```bash
./gradlew assembleDebug
```
*El archivo resultante se ubica en:* `build/outputs/apk/debug/kdeconnect-android-debug.apk`

### 📲 Cómo Instalar
Para instalar en un dispositivo conectado (ya sea por USB o Wi-Fi):
```bash
# Reemplazar [ID_DISPOSITIVO] por el ID obtenido en 'adb devices'
adb -s [ID_DISPOSITIVO] install -r build/outputs/apk/debug/kdeconnect-android-debug.apk
```

### 🧪 Cómo Probar la Funcionalidad
1. **Activación Obligatoria**:
   - Ve a `Ajustes` $\rightarrow$ `Accesibilidad` $\rightarrow$ `Servicios descargados`.
   - Activa el servicio de **KDE Connect**.
2. **Prueba de Disparo Manual**:
   - Activa la funcionalidad desde el botón flotante de accesibilidad o reiniciando el interruptor en los ajustes.
   - Verifica que aparezca el Toast: *"Portapapeles enviado"*.
3. **Verificación de Logs**:
   - Para monitorear la actividad en tiempo real desde el PC:
     ```bash
     adb -s [ID_DISPOSITIVO] logcat | grep "ClipboardAccessibility"
     ```

---

## 4. Conclusiones y Observaciones
- **Limitación de Android**: Se confirmó que el "Botón de Accesibilidad" es un interruptor de estado y no un evento de clic directo. La solución de vincular la acción al `onServiceConnected` es la alternativa más robusta.
- **Rendimiento**: Al limpiar los eventos en el XML y vaciar `onAccessibilityEvent`, el impacto en la batería es mínimo.
- **Estabilidad**: La implementación de `onUnbind` asegura que no haya pérdida de datos al desactivar la función.
