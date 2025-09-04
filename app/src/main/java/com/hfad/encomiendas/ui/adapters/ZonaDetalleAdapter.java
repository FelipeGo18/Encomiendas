package com.hfad.encomiendas.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AsignacionDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ZonaDetalleAdapter extends RecyclerView.Adapter<ZonaDetalleAdapter.VH> {

    public interface OnItemTap {
        void onTap(AsignacionDao.AsignacionDetalle det);
    }

    private final List<AsignacionDao.AsignacionDetalle> data = new ArrayList<>();
    private final OnItemTap listener;

    public ZonaDetalleAdapter(OnItemTap listener) {
        this.listener = listener;
    }

    public void setData(List<AsignacionDao.AsignacionDetalle> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_asignacion_detalle, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AsignacionDao.AsignacionDetalle d = data.get(position);

        String head = "#" + d.id + " • " + (d.estado == null ? "ASIGNADA" : d.estado)
                + " • Orden:" + (d.ordenRuta == null ? "—" : d.ordenRuta);
        h.tvHead.setText(head);

        String prod = nn(d.tipoProducto) + " " + nn(d.tamanoPaquete)
                + " — " + nn(d.ciudadOrigen) + " → " + nn(d.ciudadDestino);
        h.tvProducto.setText(prod);

        String dir = nn(d.direccion) + " — " + nn(d.horaDesde) + "–" + nn(d.horaHasta);
        h.tvDireccion.setText(dir);

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onTap(d); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvHead, tvProducto, tvDireccion;
        VH(@NonNull View v) {
            super(v);
            tvHead = v.findViewById(R.id.tvHead);
            tvProducto = v.findViewById(R.id.tvProducto);
            tvDireccion = v.findViewById(R.id.tvDireccion);
        }
    }

    private static String nn(String s) { return (s == null || s.trim().isEmpty()) ? "—" : s.trim(); }
}
