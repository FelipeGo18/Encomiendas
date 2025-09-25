package com.hfad.encomiendas.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hfad.encomiendas.R;

import java.util.ArrayList;
import java.util.List;

public class ZonaStatAdapter extends RecyclerView.Adapter<ZonaStatAdapter.VH> {

    public interface Listener {
        void onItemClick(ZonaItem item);
        // dejamos el método para compatibilidad, pero ya no se usa:
        default void onAsignarZona(ZonaItem item) {}
    }

    public static class ZonaItem {
        public final String zona;
        public int asignadas;
        public int pendientes;
        public String preview; // líneas con bullets

        public ZonaItem(String zona, int asignadas, int pendientes) {
            this.zona = zona;
            this.asignadas = asignadas;
            this.pendientes = pendientes;
        }
    }

    private final Listener listener;
    private final List<ZonaItem> data = new ArrayList<>();

    public ZonaStatAdapter(Listener l) { this.listener = l; }

    public void setData(List<ZonaItem> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_zona_stat, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        ZonaItem it = data.get(pos);
        h.tvZona.setText(it.zona); // <-- SOLO la zona
        h.tvAsignadas.setText("Asignadas: " + it.asignadas);
        h.tvPendientes.setText("Pendientes: " + it.pendientes);
        h.tvPreview.setText(it.preview == null ? "" : it.preview);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(it);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvZona, tvAsignadas, tvPendientes, tvPreview;
        VH(@NonNull View v) {
            super(v);
            tvZona = v.findViewById(R.id.tvZona);
            tvAsignadas = v.findViewById(R.id.tvAsignadas);
            tvPendientes = v.findViewById(R.id.tvPendientes);
            tvPreview = v.findViewById(R.id.tvPreview);
        }
    }
}