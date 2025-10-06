package com.hfad.encomiendas.core;

import android.os.Handler;
import android.os.Looper;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.EtaCache;
import com.hfad.encomiendas.data.TrackingEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

public class TrackingService {

    private final AppDatabase db;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public TrackingService(AppDatabase db) { this.db = db; }

    public interface TimelineEtaCb { void done(List<TrackingEvent> events, EtaCache eta); }
    public interface EventsCb { void done(List<TrackingEvent> events); }
    public interface VoidCb { void done(); }

    public void loadTimelineAndEta(long shipmentId, TimelineEtaCb cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<TrackingEvent> evs = db.trackingEventDao().listByShipment(shipmentId);
            EtaCache eta = db.etaCacheDao().byShipmentId(shipmentId);
            ui.post(() -> cb.done(evs, eta));
        });
    }

    public void loadEvents(long shipmentId, EventsCb cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<TrackingEvent> evs = db.trackingEventDao().listByShipment(shipmentId);
            ui.post(() -> cb.done(evs));
        });
    }

    public void logEvent(long shipmentId, String type, String detail,
                         Double lat, Double lon, VoidCb after) {
        Executors.newSingleThreadExecutor().execute(() -> {
            TrackingEvent e = new TrackingEvent();
            e.shipmentId = shipmentId;
            e.type = type;
            e.detail = detail;
            e.lat = lat;
            e.lon = lon;
            e.occurredAt = nowIso();
            db.trackingEventDao().insert(e);
            if (after != null) ui.post(after::done);
        });
    }

    // --- helpers ----
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    private static String nowIso() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
        fmt.setTimeZone(TimeZone.getDefault());
        return fmt.format(new Date(System.currentTimeMillis()));
    }
}
