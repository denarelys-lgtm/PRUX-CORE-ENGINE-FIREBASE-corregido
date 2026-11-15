# PRUX — panel central Firebase + Cloudflare

## Incorporado
- `cloudflare-pages/`: panel único que lista en tiempo real los dispositivos de Realtime Database.
- Inicio de sesión mediante Firebase Authentication (correo/contraseña).
- Validación de administrador mediante `administradores/{uid} = true`.
- `firebase-database.rules.json`: reglas de ejemplo sin lectura pública.

## Configuración
1. En Firebase Console, proyecto `detectcameracentral`, habilita Email/Password para administradores. Mantén Anonymous habilitado para Android.
2. Project settings > Your apps: crea una aplicación Web y copia su configuración en `cloudflare-pages/firebase-config.js`. `google-services.json` es exclusivamente la configuración Android.
3. Crea la cuenta administradora en Authentication y copia su UID.
4. En Realtime Database crea manualmente `administradores/{UID}` con valor booleano `true`. No habilites escrituras de ese nodo desde clientes.
5. Revisa y publica `firebase-database.rules.json` en Realtime Database > Rules. Verifica primero que Android autentica anónimamente y escribe `dispositivos`.
6. En Cloudflare Pages conecta el repositorio, selecciona `cloudflare-pages` como Root directory y despliega. Esa URL será el panel central.

## Funcionamiento
Cada Android mantiene su servidor local en el puerto 8080 y ejecuta `cloudflared` como túnel independiente. Al detectar la URL `trycloudflare.com`, la app actualiza `dispositivos/{ANDROID_ID}`. El panel escucha ese nodo y crea una tarjeta por dispositivo. El botón abre el túnel individual.

## Hallazgos importantes / pendientes antes de producción
- Los `trycloudflare.com` Quick Tunnels son temporales; la URL puede cambiar al reiniciar el proceso. Para una flota permanente usa Named Tunnels y dominios estables.
- El `cloudflared.zip` del APK contiene solo ELF Linux/Android ARM64 (aarch64); no funcionará en Android x86/x86_64 ni necesariamente ARM de 32 bits.
- **Riesgo crítico del código original:** `CameraService` ejecuta `iniciarServidor("", "")` y `WebServer.estaAutenticado()` deja pasar todas las peticiones si usuario/contraseña están vacíos. Así el túnel puede exponer pantalla, cámara, audio y endpoints de control sin autenticación. El login del panel NO protege el servidor Android. No publiques túneles hasta agregar credenciales obligatorias por dispositivo o una capa autenticada como Cloudflare Access con túneles nombrados.
- Las reglas limitan escritura al UID autenticado que declara el registro, pero no prueban identidad física del dispositivo. Activa Firebase App Check y considera provisión individual de dispositivos.
- La app informa al iniciar y al recibir la URL; no tiene heartbeat periódico. La fecha de la tarjeta es el último reporte, no una confirmación de conexión actual.

## Prueba de aceptación
1. Instala en dos Android ARM64 distintos.
2. Comprueba dos nodos diferentes bajo `dispositivos` y una URL individual en cada uno.
3. Entra al panel con el administrador y verifica las dos tarjetas.
4. Confirma que una cuenta no administradora no puede leer `dispositivos`.
5. Antes de abrir los túneles a Internet, resuelve la autenticación del servidor Android indicada arriba.
