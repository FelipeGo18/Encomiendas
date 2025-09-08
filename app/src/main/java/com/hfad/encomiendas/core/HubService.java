package com.hfad.encomiendas.core;

import android.content.Context;
import android.text.TextUtils;

import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Manifiesto;
import com.hfad.encomiendas.data.ManifiestoDao;
import com.hfad.encomiendas.data.ManifiestoItem;
import com.hfad.encomiendas.data.Solicitud;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HubService {

    private final AppDatabase db;

    public HubService(Context ctx) {
        this.db = AppDatabase.getInstance(ctx);
    }

    /** Crea (si no existe) un manifiesto ABIERTO reciente y lo llena con las guías RECOLECTADAS sin clasificar.
     *  Devuelve el id del manifiesto que quedó con los ítems. */
    public int clasificarGuiasHoy() {
        ManifiestoDao dao = db.manifiestoDao();

        // 1) tomar el ABIERTO más reciente (si no hay, crear uno)
        Manifiesto abierto = dao.getUltimoAbierto();
        if (abierto == null) {
            long now = System.currentTimeMillis();
            Manifiesto m = new Manifiesto();
            m.codigo      = "M-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(new Date(now));
            m.fechaMillis = now;
            m.estado      = "ABIERTO";
            m.createdAt   = now;
            int mid = (int) dao.insertManifiesto(m);
            abierto = dao.getById(mid);
        }

        // 2) llenar ESTE manifiesto (no crear otro)
        fillManifiesto((int) abierto.id);
        return (int) abierto.id;
    }

    /** Inserta en el manifiesto indicado todas las solicitudes RECOLECTADAS que aún no estén en ningún manifiesto. */
    public int fillManifiesto(int manifiestoId) {
        ManifiestoDao dao = db.manifiestoDao();
        List<Solicitud> all = db.solicitudDao().listAllByUser(0); // usa tu query real si la tienes
        int added = 0;
        if (all != null) {
            for (Solicitud s : all) {
                if (!"RECOLECTADA".equalsIgnoreCase(s.estado)) continue;
                if (dao.existsBySolicitud(s.id) > 0) continue;

                ManifiestoItem it = new ManifiestoItem();
                it.manifiestoId     = manifiestoId;
                it.solicitudId      = s.id;
                it.guia             = s.guia;
                it.destinoCiudad    = meta(s.notas, "Destino",  meta(s.notas, "MunicipioDestino", "—"));
                it.destinoDireccion = meta(s.notas, "DestinoDir","—");
                it.otp              = generarOtp();
                it.estado           = "EN_HUB";
                it.createdAt        = System.currentTimeMillis();
                dao.insertItem(it);
                added++;
            }
        }
        return added;
    }

    /** Marca el manifiesto como DESPACHADO y los ítems EN_RUTA para el repartidor. */
    public void despacharManifiesto(int manifiestoId, String emailRepartidor) {
        ManifiestoDao dao = db.manifiestoDao();
        dao.despacharManifiesto(manifiestoId, System.currentTimeMillis(), emailRepartidor);
        dao.ponerItemsEnRuta(manifiestoId);
    }

    // ---------------- helpers ----------------
    private static String generarOtp() {
        int n = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(n);
    }
    private static String meta(String notas, String key, String def) {
        if (TextUtils.isEmpty(notas)) return def;
        String lower = notas.toLowerCase(Locale.ROOT);
        String k = (key + ":").toLowerCase(Locale.ROOT);
        int i = lower.indexOf(k);
        if (i < 0) return def;
        i += k.length();
        while (i < notas.length() && Character.isWhitespace(notas.charAt(i))) i++;
        int endDot = lower.indexOf(". ", i);
        int endBar = lower.indexOf(" | ", i);
        int end = (endDot < 0 && endBar < 0) ? notas.length()
                : (endDot < 0 ? endBar : (endBar < 0 ? endDot : Math.min(endDot, endBar)));
        String v = notas.substring(i, Math.max(i, end)).trim();
        return v.isEmpty() ? def : v;
    }
}
