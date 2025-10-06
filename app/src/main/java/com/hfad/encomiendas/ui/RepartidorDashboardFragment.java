// com.hfad.encomiendas.ui.RepartidorDashboardFragment.java
package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.*;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.ManifiestoItem;
import com.hfad.encomiendas.data.Recolector;
import java.util.*;
import java.util.concurrent.Executors;

public class RepartidorDashboardFragment extends Fragment {

    private RecyclerView rv;
    private TextView tvEmpty;
    private TextView tvReputacion;
    private Button btnVerCalificaciones;
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
        tvReputacion = v.findViewById(R.id.tvReputacion);
        btnVerCalificaciones = v.findViewById(R.id.btnVerCalificaciones);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new EntregasAdapter(id -> openEntrega(id));
        rv.setAdapter(adapter);

        // Configurar el botón para ver calificaciones
        btnVerCalificaciones.setOnClickListener(view -> navigateToRatings());

        cargar();
        cargarReputacion();
    }

    private void navigateToRatings() {
        NavController nav = NavHostFragment.findNavController(this);
        nav.navigate(R.id.misCalificacionesFragment);
    }

    private void cargar() {
        Executors.newSingleThreadExecutor().execute(() -> {
            String email = new SessionManager(requireContext()).getEmail();
            List<ManifiestoItem> items = AppDatabase.getInstance(requireContext())
                    .manifiestoDao().listEnRutaParaRepartidor(email);
            requireActivity().runOnUiThread(() -> {
                adapter.setData(items == null ? Collections.emptyList() : items);
                tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void openEntrega(int itemId) {
        Bundle args = new Bundle();
        args.putInt("manifiestoItemId", itemId);
        NavController nav = NavHostFragment.findNavController(this);
        nav.navigate(R.id.entregaFragment, args);
    }

    private void cargarReputacion(){
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                String email = new SessionManager(requireContext()).getEmail();
                Recolector r = db.recolectorDao().getByUserEmail(email);
                if (r == null) return;

                // USAR RATING MANAGEMENT SERVICE PARA ESTADÍSTICAS DETALLADAS
                com.hfad.encomiendas.core.RatingManagementService ratingService =
                    new com.hfad.encomiendas.core.RatingManagementService(db);

                com.hfad.encomiendas.core.RatingManagementService.RatingStats stats =
                    ratingService.getRepartidorStats(r.id);

                requireActivity().runOnUiThread(() -> {
                    if (stats.totalRatings == 0) {
                        tvReputacion.setText("Reputación: sin calificaciones");
                    } else {
                        // Mostrar estadísticas más detalladas
                        String reputacionDetallada = String.format(Locale.getDefault(),
                            "Reputación: %.2f ★ (%d calificaciones)\n" +
                            "★★★★★ %.0f%% | ★★★★☆ %.0f%% | ★★★☆☆ %.0f%% | ★★☆☆☆ %.0f%% | ★☆☆☆☆ %.0f%%",
                            stats.averageRating, stats.totalRatings,
                            stats.getPercentage(5), stats.getPercentage(4), stats.getPercentage(3),
                            stats.getPercentage(2), stats.getPercentage(1));

                        tvReputacion.setText(reputacionDetallada);
                    }
                });
            } catch (Exception ignore) {
                requireActivity().runOnUiThread(() -> {
                    tvReputacion.setText("Reputación: error al cargar");
                });
            }
        });
    }

    /* ===== Adapter ===== */
    static class EntregasAdapter extends RecyclerView.Adapter<VH> {
        interface OnClick { void run(int itemId); }

        private final List<ManifiestoItem> data = new ArrayList<>();
        private final OnClick click;

        EntregasAdapter(OnClick c){ this.click = c; }

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
            h.itemView.setOnClickListener(v -> click.run(it.id));
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