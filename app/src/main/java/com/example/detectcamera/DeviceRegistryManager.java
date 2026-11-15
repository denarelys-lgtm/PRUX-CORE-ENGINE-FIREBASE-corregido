package com.example.detectcamera;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

/** Registra el dispositivo en Realtime Database usando autenticación anónima.
 * No utiliza credenciales de administrador dentro de la aplicación Android.
 */
public final class DeviceRegistryManager {

    private static final String TAG = "PruxRegistry";
    private static final String DATABASE_URL =
            "https://detectcameracentral-default-rtdb.firebaseio.com/";
    private static final String DATABASE_NODE = "dispositivos";

    private DeviceRegistryManager() { }

    /**
     * Registra el dispositivo incluso si Cloudflare todavía no ha entregado URL.
     * Cuando la URL llegue, se vuelve a llamar al método para actualizar el registro.
     */
    public static void registrarDispositivoEnNube(Context context, String urlTunel) {
        if (context == null) {
            Log.e(TAG, "Context inválido; no se puede registrar el dispositivo.");
            return;
        }

        final Context appContext = context.getApplicationContext();
        final String androidId;
        try {
            androidId = Settings.Secure.getString(
                    appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo obtener ANDROID_ID.", e);
            return;
        }

        if (androidId == null || androidId.trim().isEmpty()) {
            Log.e(TAG, "ANDROID_ID vacío; registro cancelado.");
            return;
        }

        final String url = urlTunel == null ? "" : urlTunel.trim();
        Log.i(TAG, "Preparando registro Firebase. URL de túnel: "
                + (url.isEmpty() ? "pendiente" : url));

        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser != null) {
                escribirRegistro(currentUser, androidId, url);
                return;
            }

            Log.i(TAG, "No hay sesión Firebase; iniciando autenticación anónima.");
            auth.signInAnonymously()
                    .addOnSuccessListener(result -> {
                        FirebaseUser user = result.getUser();
                        if (user == null) {
                            Log.e(TAG, "Firebase autenticó sin devolver usuario.");
                            return;
                        }
                        Log.i(TAG, "Autenticación Firebase correcta. UID: " + user.getUid());
                        escribirRegistro(user, androidId, url);
                    })
                    .addOnFailureListener(error ->
                            Log.e(TAG, "FALLÓ LA AUTENTICACIÓN ANÓNIMA DE FIREBASE: "
                                    + error.getMessage(), error));
        } catch (Exception e) {
            Log.e(TAG, "Error inicializando Firebase Authentication/Database.", e);
        }
    }

    private static void escribirRegistro(
            FirebaseUser user, String androidId, String url) {
        try {
            FirebaseDatabase database = FirebaseDatabase.getInstance(DATABASE_URL);
            DatabaseReference deviceRef = database.getReference(DATABASE_NODE).child(androidId);

            Map<String, Object> data = new HashMap<>();
            data.put("modelo", Build.MODEL == null ? "Android" : Build.MODEL);
            data.put("androidId", androidId);
            data.put("firebaseUid", user.getUid());
            data.put("plataforma", "Android");
            data.put("estado", url.isEmpty() ? "servidor_activo_tunel_pendiente" : "conectado");
            data.put("timestamp", ServerValue.TIMESTAMP);
            if (!url.isEmpty()) {
                data.put("url", url);
            }

            deviceRef.updateChildren(data, (DatabaseError error, DatabaseReference reference) -> {
                if (error != null) {
                    Log.e(TAG, "FALLO AL ESCRIBIR EN FIREBASE. Código="
                            + error.getCode() + ", mensaje=" + error.getMessage()
                            + ", detalles=" + error.getDetails());
                    return;
                }
                Log.i(TAG, "REGISTRO CONFIRMADO EN FIREBASE. Nodo: dispositivos/" + androidId);
                Log.i(TAG, "UID Firebase: " + user.getUid());
                Log.i(TAG, "Estado: " + (url.isEmpty() ? "túnel pendiente" : "conectado"));
            });
        } catch (Exception e) {
            Log.e(TAG, "Excepción preparando escritura en Realtime Database.", e);
        }
    }
}
