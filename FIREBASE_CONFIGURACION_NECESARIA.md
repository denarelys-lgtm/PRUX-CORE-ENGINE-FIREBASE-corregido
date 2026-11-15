# Configuración necesaria de Firebase para PRUX

La app ahora usa Firebase Authentication anónima antes de escribir en Realtime Database.

1. En Firebase Console abre Authentication > Sign-in method y habilita Anonymous (Anónimo).
2. En Realtime Database > Rules, usa estas reglas iniciales para permitir únicamente escrituras autenticadas en el nodo de dispositivos y validar que el UID guardado corresponda a la sesión. Las lecturas quedan deshabilitadas:

```json
{
  "rules": {
    "dispositivos": {
      "$deviceId": {
        ".read": false,
        ".write": "auth != null",
        ".validate": "newData.hasChildren(['modelo', 'androidId', 'firebaseUid', 'plataforma', 'estado', 'timestamp']) && newData.child('firebaseUid').val() == auth.uid && newData.child('androidId').val() == $deviceId"
      }
    }
  }
}
```

Estas reglas permiten que cualquier usuario anónimo autenticado escriba/actualice un registro bajo el ID de dispositivo que declara; no habilitan lecturas. Para una flota de dispositivos con un modelo de confianza más estricto, conviene migrar a rutas basadas en UID o usar un backend con tokens/claims.

No coloques `serviceAccountKey.json` ni claves privadas de Admin SDK dentro del APK. El archivo `app/google-services.json` es la configuración cliente y debe corresponder al paquete `com.example.detectcamera`.

El registro se intenta al iniciar el servidor local, aunque Cloudflare todavía no tenga URL. Cuando el túnel genera la URL, se actualiza el mismo registro.
