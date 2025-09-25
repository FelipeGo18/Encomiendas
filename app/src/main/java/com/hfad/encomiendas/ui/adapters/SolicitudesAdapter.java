package com.hfad.encomiendas.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.SolicitudDao;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Adapter para el panel del remitente que muestra solicitudes con ETA.
 * Nota: ahora el dataset es List<SolicitudDao.SolicitudConEta> (proyección con LEFT JOIN a eta_cache).
 * El Listener se mantiene igual y te entrega la Solicitud original (item.s).
 */
public class SolicitudesAdapter extends RecyclerView.Adapter<SolicitudesAdapter.VH> {

    public interface Listener {
        void onClick(Solicitud s);
    }

    private final Listener listener;
    private final List<SolicitudDao.SolicitudConEta> data = new ArrayList<>();

    public SolicitudesAdapter(Listener l) { this.listener = l; }

    /** Reemplaza tu setData(List<Solicitud>) por este que recibe la proyección con ETA */
    public void setData(List<SolicitudDao.SolicitudConEta> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_solicitud, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SolicitudDao.SolicitudConEta it = data.get(pos);
        Solicitud s = it.s;

        String titulo = safe(s.guia) + "  •  " + safe(s.estado);
        h.tvTitulo.setText(titulo);

        String sub = formatRange(s.ventanaInicioMillis, s.ventanaFinMillis);
        h.tvSub.setText(sub);

        // NUEVO: ETA (amigable). Si no hay, muestra —
        String etaPretty = prettyEta(it.eta);
        h.tvEta.setText("ETA: " + etaPretty);

        h.tvDir.setText(safe(s.direccion));

        String destino = parseFromNotes(s.notas, "DestinoDir");
        h.tvDestino.setText("Destino: " + (isEmpty(destino) ? "—" : destino));

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(s);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitulo, tvSub, tvEta, tvDir, tvDestino; // ← agregado tvEta
        VH(@NonNull View v) {
            super(v);
            tvTitulo  = v.findViewById(R.id.tvTitulo);
            tvSub     = v.findViewById(R.id.tvSub);
            tvEta     = v.findViewById(R.id.tvEta);      // ← asegúrate de tener este id en el layout
            tvDir     = v.findViewById(R.id.tvDir);
            tvDestino = v.findViewById(R.id.tvDestino);
        }
    }

    // ------- helpers (reusamos los tuyos) -------
    private static String formatRange(Long ini, Long fin) {
        if (ini == null || fin == null || ini == 0 || fin == 0) return "—";
        Calendar c = Calendar.getInstance();
        SimpleDateFormat dfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat dfTime = new SimpleDateFormat("h:mm a", Locale.getDefault());

        c.setTimeInMillis(ini);
        String d = dfDate.format(c.getTime());
        String hi= dfTime.format(c.getTime());
        c.setTimeInMillis(fin);
        String hf= dfTime.format(c.getTime());
        return d + ", " + hi + " - " + hf;
    }

    private static String parseFromNotes(String notas, String key) {
        if (isEmpty(notas) || isEmpty(key)) return null;
        String look = key + ": ";
        int i = notas.indexOf(look);
        if (i < 0) return null;
        int start = i + look.length();
        int end = notas.indexOf(".", start);
        if (end < 0) end = notas.length();
        return notas.substring(start, end).trim();
    }

    private static String safe(String s) { return isEmpty(s) ? "—" : s; }
    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    /** Formatea ISO-8601 a "HH:mm". Si no puede, devuelve el ISO tal cual. */
    private static String prettyEta(String iso) {
        if (isEmpty(iso)) return "—";
        // Extract HH:mm sin depender de java.time
        int t = iso.indexOf('T');
        if (t >= 0 && iso.length() >= t + 6) {
            // iso ej: 2025-09-20T18:45:00-05:00 → toma 18:45
            return iso.substring(t + 1, t + 6);
        }
        return iso;
    }
}
