package com.hfad.encomiendas.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.Solicitud;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SolicitudesAdapter extends RecyclerView.Adapter<SolicitudesAdapter.VH> {

    public interface Listener {
        void onClick(Solicitud s);
    }

    private final Listener listener;
    private final List<Solicitud> data = new ArrayList<>();

    public SolicitudesAdapter(Listener l) { this.listener = l; }

    public void setData(List<Solicitud> list) {
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
        Solicitud s = data.get(pos);

        String titulo = safe(s.guia) + "  •  " + safe(s.estado);
        h.tvTitulo.setText(titulo);

        String sub = formatRange(s.ventanaInicioMillis, s.ventanaFinMillis);
        h.tvSub.setText(sub);

        h.tvDir.setText(safe(s.direccion));

        String destino = parseFromNotes(s.notas, "DestinoDir");
        h.tvDestino.setText("Destino: " + (isEmpty(destino) ? "—" : destino));

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(s);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitulo, tvSub, tvDir, tvDestino;
        VH(@NonNull View v) {
            super(v);
            tvTitulo = v.findViewById(R.id.tvTitulo);
            tvSub    = v.findViewById(R.id.tvSub);
            tvDir    = v.findViewById(R.id.tvDir);
            tvDestino= v.findViewById(R.id.tvDestino);
        }
    }

    // -------
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
}
