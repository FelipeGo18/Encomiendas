package com.hfad.encomiendas.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.Zone;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ZoneListAdapter extends RecyclerView.Adapter<ZoneListAdapter.VH> {

    public interface Listener {
        void onEdit(Zone z);
        void onDelete(Zone z);
        void onEditPolygon(Zone z);
        void onOpenDetalle(Zone z);
    }

    private final Listener listener;
    private final List<Zone> data = new ArrayList<>();
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public ZoneListAdapter(Listener l){ this.listener = l; }

    public void setData(List<Zone> list){
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_zone, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Zone z = data.get(pos);
        h.tvName.setText(z.nombre.isEmpty()?"(sin nombre)": z.nombre);
        String info = "Creada: " + df.format(new Date(z.createdAt)) +
                (z.polygonJson != null && z.polygonJson.length()>2 ? "\nPolígono: sí" : "\nPolígono: no");
        h.tvInfo.setText(info);

        h.btnEdit.setOnClickListener(v -> { if (listener!=null) listener.onEdit(z); });
        h.btnDelete.setOnClickListener(v -> { if (listener!=null) listener.onDelete(z); });
        h.btnPolygon.setOnClickListener(v -> { if (listener!=null) listener.onEditPolygon(z); });
        h.btnOpen.setOnClickListener(v -> { if (listener!=null) listener.onOpenDetalle(z); });
        h.itemView.setOnClickListener(v -> { if (listener!=null) listener.onOpenDetalle(z); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvInfo, btnEdit, btnDelete, btnPolygon, btnOpen;
        VH(@NonNull View v){
            super(v);
            tvName = v.findViewById(R.id.tvZoneName);
            tvInfo = v.findViewById(R.id.tvZoneInfo);
            btnEdit = v.findViewById(R.id.btnEditZone);
            btnDelete = v.findViewById(R.id.btnDeleteZone);
            btnPolygon = v.findViewById(R.id.btnPolygonZone);
            btnOpen = v.findViewById(R.id.btnOpenZone);
        }
    }
}

