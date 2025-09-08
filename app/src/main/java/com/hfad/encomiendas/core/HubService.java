package com.hfad.encomiendas.core;

import android.content.Context;
import android.text.TextUtils;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Manifiesto;
import com.hfad.encomiendas.data.ManifiestoDao;
import com.hfad.encomiendas.data.ManifiestoItem;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.SolicitudDao;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Servicio de HUB:
 * - Clasificar guías RECOLECTADAS del día creando un Manifiesto ABIERTO
 * - Despachar manifiestos (asignar repartidor y poner ítems EN_RUTA)
 * - Helpers para obtener manifiesto por id o el último ABIERTO SIN tocar el DAO
 */
public class HubService {

    private final AppDatabase db;

    public HubService(Context ctx) {
        this.db = AppDatabase.getInstance(ctx.getApplicationContext());
    }

    /** Crea un manifiesto ABIERTO hoy y agrega todas las Solicitudes RECOLECTADAS de hoy que aún no estén en items. */
    public int clasificarGuiasHoy() {
        final long now = System.currentTimeMillis();

        ManifiestoDao mdao = db.manifiestoDao();
        Manifiesto m = new Manifiesto();
        m.codigo      = "M-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
                .format(new Date(now));
        m.fechaMillis = now;
        m.estado      = "ABIERTO";
        m.createdAt   = now;
        final int mid = (int) mdao.insertManifiesto(m);

        // Traer solicitudes del día (ajusta si usas otro query).
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SolicitudDao sdao = db.solicitudDao();
        List<Solicitud> lista = sdao.listByFecha(hoy);

        int agregadas = 0;
        if (lista != null) {
            for (Solicitud s : lista) {
                if (s == null) continue;
                if (TextUtils.isEmpty(s.estado) || !s.estado.equalsIgnoreCase("RECOLECTADA")) continue;
                if (mdao.existsBySolicitud(s.id) > 0) continue; // ya clasificada antes en algún manifiesto

                String destinoCiudad = meta(s.notas, "Destino",
                        meta(s.notas, "MunicipioDestino", "—"));
                String destinoDir    = meta(s.notas, "DestinoDir", "—");

                ManifiestoItem it = new ManifiestoItem();
                it.manifiestoId     = mid;
                it.solicitudId      = s.id;
                it.guia             = s.guia;
                it.destinoCiudad    = isEmpty(destinoCiudad) ? "—" : destinoCiudad.trim();
                it.destinoDireccion = isEmpty(destinoDir)    ? "—" : destinoDir.trim();
                it.otp              = generarOtp();
                it.estado           = "EN_HUB";
                it.createdAt        = System.currentTimeMillis();

                mdao.insertItem(it);
                agregadas++;
            }
        }
        return agregadas;
    }

    /** Despacha un manifiesto: asigna repartidor, marca manifiesto DESPACHADO y pone todos sus ítems EN_RUTA. */
    public void despacharManifiesto(int manifiestoId, String repartidorEmail) {
        ManifiestoDao dao = db.manifiestoDao();
        long ts = System.currentTimeMillis();
        dao.despacharManifiesto(manifiestoId, ts, repartidorEmail);
        dao.ponerItemsEnRuta(manifiestoId);
    }

    /* ========================= Helpers públicos opcionales ========================= */

    /** Devuelve el último manifiesto ABIERTO (o null si no hay). No requiere métodos extra en el DAO. */
    public Manifiesto getUltimoAbierto() {
        List<Manifiesto> lista = db.manifiestoDao().listAbiertosODespachados();
        if (lista == null || lista.isEmpty()) return null;
        for (Manifiesto m : lista) {
            if (m != null && "ABIERTO".equalsIgnoreCase(m.estado)) return m;
        }
        return null;
    }

    /** Busca un manifiesto por id recorriendo la lista disponible del DAO. */
    public Manifiesto getById(int id) {
        List<Manifiesto> lista = db.manifiestoDao().listAbiertosODespachados();
        if (lista == null) return null;
        for (Manifiesto m : lista) {
            if (m != null && m.id == id) return m;
        }
        return null;
    }

    /* ========================= Utilidades internas ========================= */

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Extrae "Key: valor" desde un campo de notas; se detiene en ". ", " | " o fin. */
    private static String meta(String notas, String key, String def) {
        if (isEmpty(notas)) return def;
        String lower = notas.toLowerCase(Locale.ROOT);
        String k = (key + ":").toLowerCase(Locale.ROOT);
        int i = lower.indexOf(k);
        if (i < 0) return def;
        i += k.length();
        // saltar espacios tras "key:"
        while (i < notas.length() && Character.isWhitespace(notas.charAt(i))) i++;
        int endDot = lower.indexOf(". ", i);
        int endBar = lower.indexOf(" | ", i);
        int end = (endDot < 0 && endBar < 0) ? notas.length()
                : (endDot < 0 ? endBar : (endBar < 0 ? endDot : Math.min(endDot, endBar)));
        String v = notas.substring(i, Math.max(i, end)).trim();
        return isEmpty(v) ? def : v;
    }

    private static String generarOtp() {
        int n = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(n);
    }


    public int clasificarGuiasPorDestino() {
        ManifiestoDao mdao = db.manifiestoDao();
        SolicitudDao  sdao = db.solicitudDao();

        // 1) Trae solicitudes RECOLECTADAS que aún no estén en ningún manifiesto
        //    (asumiendo que tienes listByFecha para hoy; si no, usa listAll y filtra estado)
        String hoy = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
        java.util.List<Solicitud> candidatas = sdao.listByFecha(hoy);
        if (candidatas == null) candidatas = new java.util.ArrayList<>();

        // 2) Agrupa por ciudad de destino (key)
        java.util.Map<String, java.util.List<Solicitud>> porDestino = new java.util.LinkedHashMap<>();
        for (Solicitud s : candidatas) {
            if (!"RECOLECTADA".equalsIgnoreCase(s.estado)) continue;
            // si ya está en un manifiesto, sáltala
            if (mdao.existsBySolicitud(s.id) > 0) continue;

            String destino = meta(s.notas, "MunicipioDestino",
                    meta(s.notas, "Destino", null));
            if (destino == null || destino.trim().isEmpty()) {
                // fallback: si no hay destino, intenta marcar URBANO con el Origen
                destino = "URBANO-" + meta(s.notas, "Origen", "CIUDAD_DESCONOCIDA");
            }
            destino = destino.trim();

            porDestino.computeIfAbsent(destino, k -> new java.util.ArrayList<>()).add(s);
        }

        int totalInsertados = 0;

        // 3) Por cada ciudad destino, crear 1 manifiesto ABIERTO y agregar ítems
        long ahora = System.currentTimeMillis();
        for (java.util.Map.Entry<String, java.util.List<Solicitud>> e : porDestino.entrySet()) {
            String ciudad = e.getKey();
            java.util.List<Solicitud> lote = e.getValue();
            if (lote.isEmpty()) continue;

            // crea manifiesto
            Manifiesto m = new Manifiesto();
            m.codigo = "M-" + ciudad.replaceAll("\\s+", "_") + "-" +
                    new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.getDefault())
                            .format(new java.util.Date(ahora));
            m.fechaMillis = ahora;
            m.estado = "ABIERTO";
            m.createdAt = ahora;
            int mid = (int) mdao.insertManifiesto(m);

            // mete ítems
            for (Solicitud s : lote) {
                ManifiestoItem it = new ManifiestoItem();
                it.manifiestoId = mid;
                it.solicitudId = s.id;
                it.guia = s.guia;
                it.destinoCiudad = ciudad;
                it.destinoDireccion = meta(s.notas, "DestinoDir", "—");
                it.otp = String.valueOf((int)(Math.random()*900000)+100000);
                it.estado = "EN_HUB";
                it.createdAt = System.currentTimeMillis();
                mdao.insertItem(it);
                totalInsertados++;
            }
        }

        return totalInsertados;
    }


}