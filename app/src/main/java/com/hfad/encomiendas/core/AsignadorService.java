package com.hfad.encomiendas.core;

import android.content.Context;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Asignacion;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.data.Recolector;
import com.hfad.encomiendas.data.RecolectorDao;
import com.hfad.encomiendas.data.Solicitud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AsignadorService {

    private final AppDatabase db;

    public AsignadorService(Context ctx) {
        this.db = AppDatabase.getInstance(ctx);
    }

    /** Asigna todas las solicitudes pendientes de una fecha (todas las zonas). */
    public int generarRutasParaFecha(String fecha) {
        List<Solicitud> pendientes = db.solicitudDao().listUnassignedByFechaZona(fecha, "");
        return asignarLote(pendientes, fecha);
    }

    /** Asigna solo las solicitudes pendientes de una zona (zona = barrio/localidad). */
    public int generarRutasParaFechaZona(String fecha, String zona) {
        List<Solicitud> pendientes = db.solicitudDao().listUnassignedByFechaZona(fecha, zona);
        return asignarLote(pendientes, fecha);
    }

    // ---------- Núcleo de asignación ----------
    private int asignarLote(List<Solicitud> solicitudes, String fecha) {
        if (solicitudes == null || solicitudes.isEmpty()) return 0;

        RecolectorDao rdao = db.recolectorDao();
        AsignacionDao adao = db.asignacionDao();

        List<Recolector> recolectores = rdao.listAll();
        if (recolectores == null) recolectores = new ArrayList<>();

        // Tomamos solo activos
        List<Recolector> activos = new ArrayList<>();
        for (Recolector r : recolectores) {
            if (r != null && r.activo) activos.add(r);
        }
        if (activos.isEmpty()) return 0;

        int creadas = 0;
        for (Solicitud s : solicitudes) {
            Recolector best = pickRecolector(activos, s);
            if (best == null) continue;

            Integer orden = siguienteOrden(fecha, best.id);

            Asignacion a = new Asignacion();
            a.solicitudId  = (int) s.id;
            a.recolectorId = best.id;
            a.fecha        = fecha;
            a.estado       = "ASIGNADA";
            a.ordenRuta    = orden;
            a.createdAt    = System.currentTimeMillis();

            adao.insert(a);

            // También reflejamos en la solicitud
            db.solicitudDao().asignar(s.id, best.id);

            creadas++;
        }
        return creadas;
    }

    private Integer siguienteOrden(String fecha, int recolectorId) {
        List<Asignacion> actuales = db.asignacionDao().listByRecolectorAndFecha(recolectorId, fecha);
        int max = 0;
        if (actuales != null) {
            for (Asignacion a : actuales) {
                if (a.ordenRuta != null && a.ordenRuta > max) max = a.ordenRuta;
            }
        }
        return max + 1;
    }

    /** Score por municipio + BARRIO/LOCALIDAD (no "tipo de zona") + vehículo vs tamaño. */
    private Recolector pickRecolector(List<Recolector> recs, Solicitud s) {
        if (recs.isEmpty()) return null;

        final String municipioS = extraerCampo(s.notas, "Municipio");
        // Para zona de compatibilidad usamos el BARRIO/LOCALIDAD/VEREDA:
        final String zonaS      = firstNonEmpty(
                extraerCampo(s.notas, "Barrio"),
                extraerCampo(s.notas, "Localidad"),
                extraerCampo(s.notas, "Vereda")
        );
        final String tamanoS    = mapTamanoDesdeTipo(s.tipoPaquete);

        recs.sort(new Comparator<Recolector>() {
            @Override public int compare(Recolector r1, Recolector r2) {
                int s1 = score(r1);
                int s2 = score(r2);
                if (s1 != s2) return Integer.compare(s2, s1);
                return Integer.compare(r1.cargaActual, r2.cargaActual);
            }

            private int score(Recolector r) {
                int sc = 0;
                if (eq(municipioS, r.municipio)) sc += 1;
                if (eq(zonaS, r.zona))           sc += 1;
                if (vehOk(r.vehiculo, tamanoS))  sc += 1;
                return sc;
            }

            private boolean eq(String a, String b) {
                if (a == null || b == null) return false;
                return a.trim().equalsIgnoreCase(b.trim());
            }

            private boolean vehOk(String vehiculo, String tam) {
                if (vehiculo == null || tam == null) return true;
                String v = vehiculo.toUpperCase(Locale.ROOT);
                String t = tam.toUpperCase(Locale.ROOT).replace("Ñ", "N");
                switch (v) {
                    case "BICI":       return t.equals("SOBRE") || t.equals("PEQUENO");
                    case "MOTO":       return !t.equals("VOLUMINOSO");
                    case "CARRO":      return !t.equals("VOLUMINOSO");
                    case "CAMIONETA":  return true;
                    default:           return true;
                }
            }
        });

        for (Recolector r : recs) {
            if (r.capacidad > 0 && r.cargaActual >= r.capacidad) continue;
            return r;
        }
        return recs.get(0);
    }

    // ---------- Utils ----------
    /** Notas separadas por " | " (ej: "Barrio: Chico | DestinoDir: Av 7 #...") */
    private static String extraerCampo(String notas, String campo) {
        if (notas == null) return null;
        String txtLower = notas.toLowerCase(Locale.ROOT);
        String key = (campo + ": ").toLowerCase(Locale.ROOT);

        int start = txtLower.indexOf(key);
        if (start < 0) return null;
        start += key.length();

        // Cortar al siguiente separador " | " si existe; si no, hasta fin
        int rel = txtLower.indexOf(" | ", start);
        int end = (rel >= 0) ? rel : notas.length();

        return notas.substring(start, end).trim();
    }

    private static String firstNonEmpty(String... arr) {
        if (arr == null) return null;
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    /** Mapea tipo de paquete → tamaño para compatibilidad de vehículo. */
    private static String mapTamanoDesdeTipo(String tipoPaquete) {
        if (tipoPaquete == null) return null;
        String t = tipoPaquete.toLowerCase(Locale.ROOT);
        if (t.contains("documento") || t.contains("sobre")) return "SOBRE";
        if (t.contains("peque"))     return "PEQUENO";
        if (t.contains("mediano"))   return "MEDIANO";
        if (t.contains("grande"))    return "GRANDE";
        if (t.contains("fragil") || t.contains("frágil")) return "MEDIANO";
        return "MEDIANO";
    }
}
