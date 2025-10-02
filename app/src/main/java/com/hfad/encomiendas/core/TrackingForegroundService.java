package com.hfad.encomiendas.core;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Recolector;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Servicio foreground opcional para tracking continuo (usar sólo si se requiere seguimiento prolongado en background). */
public class TrackingForegroundService extends Service {

    public static final String ACTION_START = "TRACKING_START";
    public static final String ACTION_STOP  = "TRACKING_STOP";
    private static final String CHANNEL_ID = "tracking_channel";

    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Integer recolectorId = null;

    @Override public void onCreate() {
        super.onCreate();
        fused = LocationServices.getFusedLocationProviderClient(this);
        callback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                if (result == null || result.getLastLocation() == null || recolectorId == null) return;
                double lat = result.getLastLocation().getLatitude();
                double lon = result.getLastLocation().getLongitude();
                long ts = System.currentTimeMillis();
                io.execute(() -> {
                    try {
                        AppDatabase db = AppDatabase.getInstance(getApplicationContext());

                        // 1. Actualizar tabla Recolector (como antes)
                        db.recolectorDao().updateLocation(recolectorId, lat, lon, ts);

                        // 2. NUEVO: Crear TrackingEvents para todas las solicitudes ASIGNADAS de este recolector
                        // Esto es lo que hace que funcione igual que cuando está RECOLECTADA
                        java.util.List<com.hfad.encomiendas.data.Solicitud> solicitudesAsignadas =
                            db.solicitudDao().listByRecolectorAndEstado(recolectorId, "ASIGNADA");

                        if (solicitudesAsignadas != null && !solicitudesAsignadas.isEmpty()) {
                            android.util.Log.d("TrackingService", "Creando tracking events para " + solicitudesAsignadas.size() +
                                " solicitudes asignadas del recolector " + recolectorId + " en " + lat + "," + lon);

                            for (com.hfad.encomiendas.data.Solicitud solicitud : solicitudesAsignadas) {
                                // Crear TrackingEvent con la ubicación GPS real del recolector
                                com.hfad.encomiendas.data.TrackingEvent trackingEvent = new com.hfad.encomiendas.data.TrackingEvent();
                                trackingEvent.shipmentId = solicitud.id;
                                trackingEvent.lat = lat;
                                trackingEvent.lon = lon;
                                trackingEvent.type = "EN_RUTA";
                                trackingEvent.detail = "Recolector en camino - ubicación actualizada";
                                trackingEvent.occurredAt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
                                        .format(new java.util.Date(ts));

                                db.trackingEventDao().insert(trackingEvent);
                            }

                            android.util.Log.d("TrackingService", "✓ Tracking events creados exitosamente para recolector " + recolectorId);
                        }

                    } catch (Exception e) {
                        android.util.Log.e("TrackingService", "Error actualizando ubicación y tracking", e);
                    }
                });
            }
        };
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String act = intent.getAction();
            if (ACTION_STOP.equals(act)) { stopSelf(); return START_NOT_STICKY; }
            if (ACTION_START.equals(act)) {
                recolectorId = intent.getIntExtra("recolectorId", -1);
                startForeground(1001, buildNotification());
                startUpdates();
            }
        }
        return START_STICKY;
    }

    private void startUpdates(){
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30000L)
                    .setMinUpdateDistanceMeters(50f)
                    .build();
            fused.requestLocationUpdates(req, callback, getMainLooper());
        } catch (Exception ignore) {}
    }

    private Notification buildNotification(){
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_recolector)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Tracking de ubicación activo")
                .setOngoing(true)
                .build();
    }

    private void createChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Tracking", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Ubicación en segundo plano");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override public void onDestroy() {
        try { fused.removeLocationUpdates(callback); } catch (Exception ignore) {}
        io.shutdown();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
