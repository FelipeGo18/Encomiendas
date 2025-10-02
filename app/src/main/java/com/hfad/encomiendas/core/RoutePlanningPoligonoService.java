package com.hfad.encomiendas.core;

import android.content.Context;

import com.google.android.gms.maps.model.LatLng;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.Zone;

import java.util.ArrayList;
import java.util.List;

/** Servicio para construir una ruta optimizada tomando las solicitudes pendientes dentro
 * de un polígono de zona (ya guardado en DB). */
public class RoutePlanningPoligonoService {

    public static class PlanResult {
        public final List<Solicitud> solicitudesDentro; // crudo dentro polígono
        public final List<Solicitud> ordenOptimizado;   // optimizado
        public final double distanciaTotalM;
        public PlanResult(List<Solicitud> dentro, List<Solicitud> orden, double dist) {
            this.solicitudesDentro = dentro; this.ordenOptimizado = orden; this.distanciaTotalM = dist; }
    }

    private final AppDatabase db;

    public RoutePlanningPoligonoService(Context ctx){ this.db = AppDatabase.getInstance(ctx); }

    public PlanResult plan(String fecha, long zoneId){
        Zone z = db.zoneDao().getById(zoneId);
        if (z == null) return new PlanResult(new ArrayList<>(), new ArrayList<>(), 0);
        List<LatLng> poly = GeoUtils.jsonToPolygon(z.polygonJson);
        if (poly.size() < 3) return new PlanResult(new ArrayList<>(), new ArrayList<>(), 0);
        List<Solicitud> all = db.solicitudDao().listUnassignedByFecha(fecha);
        List<Solicitud> dentro = new ArrayList<>();
        for (Solicitud s : all) {
            if (s.lat == null || s.lon == null) continue;
            if (GeoUtils.pointInPolygon(s.lat, s.lon, poly)) dentro.add(s);
        }
        if (dentro.isEmpty()) return new PlanResult(dentro, new ArrayList<>(), 0);
        List<Solicitud> orden = GeoUtils.optimizeRoute(dentro);
        double dist = GeoUtils.pathDistance(orden);
        return new PlanResult(dentro, orden, dist);
    }
}

