package com.hfad.encomiendas.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.hfad.encomiendas.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ZonaStatAdapter extends RecyclerView.Adapter<ZonaStatAdapter.VH> {

    public static class ZonaItem {
        public String zona;
        public int asignadas;
        public int pendientes;
        public String preview; // NUEVO: texto con 2-3 líneas

        public ZonaItem(String zona, int asignadas, int pendientes) {
            this.zona = (zona == null || zona.trim().isEmpty()) ? "(sin zona)" : zona.trim();
            this.asignadas = asignadas;
            this.pendientes = pendientes;
        }
    }

    public interface Listener {
        void onItemClick(ZonaItem item);
        void onAsignarZona(ZonaItem item);
    }

    private final List<ZonaItem> data = new ArrayList<>();
    private final Listener listener;

    public ZonaStatAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<ZonaItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_zona_stat, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ZonaItem z = data.get(position);

        h.tvZona.setText(z.zona);
        h.tvAsignadas.setText(String.format(Locale.getDefault(), "Asignadas: %d", z.asignadas));
        h.tvPendientes.setText(String.format(Locale.getDefault(), "Pendientes: %d", z.pendientes));
        h.tvPreview.setText(z.preview == null ? "" : z.preview);

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onItemClick(z); });

        if (z.pendientes > 0) {
            h.btnAsignar.setVisibility(View.VISIBLE);
            h.btnAsignar.setEnabled(true);
            h.btnAsignar.setOnClickListener(v -> { if (listener != null) listener.onAsignarZona(z); });
        } else {
            h.btnAsignar.setVisibility(View.GONE);
            h.btnAsignar.setEnabled(false);
        }
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvZona, tvAsignadas, tvPendientes, tvPreview;
        MaterialButton btnAsignar;
        VH(@NonNull View v) {
            super(v);
            tvZona       = v.findViewById(R.id.tvZona);
            tvAsignadas  = v.findViewById(R.id.tvAsignadas);
            tvPendientes = v.findViewById(R.id.tvPendientes);
            tvPreview    = v.findViewById(R.id.tvPreview);     // NUEVO
            btnAsignar   = v.findViewById(R.id.btnAsignarZona);
        }
    }
}
