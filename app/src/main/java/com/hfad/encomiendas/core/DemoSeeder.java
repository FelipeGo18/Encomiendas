package com.hfad.encomiendas.core;

import android.content.Context;
import android.util.Log;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Asignacion;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.SolicitudDao;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DemoSeeder {

    private static final String TAG = "DemoSeeder";

    private DemoSeeder() {}

    public static void seed(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        SolicitudDao sdao = db.solicitudDao();

        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // Si hoy no hay solicitudes, creamos 3 de demo
        List<Solicitud> existentes = sdao.listByFecha(hoy);
        if (existentes == null || existentes.isEmpty()) {
            Log.d(TAG, "Sembrando solicitudes demo para " + hoy);

            insertarSolicitud(sdao,
                    "Paquete", "Bogotá", "Medellín", "Efectivo", 120_000d,   // <-- Double (d)
                    "Bogotá", "Calle 123 #45-67 Apto 101", "Chapinero",
                    "MEDIANO",                                            // <-- tamaño
                    hoy, "09:00", "10:00");

            insertarSolicitud(sdao,
                    "Documento", "Bogotá", "Bogotá", "Tarjeta", 0d,        // <-- Double
                    "Bogotá", "Carrera 7 #45-10 Oficina 301", "Teusaquillo",
                    "SOBRE",
                    hoy, "10:00", "11:00");

            insertarSolicitud(sdao,
                    "Paquete", "Medellín", "Bogotá", "Efectivo", 80_000d,   // <-- Double
                    "Medellín", "Cl 30 #55-10 Casa 2", "Belén",
                    "GRANDE",
                    hoy, "13:00", "14:00");
        } else {
            Log.d(TAG, "Ya existen solicitudes para hoy (" + existentes.size() + ")");
        }

        // Generar asignaciones automáticamente para hoy
        AsignadorService svc = new AsignadorService(ctx);
        int n = svc.generarRutasParaFecha(hoy);
        Log.d(TAG, "Asignaciones generadas hoy: " + n);

        // (Opcional) Log de asignaciones del recolector #1 para facilitar pruebas
        AsignacionDao adao = db.asignacionDao();
        List<Asignacion> asignacionesReco1 = adao.listByRecolectorAndFecha(1, hoy);
        for (Asignacion a : asignacionesReco1) {
            Log.d(TAG, "Asignación #" + a.id + " estado=" + a.estado + " orden=" + a.ordenRuta);
        }
    }

    private static void insertarSolicitud(SolicitudDao dao,
                                          String tipo, String origen, String destino, String formaPago, Double valorDec,
                                          String municipio, String direccion, String barrioVereda,
                                          String tamanoPaquete,
                                          String fecha, String desde, String hasta) {
        Solicitud s = new Solicitud();
        s.tipoProducto   = tipo;
        s.ciudadOrigen   = origen;
        s.ciudadDestino  = destino;
        s.formaPago      = formaPago;
        s.valorDeclarado = valorDec;          // Double en entidad

        s.tamanoPaquete  = tamanoPaquete;     // NUEVO tamaño

        s.municipio      = municipio;         // clave para el asignador
        s.direccion      = direccion;
        s.barrioVereda   = barrioVereda;

        s.fecha          = fecha;
        s.horaDesde      = desde;
        s.horaHasta      = hasta;

        s.createdAt      = System.currentTimeMillis(); // long

        dao.insert(s);
    }
}
