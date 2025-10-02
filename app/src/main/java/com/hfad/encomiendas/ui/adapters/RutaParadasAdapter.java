package com.hfad.encomiendas.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.Solicitud;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RutaParadasAdapter extends RecyclerView.Adapter<RutaParadasAdapter.VH> {

    private final List<Solicitud> data = new ArrayList<>();
    private final SimpleDateFormat dfHora = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public void setData(List<Solicitud> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public List<Solicitud> getData() { return data; }

    public void move(int from, int to) {
        if (from < 0 || to < 0 || from >= data.size() || to >= data.size()) return;
        Solicitud s = data.remove(from);
        data.add(to, s);
        notifyItemMoved(from, to);
        notifyItemRangeChanged(Math.min(from, to), Math.abs(to - from) + 1);
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ruta_parada, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Solicitud s = data.get(pos);
        h.tvOrden.setText(String.valueOf(pos + 1));
        h.tvDireccion.setText(s.direccion == null ? "—" : s.direccion);
        String ventana = "—";
        if (s.ventanaInicioMillis != null) {
            String hi = dfHora.format(new Date(s.ventanaInicioMillis));
            String hf = dfHora.format(new Date(s.ventanaFinMillis));
            ventana = hi + " - " + hf;
        }
        h.tvVentana.setText(ventana);
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOrden, tvDireccion, tvVentana; ImageView ivDrag;
        VH(@NonNull View v) {
            super(v);
            tvOrden = v.findViewById(R.id.tvOrden);
            tvDireccion = v.findViewById(R.id.tvDireccion);
            tvVentana = v.findViewById(R.id.tvVentana);
            ivDrag = v.findViewById(R.id.ivDragHandle);
        }
    }
}

