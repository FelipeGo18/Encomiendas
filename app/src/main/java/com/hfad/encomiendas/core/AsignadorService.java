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

public class AsignadorService {

    private final AppDatabase db;

    public AsignadorService(Context ctx) {
        this.db = AppDatabase.getInstance(ctx);
    }

    /** Asigna todas las solicitudes pendientes de una fecha (todas las zonas). */
    public int generarRutasParaFecha(String fecha) {
        // Trae pendientes sin asignar para esa fecha, sin filtrar por zona
        List<Solicitud> pendientes = db.solicitudDao().listUnassignedByFechaZona(fecha, "");
        return asignarLote(pendientes, fecha);
    }

    /** Asigna solo las solicitudes pendientes de una zona. */
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
            a.solicitudId  = s.id;
            a.recolectorId = best.id;
            a.fecha        = fecha;
            a.estado       = "ASIGNADA";
            a.ordenRuta    = orden;
            a.createdAt    = System.currentTimeMillis();
            a.otp          = null; // ya no usamos OTP para activar guía

            adao.insert(a);
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

    private Recolector pickRecolector(List<Recolector> recs, Solicitud s) {
        if (recs.isEmpty()) return null;

        // Ordenar por mejor compatibilidad (zona/municipio/vehículo) y menor carga
        recs.sort(new Comparator<Recolector>() {
            @Override public int compare(Recolector r1, Recolector r2) {
                int s1 = score(r1, s);
                int s2 = score(r2, s);
                if (s1 != s2) return Integer.compare(s2, s1); // mayor score primero
                return Integer.compare(r1.cargaActual, r2.cargaActual); // menos carga primero
            }

            private int score(Recolector r, Solicitud s) {
                int sc = 0;
                if (eq(s.municipio, r.municipio)) sc += 1;
                if (eq(s.barrioVereda, r.zona))    sc += 1;
                if (vehOk(r.vehiculo, s.tamanoPaquete)) sc += 1;
                return sc;
            }

            private boolean eq(String a, String b) {
                if (a == null || b == null) return false;
                return a.trim().equalsIgnoreCase(b.trim());
            }

            private boolean vehOk(String vehiculo, String tam) {
                if (vehiculo == null || tam == null) return true;
                String v = vehiculo.toUpperCase();
                String t = tam.toUpperCase().replace("Ñ", "N");
                switch (v) {
                    case "BICI":       return t.equals("SOBRE") || t.equals("PEQUENO");
                    case "MOTO":       return !t.equals("VOLUMINOSO");
                    case "CARRO":      return !t.equals("VOLUMINOSO");
                    case "CAMIONETA":  return true;
                    default:           return true;
                }
            }
        });

        // Devuelve el primero con cupo
        for (Recolector r : recs) {
            if (r.capacidad > 0 && r.cargaActual >= r.capacidad) continue;
            return r;
        }
        return recs.get(0);
    }
}
