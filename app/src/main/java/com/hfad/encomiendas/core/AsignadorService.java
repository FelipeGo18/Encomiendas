package com.hfad.encomiendas.core;

import android.content.Context;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Asignacion;
import com.hfad.encomiendas.data.Recolector;
import com.hfad.encomiendas.data.Solicitud;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AsignadorService {
    private final AppDatabase db;
    private final Context context;

    public AsignadorService(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.db = AppDatabase.getInstance(ctx);
    }

    /** Asigna todas las pendientes de una zona al recolector de esa zona (si existe). */
    public int generarRutasParaFechaZona(String fecha, String zona) {
        if (fecha == null || fecha.trim().isEmpty()) fecha = yyyymmdd(System.currentTimeMillis());
        if (zona == null) zona = "";

        Recolector r = db.recolectorDao().findByZona(zona);
        if (r == null) {
            List<Recolector> all = db.recolectorDao().listAll();
            if (all == null || all.isEmpty()) return 0;
            r = all.get(0);
        }
        return generarRutasParaFechaZonaForRecolector(fecha, zona,  r.id);
    }

    /** Asigna las pendientes de la zona/fecha al recolector indicado, ordenadas por proximidad. */
    public int generarRutasParaFechaZonaForRecolector(String fecha, String zona, int recolectorId) {
        if (fecha == null || fecha.trim().isEmpty()) fecha = yyyymmdd(System.currentTimeMillis());
        if (zona == null) zona = "";

        List<Solicitud> list = db.solicitudDao().listUnassignedByFechaZona(fecha, zona);
        android.util.Log.d("ASIG", "pendientes zona=" + zona + " fecha=" + fecha + " -> " + (list==null?0:list.size()));

        if (list == null || list.isEmpty()) return 0;

        List<Solicitud> withLL = new ArrayList<>();
        for (Solicitud s : list) {
            if (s != null && s.lat != null && s.lon != null && s.lat != 0 && s.lon != 0) withLL.add(s);
        }
        if (withLL.isEmpty()) return 0;

        List<Solicitud> orden = greedyOrder(withLL);

        final int[] nInserted = {0};

        // >>> variables efectivamente finales
        final String fFecha = fecha;
        final int fRecolectorId = recolectorId;

        db.runInTransaction(() -> {
            int ordenRuta = 1;
            List<Asignacion> nuevas = new ArrayList<>();
            for (Solicitud s : orden) {
                Asignacion a = new Asignacion();
                a.solicitudId  = (int) s.id;
                a.recolectorId = fRecolectorId;
                a.ordenRuta    = ordenRuta++;
                a.guiaActiva   = false;
                a.estado       = "ASIGNADA";
                a.fecha        = fFecha; // <-- asegúrate de tener este campo en la Entity
                nuevas.add(a);
            }
            db.asignacionDao().insertAll(nuevas);
            for (Solicitud s : orden) db.solicitudDao().asignar(s.id, fRecolectorId);
            nInserted[0] = nuevas.size();
        });
        android.util.Log.d("ASIG", "insertadas=" + nInserted[0] + " para recolector=" + fRecolectorId);

        NotificationHelper.showAssignments(
                context,
                "Asignaciones listas",
                "Se generaron " + nInserted[0] + " recolecciones para la zona " + zona + ".",
                ("asig_" + fecha + "_" + zona).hashCode()
        );
        NotificationHelper.notifyRecolector(
                context, fRecolectorId, zona, fFecha, nInserted[0]
        );
        return nInserted[0];
    }

    // ---- Orden greedy por proximidad ----
    private static List<Solicitud> greedyOrder(List<Solicitud> input) {
        List<Solicitud> pool = new ArrayList<>(input);
        List<Solicitud> out  = new ArrayList<>();
        if (pool.isEmpty()) return out;

        double cx=0, cy=0; int n=0;
        for (Solicitud s : pool) { cx += s.lat; cy += s.lon; n++; }
        double cLat = cx/n, cLon = cy/n;

        Solicitud current = pool.get(0);
        double best = Double.MAX_VALUE;
        for (Solicitud s : pool) {
            double d = dist(cLat,cLon,s.lat,s.lon);
            if (d < best) { best = d; current = s; }
        }
        out.add(current); pool.remove(current);

        while (!pool.isEmpty()) {
            Solicitud next = pool.get(0); double bestD = Double.MAX_VALUE;
            for (Solicitud s : pool) {
                double d = dist(current.lat,current.lon,s.lat,s.lon);
                if (d < bestD) { bestD = d; next = s; }
            }
            out.add(next); pool.remove(next); current = next;
        }
        return out;
    }

    private static double dist(double lat1, double lon1, double lat2, double lon2) {
        double R=6371.0;
        double dLat=Math.toRadians(lat2-lat1), dLon=Math.toRadians(lon2-lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    private static String yyyymmdd(long ms){
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(ms));
    }
}
