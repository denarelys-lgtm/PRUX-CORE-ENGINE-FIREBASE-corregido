
package com.example.detectcamera;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CloudflareTunnelManager {

    private static final String TAG = "PruxCloudflare";

    private static Process tunnelProcess;
    private static boolean tunnelStarting = false;

    private static volatile String currentPublicUrl = null;

    private static final Pattern URL_PATTERN = Pattern.compile(
        "https://[a-zA-Z0-9-]+\\.trycloudflare\\.com"
    );

    private CloudflareTunnelManager() { }

    /**
     * Compatibilidad con el código anterior.
     * Utiliza el puerto 8080 por defecto.
     */
    public static void iniciarTunel(Context context) {
        iniciarTunel(context, 8080);
    }

    /**
     * Inicia Cloudflare utilizando el puerto real del WebServer.
     */
    public static synchronized void iniciarTunel(
            Context context, int puertoLocal) {

        if (context == null) {
            Log.e(TAG, "Context inválido.");
            return;
        }

        if (puertoLocal < 1 || puertoLocal > 65535) {
            Log.e(TAG, "Puerto inválido: " + puertoLocal);
            return;
        }

        if (tunnelStarting) {
            Log.i(TAG, "Cloudflare ya está iniciándose.");
            return;
        }

        if (tunnelProcess != null && tunnelProcess.isAlive()) {
            Log.i(TAG, "Cloudflare ya está ejecutándose.");
            return;
        }

        tunnelStarting = true;
        currentPublicUrl = null;

        Context appContext = context.getApplicationContext();

        new Thread(() -> ejecutarTunel(appContext, puertoLocal),
                "Prux-Tunnel-Thread").start();
    }

    private static void ejecutarTunel(
            Context context, int puertoLocal) {

        Process proceso = null;

        try {
            File binDir = context.getFilesDir();
            File tunnelBin = new File(binDir, "cloudflared");

            if (!tunnelBin.exists() || tunnelBin.length() == 0) {
                extraerBinario(context, tunnelBin);
            }

            if (!tunnelBin.exists() || tunnelBin.length() == 0) {
                throw new IllegalStateException(
                    "No se encontró el binario cloudflared."
                );
            }

            if (!tunnelBin.setExecutable(true, true)
                    && !tunnelBin.canExecute()) {
                throw new IllegalStateException(
                    "No se pudieron otorgar permisos de ejecución."
                );
            }

            Log.i(TAG, "Binario: " + tunnelBin.getAbsolutePath());
            Log.i(TAG, "Puerto local: " + puertoLocal);

            String destino = "http://127.0.0.1:" + puertoLocal;

            ProcessBuilder builder = new ProcessBuilder(
                tunnelBin.getAbsolutePath(),
                "tunnel",
                "--no-autoupdate",
                "--url",
                destino
            );

            builder.directory(binDir);
            builder.redirectErrorStream(true);

            proceso = builder.start();

            synchronized (CloudflareTunnelManager.class) {
                tunnelProcess = proceso;
                tunnelStarting = false;
            }

            Log.i(TAG, "Proceso Cloudflare iniciado.");
            Log.i(TAG, "Destino configurado: " + destino);

            boolean urlDetectada = false;

            try (InputStream input = proceso.getInputStream();
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input))) {

                String line;

                while ((line = reader.readLine()) != null) {

                    Log.d(TAG, line);

                    String url = extraerUrlDeLinea(line);

                    if (url != null && !urlDetectada) {

                        urlDetectada = true;
                        currentPublicUrl = url;

                        Log.i(TAG,
                            "================================");

                        Log.i(TAG,
                            "URL PUBLICA DETECTADA: " + url);

                        Log.i(TAG,
                            "Actualizando Firebase...");

                        Log.i(TAG,
                            "================================");

                        DeviceRegistryManager
                            .registrarDispositivoEnNube(
                                context, url
                            );
                    }
                }
            }

            int exitCode = proceso.waitFor();

            Log.e(TAG,
                "Cloudflare terminó. Código de salida: "
                    + exitCode);

            if (!urlDetectada) {
                Log.e(TAG,
                    "El proceso terminó sin entregar una URL pública.");
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            Log.e(TAG, "Hilo Cloudflare interrumpido.", e);

        } catch (Exception e) {

            Log.e(TAG,
                "ERROR CRÍTICO INICIANDO CLOUDFLARE: "
                    + e.getMessage(), e);

        } finally {

            synchronized (CloudflareTunnelManager.class) {

                if (tunnelProcess == proceso) {
                    tunnelProcess = null;
                }

                tunnelStarting = false;
            }

            Log.i(TAG, "Finalizó el ciclo del administrador Cloudflare.");
        }
    }

    /**
     * Extrae el ejecutable del ZIP de assets.
     */
    private static void extraerBinario(
            Context context, File destino) throws Exception {

        Log.i(TAG, "Extrayendo cloudflared.zip...");

        boolean encontrado = false;

        try (InputStream asset = context.getAssets()
                    .open("cloudflared.zip");
             ZipInputStream zis = new ZipInputStream(asset)) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {

                String nombre = entry.getName();

                if (!entry.isDirectory()
                        && (nombre.equals("cloudflared")
                        || nombre.endsWith("/cloudflared"))) {

                    try (FileOutputStream fos =
                            new FileOutputStream(destino)) {

                        byte[] buffer = new byte[8192];
                        int length;

                        while ((length = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, length);
                        }

                        fos.flush();
                    }

                    encontrado = true;
                    zis.closeEntry();
                    break;
                }

                zis.closeEntry();
            }
        }

        if (!encontrado) {
            destino.delete();

            throw new IllegalStateException(
                "El ZIP no contiene el ejecutable cloudflared."
            );
        }

        if (destino.length() == 0) {
            destino.delete();

            throw new IllegalStateException(
                "El ejecutable extraído está vacío."
            );
        }

        Log.i(TAG,
            "Binario extraído correctamente. Tamaño: "
                + destino.length());
    }

    /**
     * Extrae la URL incluso si la línea contiene
     * códigos ANSI o texto adicional.
     */
    private static String extraerUrlDeLinea(String linea) {

        if (linea == null || linea.isEmpty()) {
            return null;
        }

        String limpia = linea.replaceAll(
            "\\u001B\\[[;\\d]*[ -/]*[@-~]",
            ""
        );

        Matcher matcher = URL_PATTERN.matcher(limpia);

        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    public static String obtenerUrlPublica() {
        return currentPublicUrl;
    }

    public static synchronized boolean estaEjecutandose() {
        return tunnelStarting ||
            (tunnelProcess != null && tunnelProcess.isAlive());
    }

    public static synchronized void detenerTunel() {

        if (tunnelProcess != null) {

            Log.i(TAG, "Deteniendo Cloudflare...");

            tunnelProcess.destroy();

            if (tunnelProcess.isAlive()) {
                tunnelProcess.destroyForcibly();
            }

            tunnelProcess = null;
        }

        currentPublicUrl = null;

        Log.i(TAG, "Solicitud de detención de Cloudflare completada.");
    }
}
