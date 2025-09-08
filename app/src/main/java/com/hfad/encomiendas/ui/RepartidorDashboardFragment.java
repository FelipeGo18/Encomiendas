package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.ManifiestoItem;

import java.util.*;
import java.util.concurrent.Executors;

public class RepartidorDashboardFragment extends Fragment {

    private RecyclerView rv;
    private TextView tvEmpty;
    private EntregasAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_repartidor_dashboard, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        rv = v.findViewById(R.id.rvEntregas);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new EntregasAdapter(itemId -> {
            // Navegar al detalle de ENTREGa (usa el arg 'manifiestoItemId')
            Bundle args = EntregaFragment.argsOf(itemId);
            NavHostFragment.findNavController(this).navigate(R.id.entregaFragment, args);
        });
        rv.setAdapter(adapter);
        cargar();
    }

    @Override public void onResume() {
        super.onResume();
        cargar(); // refresca al volver del detalle
    }

    private void cargar() {
        Executors.newSingleThreadExecutor().execute(() -> {
            String email = new SessionManager(requireContext()).getEmail();
            List<ManifiestoItem> items = AppDatabase.getInstance(requireContext())
                    .manifiestoDao()
                    .listEnRutaParaRepartidor(email);

            requireActivity().runOnUiThread(() -> {
                adapter.setData(items == null ? Collections.emptyList() : items);
                tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            });
        });
    }

    /* ===== Adapter ===== */
    static class EntregasAdapter extends RecyclerView.Adapter<VH> {
        interface OnClick { void open(int itemId); }

        private final List<ManifiestoItem> data = new ArrayList<>();
        private final OnClick onClick;

        EntregasAdapter(OnClick onClick) { this.onClick = onClick; }

        void setData(List<ManifiestoItem> list){ data.clear(); data.addAll(list); notifyDataSetChanged(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_entrega_repartidor, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            ManifiestoItem it = data.get(pos);
            h.tvLinea1.setText((it.guia==null?"—":it.guia) + "  •  " + (it.estado==null?"—":it.estado));
            h.tvLinea2.setText("Destino: " + (it.destinoCiudad==null?"—":it.destinoCiudad));
            h.tvLinea3.setText((it.destinoDireccion==null?"—":it.destinoDireccion));

            h.itemView.setOnClickListener(v -> onClick.open(it.id)); // <-- abrir detalle
        }

        @Override public int getItemCount(){ return data.size(); }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvLinea1, tvLinea2, tvLinea3;
        VH(@NonNull View item){
            super(item);
            tvLinea1 = item.findViewById(R.id.tvLinea1);
            tvLinea2 = item.findViewById(R.id.tvLinea2);
            tvLinea3 = item.findViewById(R.id.tvLinea3);
        }
    }
}