package com.hfad.encomiendas.ui.adapters;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.TrackingEvent;
import java.util.*;

public class TrackingAdapter extends RecyclerView.Adapter<TrackingAdapter.VH> {
    private final List<TrackingEvent> data = new ArrayList<>();
    public void submit(List<TrackingEvent> evs){ data.clear(); if(evs!=null) data.addAll(evs); notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_tracking_event, p, false));
    }
    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        TrackingEvent e = data.get(i);
        h.tvType.setText(e.type);
        h.tvDesc.setText(e.detail == null ? "" : e.detail);
        h.tvTime.setText(e.occurredAt);
    }
    @Override public int getItemCount(){ return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvType, tvDesc, tvTime;
        VH(View v){ super(v);
            tvType=v.findViewById(R.id.tvType);
            tvDesc=v.findViewById(R.id.tvDesc);
            tvTime=v.findViewById(R.id.tvTime);
        }
    }
}
