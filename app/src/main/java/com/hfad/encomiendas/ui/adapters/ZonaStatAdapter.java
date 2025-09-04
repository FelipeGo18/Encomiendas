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

    // Item de zona para el tablero
    public static class ZonaItem {
        public String zona;
        public int asignadas;
        public int pendientes;

        public ZonaItem(String zona, int asignadas, int pendientes) {
            this.zona = (zona == null || zona.trim().isEmpty()) ? "(sin zona)" : zona.trim();
            this.asignadas = asignadas;
            this.pendientes = pendientes;
        }
    }

    public interface Listener {
        void onItemClick(ZonaItem item);     // tap en la tarjeta -> ver detalle
        void onAsignarZona(ZonaItem item);   // pulsar botón Asignar zona
    }

    private final List<VHItem> data = new ArrayList<>();
    private final Listener listener;

    // envoltorio para poder modificar/ocultar el botón sin perder valores
    private static class VHItem {
        ZonaItem item;
        VHItem(ZonaItem i) { this.item = i; }
    }

    public ZonaStatAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<ZonaItem> items) {
        data.clear();
        if (items != null) {
            for (ZonaItem it : items) data.add(new VHItem(it));
        }
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
        VHItem w = data.get(position);
        ZonaItem z = w.item;

        h.tvZona.setText(z.zona);
        h.tvAsignadas.setText(String.format(Locale.getDefault(), "Asignadas: %d", z.asignadas));
        h.tvPendientes.setText(String.format(Locale.getDefault(), "Pendientes: %d", z.pendientes));

        // Card clickeable para ir al detalle (ya NO hay botón "Ver detalle")
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(z);
        });

        // Botón "Asignar zona": visible solo si hay pendientes
        if (z.pendientes > 0) {
            h.btnAsignar.setVisibility(View.VISIBLE);
            h.btnAsignar.setEnabled(true);
        } else {
            h.btnAsignar.setVisibility(View.GONE);
            h.btnAsignar.setEnabled(false);
        }

        h.btnAsignar.setOnClickListener(v -> {
            if (listener != null) listener.onAsignarZona(z);
        });

        // (Estética) leve tinte según carga pendiente
        int alpha = Math.min(100, 20 + z.pendientes * 10);
        h.itemView.setBackgroundColor((alpha << 24)); // sólo alfa
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvZona, tvAsignadas, tvPendientes;
        MaterialButton btnAsignar;
        VH(@NonNull View v) {
            super(v);
            tvZona       = v.findViewById(R.id.tvZona);
            tvAsignadas  = v.findViewById(R.id.tvAsignadas);
            tvPendientes = v.findViewById(R.id.tvPendientes);
            btnAsignar   = v.findViewById(R.id.btnAsignarZona);
        }
    }
}
