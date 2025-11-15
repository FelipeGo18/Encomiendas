package com.hfad.encomiendas;

import android.app.Application;
import android.util.Log;

import com.hfad.encomiendas.api.AutoSyncManager;
import com.hfad.encomiendas.core.ServerMonitoringService;

/**
 * Clase Application personalizada que se ejecuta al iniciar la aplicación
 * Se encarga de realizar la sincronización automática de datos con el servidor
 */
public class EncomiendasApp extends Application {

    private static final String TAG = "EncomiendasApp";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚀 Aplicación iniciada - Ejecutando sincronización automática");

        // 🔄 SINCRONIZACIÓN AUTOMÁTICA AL REINICIAR LA APP
        // Se ejecuta cada vez que se inicia la aplicación (después de reinicio, cierre, etc.)
        AutoSyncManager.syncOnAppStart(this);

        // 📡 INICIAR MONITOREO DEL SERVIDOR
        // Se ejecuta en background para detectar cuando el servidor se reinicia
        ServerMonitoringService.start(this);
        Log.d(TAG, "📡 Servicio de monitoreo iniciado");
    }
}
