package com.hfad.encomiendas.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.SolicitudDao;

import java.util.ArrayList;
import java.util.List;

public class PendienteDetalleAdapter extends RecyclerView.Adapter<PendienteDetalleAdapter.VH> {

    private final List<SolicitudDao.PendienteDetalle> data = new ArrayList<>();

    public void setData(List<SolicitudDao.PendienteDetalle> list) {
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
        SolicitudDao.PendienteDetalle d = data.get(position);

        h.tvHead.setText("Pendiente");
        String prod = nn(d.tipoProducto) + " " + nn(d.tamanoPaquete) +
                " — " + nn(d.horaDesde) + "–" + nn(d.horaHasta);
        h.tvProducto.setText(prod);
        h.tvDireccion.setText(nn(d.direccion));
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvHead, tvProducto, tvDireccion;
        VH(@NonNull View v) {
            super(v);
            tvHead      = v.findViewById(R.id.tvHead);
            tvProducto  = v.findViewById(R.id.tvProducto);
            tvDireccion = v.findViewById(R.id.tvDireccion);
        }
    }

    private static String nn(String s) { return (s == null || s.trim().isEmpty()) ? "—" : s.trim(); }
}
