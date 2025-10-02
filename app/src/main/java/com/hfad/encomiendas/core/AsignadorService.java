package com.hfad.encomiendas.core;

import android.content.Context;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Asignacion;
import com.hfad.encomiendas.data.Manifiesto;
import com.hfad.encomiendas.data.ManifiestoItem;
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

    /** Asigna las pendientes de la zona/fecha al recolector indicado, ordenadas por proximidad (greedy + 2-opt). */
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

        // Orden inicial greedy
        List<Solicitud> orden = greedyOrder(withLL);
        // Optimización ligera 2-opt
        orden = twoOptImprove(orden, 40);
        final List<Solicitud> finalOrden = orden; // hacer efectivamente final para la lambda

        final int[] nInserted = {0};
        final String fFecha = fecha;
        final int fRecolectorId = recolectorId;

        db.runInTransaction(() -> {
            int ordenRuta = 1;
            List<Asignacion> nuevas = new ArrayList<>();
            for (Solicitud s : finalOrden) {
                Asignacion a = new Asignacion();
                a.solicitudId  = (int) s.id;
                a.recolectorId = fRecolectorId;
                a.ordenRuta    = ordenRuta++;
                a.guiaActiva   = false;
                a.estado       = "ASIGNADA";
                a.fecha        = fFecha;
                a.createdAt    = System.currentTimeMillis();
                nuevas.add(a);
            }
            db.asignacionDao().insertAll(nuevas);
            for (Solicitud s : finalOrden) {
                db.solicitudDao().asignar(s.id, fRecolectorId);

                // CREAR TRACKING EVENT INICIAL AUTOMÁTICAMENTE CON UBICACIÓN REAL DEL RECOLECTOR
                // Esto garantiza que el remitente vea inmediatamente la ubicación REAL del recolector
                if (s.lat != null && s.lon != null) {
                    com.hfad.encomiendas.data.TrackingEvent trackingInicial = new com.hfad.encomiendas.data.TrackingEvent();
                    trackingInicial.shipmentId = s.id;

                    // OBTENER LA UBICACIÓN REAL DEL RECOLECTOR (NO SIMULADA)
                    Recolector recolector = db.recolectorDao().getById(fRecolectorId);
                    Double latRecolector = null;
                    Double lonRecolector = null;

                    if (recolector != null && recolector.lat != null && recolector.lon != null) {
                        // USAR LA UBICACIÓN REAL DEL RECOLECTOR ALMACENADA EN LA BD
                        latRecolector = recolector.lat;
                        lonRecolector = recolector.lon;

                        android.util.Log.d("ASIG", "✓ Usando ubicación REAL del recolector " + recolector.nombre +
                            ": " + latRecolector + "," + lonRecolector +
                            " (última actualización: " + (recolector.lastSeenMillis != null ?
                                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                    .format(new java.util.Date(recolector.lastSeenMillis)) : "desconocida") + ")");
                    } else {
                        // FALLBACK: Si no hay ubicación real, usar ubicación cerca del destino
                        double[] ubicacionFallback = generarUbicacionRecolectorInicial(s.lat, s.lon);
                        latRecolector = ubicacionFallback[0];
                        lonRecolector = ubicacionFallback[1];

                        android.util.Log.w("ASIG", "⚠ Recolector sin ubicación real, usando fallback cerca del destino: " +
                            latRecolector + "," + lonRecolector);
                    }

                    trackingInicial.lat = latRecolector;
                    trackingInicial.lon = lonRecolector;
                    trackingInicial.type = "ASIGNADO";
                    trackingInicial.detail = "Recolector asignado - " +
                        (recolector != null ? recolector.nombre : "Recolector") +
                        " se dirige a recoger el paquete";

                    // TIMESTAMP PRECISO
                    long ahora = System.currentTimeMillis();
                    trackingInicial.occurredAt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
                            .format(new java.util.Date(ahora));

                    // Insertar el evento de tracking
                    long trackingId = db.trackingEventDao().insert(trackingInicial);

                    android.util.Log.d("ASIG", "✓ Tracking inicial creado (ID: " + trackingId + ") para solicitud " + s.id +
                            " - Recolector ubicado en: " + trackingInicial.lat + "," + trackingInicial.lon +
                            " - Timestamp: " + trackingInicial.occurredAt);
                }
            }
            nInserted[0] = nuevas.size();
        });
        android.util.Log.d("ASIG", "insertadas=" + nInserted[0] + " para recolector=" + fRecolectorId);

        com.hfad.encomiendas.core.NotificationHelper.showAssignments(
                context,
                "Asignaciones listas",
                "Se generaron " + nInserted[0] + " recolecciones para la zona " + zona + ".",
                ("asig_" + fecha + "_" + zona).hashCode()
        );
        com.hfad.encomiendas.core.NotificationHelper.notifyRecolector(
                context, fRecolectorId, zona, fFecha, nInserted[0]
        );
        return nInserted[0];
    }

    private String generarCodigoManifiestoUnico() {
        String base = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(new java.util.Date());
        int intento = 1;
        while (intento < 10_000) {
            String codigo = "M-" + base + "-" + String.format(java.util.Locale.getDefault(), "%04d", intento);
            if (db.manifiestoDao().countByCodigo(codigo) == 0) return codigo;
            intento++;
        }
        return "M-" + base + "-XXXX";
    }

    /**
     * Asigna una ruta ya ordenada (lista de solicitudes) al recolector indicado.
     * Además crea un Manifiesto y sus ManifiestoItems para persistir la ruta.
     */
    public int assignRutaOrdenada(String fecha,
                                  int recolectorId,
                                  List<Solicitud> orden,
                                  Long horaInicioMillisOpt,
                                  boolean notificarRuta) {
        if (orden == null || orden.isEmpty()) return 0;
        final String fFecha = (fecha == null || fecha.trim().isEmpty()) ? yyyymmdd(System.currentTimeMillis()) : fecha;
        final int fRecolector = recolectorId;
        final List<Solicitud> copia = new ArrayList<>();
        for (Solicitud s : orden) {
            if (s != null && s.lat != null && s.lon != null && s.lat != 0 && s.lon != 0) copia.add(s);
        }
        if (copia.isEmpty()) return 0;

        double distanciaTotalM = com.hfad.encomiendas.core.GeoUtils.pathDistance(copia);
        String polyline = com.hfad.encomiendas.core.GeoUtils.encodePolyline(copia);
        long startPlan = (horaInicioMillisOpt == null) ? (System.currentTimeMillis() + 10 * 60_000) : horaInicioMillisOpt;
        final int[] inserted = {0};
        final int[] manifiestoId = {0};

        db.runInTransaction(() -> {
            // Crear Manifiesto
            Manifiesto m = new Manifiesto();
            m.codigo = generarCodigoManifiestoUnico();
            m.fechaMillis = System.currentTimeMillis();
            m.zoneId = null; // si se asigna a zona polígono, establecer antes de llamar
            m.paradas = copia.size();
            m.distanciaTotalM = (int) Math.round(distanciaTotalM);
            // duración heurística: 3 min por parada + 1 min cada 800m
            int duracion = (int) (copia.size() * 3 + (distanciaTotalM / 800.0));
            m.duracionEstimadaMin = duracion;
            m.polylineEncoded = polyline;
            m.horaInicioPlanificada = startPlan;
            m.estado = "ABIERTO";
            m.createdAt = System.currentTimeMillis();
            manifiestoId[0] = (int) db.manifiestoDao().insertManifiesto(m);

            // Crear items manifiesto + asignaciones legacy
            int idx = 1;
            List<Asignacion> nuevas = new ArrayList<>();
            List<ManifiestoItem> items = new ArrayList<>();
            for (Solicitud s : copia) {
                Asignacion a = new Asignacion();
                a.solicitudId = (int) s.id;
                a.recolectorId = fRecolector;
                a.fecha = fFecha;
                a.estado = "ASIGNADA";
                a.ordenRuta = idx;
                a.guiaActiva = false;
                a.createdAt = System.currentTimeMillis();
                nuevas.add(a);

                ManifiestoItem mi = new ManifiestoItem();
                mi.manifiestoId = manifiestoId[0];
                mi.solicitudId = s.id;
                mi.orden = idx;
                mi.estado = "EN_HUB"; // estado inicial
                mi.createdAt = System.currentTimeMillis();
                items.add(mi);
                idx++;
            }
            db.asignacionDao().insertAll(nuevas);
            db.manifiestoDao().insertItems(items);
            for (Solicitud s : copia) db.solicitudDao().asignar(s.id, fRecolector);
            inserted[0] = nuevas.size();
        });

        if (notificarRuta && inserted[0] > 0) {
            com.hfad.encomiendas.core.NotificationHelper.notifyRecolectorRutaEnriquecida(
                    context,
                    recolectorId,
                    manifiestoId[0],
                    distanciaTotalM,
                    startPlan,
                    inserted[0]
            );
        }
        return inserted[0];
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

    // ---- 2-Opt mejora local de la ruta ----
    private static List<Solicitud> twoOptImprove(List<Solicitud> route, int maxIterations) {
        if (route.size() < 4) return route; // nada que optimizar seriamente
        List<Solicitud> best = new ArrayList<>(route);
        double bestDist = totalDistance(best);
        boolean improved;
        int iter = 0;
        do {
            improved = false;
            for (int i = 1; i < best.size() - 2; i++) {
                for (int k = i + 1; k < best.size() - 1; k++) {
                    List<Solicitud> candidate = twoOptSwap(best, i, k);
                    double candDist = totalDistance(candidate);
                    if (candDist + 0.0001 < bestDist) { // margen flotante
                        best = candidate;
                        bestDist = candDist;
                        improved = true;
                    }
                }
            }
            iter++;
        } while (improved && iter < maxIterations);
        return best;
    }

    private static List<Solicitud> twoOptSwap(List<Solicitud> route, int i, int k) {
        List<Solicitud> out = new ArrayList<>();
        // 0..i-1 igual
        for (int c = 0; c < i; c++) out.add(route.get(c));
        // i..k invertido
        for (int c = k; c >= i; c--) out.add(route.get(c));
        // k+1 .. end igual
        for (int c = k + 1; c < route.size(); c++) out.add(route.get(c));
        return out;
    }

    private static double totalDistance(List<Solicitud> list) {
        double sum = 0.0;
        for (int i = 0; i < list.size() - 1; i++) {
            Solicitud a = list.get(i);
            Solicitud b = list.get(i + 1);
            sum += dist(a.lat, a.lon, b.lat, b.lon);
        }
        return sum;
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

    /**
     * Genera una ubicación mejorada para el recolector, teniendo en cuenta su ubicación actual si está disponible.
     * Se espera que esta ubicación sea realista y cerca del destino pero no exactamente en él.
     */
    private double[] generarUbicacionRecolectorMejorada(double latDestino, double lonDestino, Recolector recolector) {
        if (recolector != null && recolector.lat != null && recolector.lon != null) {
            // Si el recolector tiene una ubicación conocida, usarla como base
            // Pero moverlo un poco hacia el destino para simular que se está dirigiendo allí
            double distanciaAlDestino = dist(recolector.lat, recolector.lon, latDestino, lonDestino);

            if (distanciaAlDestino < 5.0) { // Si está a menos de 5km del destino
                // Posicionar entre su ubicación actual y el destino (75% del camino hacia el destino)
                double latRecolector = recolector.lat + (latDestino - recolector.lat) * 0.75;
                double lonRecolector = recolector.lon + (lonDestino - recolector.lon) * 0.75;
                return new double[]{latRecolector, lonRecolector};
            } else {
                // Si está muy lejos, usar su ubicación actual pero con pequeño desplazamiento hacia el destino
                double factor = 0.001; // ~100 metros hacia el destino
                double latRecolector = recolector.lat + (latDestino - recolector.lat) * factor;
                double lonRecolector = recolector.lon + (lonDestino - recolector.lon) * factor;
                return new double[]{latRecolector, lonRecolector};
            }
        } else {
            // Si no tiene ubicación conocida, usar método de ubicación inicial cerca del destino
            return generarUbicacionRecolectorInicial(latDestino, lonDestino);
        }
    }

    /**
     * Genera una ubicación simulada para el recolector al momento de la asignación.
     * Se espera que esté cerca del destino, pero no en el destino exacto.
     */
    private double[] generarUbicacionRecolectorInicial(double latDestino, double lonDestino) {
        // Generar una ubicación en un radio de 200-800 metros del destino
        double anguloAleatorio = Math.random() * 2 * Math.PI; // Ángulo aleatorio
        double distanciaAleatoria = 0.002 + (Math.random() * 0.006); // Entre 200m y 800m aproximadamente

        double latRecolector = latDestino + (distanciaAleatoria * Math.cos(anguloAleatorio));
        double lonRecolector = lonDestino + (distanciaAleatoria * Math.sin(anguloAleatorio));

        return new double[]{latRecolector, lonRecolector};
    }

    /**
     * Simula el movimiento del recolector hacia el destino, generando una ubicación intermedia.
     * Se utiliza para crear un evento de tracking "EN_RUTA" que muestre el progreso hacia el destino.
     */
    private double[] simularMovimientoHaciaDestino(double latInicio, double lonInicio, double latDestino, double lonDestino, double porcentaje) {
        double latIntermedia = latInicio + (latDestino - latInicio) * porcentaje;
        double lonIntermedia = lonInicio + (lonDestino - lonInicio) * porcentaje;
        return new double[]{latIntermedia, lonIntermedia};
    }
}
