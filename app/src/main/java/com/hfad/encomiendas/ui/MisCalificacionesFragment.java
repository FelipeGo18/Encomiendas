package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.RatingManagementService;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Rating;
import com.hfad.encomiendas.data.Recolector;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MisCalificacionesFragment extends Fragment {

    private RecyclerView rvCalificaciones;
    private TextView tvEstadisticas;
    private TextView tvEmpty;
    private SwipeRefreshLayout swipeRefresh;
    private CalificacionesAdapter adapter;
    private RatingManagementService ratingService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mis_calificaciones, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvCalificaciones = view.findViewById(R.id.rvCalificaciones);
        tvEstadisticas = view.findViewById(R.id.tvEstadisticas);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);

        rvCalificaciones.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CalificacionesAdapter();
        rvCalificaciones.setAdapter(adapter);

        ratingService = new RatingManagementService(AppDatabase.getInstance(requireContext()));

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::cargarCalificaciones);
        }

        cargarCalificaciones();
    }

    private void cargarCalificaciones() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                String email = new SessionManager(requireContext()).getEmail();
                Recolector recolector = db.recolectorDao().getByUserEmail(email);

                if (recolector == null) {
                    runOnUi(() -> {
                        tvEmpty.setText("No se pudo cargar el perfil del repartidor");
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvCalificaciones.setVisibility(View.GONE);
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    });
                    return;
                }

                // Obtener estadísticas
                RatingManagementService.RatingStats stats = ratingService.getRepartidorStats(recolector.id);

                // Obtener calificaciones detalladas
                List<Rating> calificaciones = db.ratingDao().listByRepartidorId(recolector.id);

                runOnUi(() -> {
                    // Mostrar estadísticas
                    mostrarEstadisticas(stats);

                    // Mostrar calificaciones
                    if (calificaciones == null || calificaciones.isEmpty()) {
                        tvEmpty.setText("Aún no tienes calificaciones.\n¡Completa más entregas para recibir calificaciones de los clientes!");
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvCalificaciones.setVisibility(View.GONE);
                    } else {
                        adapter.setData(calificaciones);
                        tvEmpty.setVisibility(View.GONE);
                        rvCalificaciones.setVisibility(View.VISIBLE);
                    }

                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                });

            } catch (Exception e) {
                runOnUi(() -> {
                    tvEmpty.setText("Error al cargar las calificaciones: " + e.getMessage());
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvCalificaciones.setVisibility(View.GONE);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                });
            }
        });
    }

    private void mostrarEstadisticas(RatingManagementService.RatingStats stats) {
        if (stats.totalRatings == 0) {
            tvEstadisticas.setText("📊 Estadísticas de Calificaciones\n\nAún no tienes calificaciones");
            return;
        }

        String estadisticas = String.format(Locale.getDefault(),
            "📊 Estadísticas de Calificaciones\n\n" +
            "⭐ Promedio: %.2f estrellas (%d calificaciones)\n\n" +
            "Distribución:\n" +
            "★★★★★ %d calificaciones (%.1f%%)\n" +
            "★★★★☆ %d calificaciones (%.1f%%)\n" +
            "★★★☆☆ %d calificaciones (%.1f%%)\n" +
            "★★☆☆☆ %d calificaciones (%.1f%%)\n" +
            "★☆☆☆☆ %d calificaciones (%.1f%%)",
            stats.averageRating, stats.totalRatings,
            stats.fiveStars, stats.getPercentage(5),
            stats.fourStars, stats.getPercentage(4),
            stats.threeStars, stats.getPercentage(3),
            stats.twoStars, stats.getPercentage(2),
            stats.oneStars, stats.getPercentage(1));

        tvEstadisticas.setText(estadisticas);
    }

    private void runOnUi(Runnable r) {
        if (isAdded()) {
            requireActivity().runOnUiThread(r);
        }
    }

    /* =============== Adapter =============== */

    private static class CalificacionesAdapter extends RecyclerView.Adapter<CalificacionViewHolder> {
        private final List<Rating> calificaciones = new ArrayList<>();

        void setData(List<Rating> data) {
            calificaciones.clear();
            if (data != null) {
                calificaciones.addAll(data);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public CalificacionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calificacion_detalle, parent, false);
            return new CalificacionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CalificacionViewHolder holder, int position) {
            Rating rating = calificaciones.get(position);
            holder.bind(rating);
        }

        @Override
        public int getItemCount() {
            return calificaciones.size();
        }
    }

    private static class CalificacionViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvEstrellas;
        private final TextView tvFecha;
        private final TextView tvComentario;
        private final TextView tvCliente;

        CalificacionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEstrellas = itemView.findViewById(R.id.tvEstrellas);
            tvFecha = itemView.findViewById(R.id.tvFecha);
            tvComentario = itemView.findViewById(R.id.tvComentario);
            tvCliente = itemView.findViewById(R.id.tvCliente);
        }

        void bind(Rating rating) {
            // Mostrar estrellas
            StringBuilder estrellas = new StringBuilder();
            for (int i = 1; i <= 5; i++) {
                if (i <= rating.puntuacion) {
                    estrellas.append("★");
                } else {
                    estrellas.append("☆");
                }
            }
            tvEstrellas.setText(estrellas.toString() + " (" + rating.puntuacion + "/5)");

            // Mostrar fecha
            if (rating.fechaMillis > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                tvFecha.setText(sdf.format(new Date(rating.fechaMillis)));
            } else {
                tvFecha.setText("Fecha no disponible");
            }

            // Mostrar comentario
            if (rating.comentario != null && !rating.comentario.trim().isEmpty()) {
                tvComentario.setText("\"" + rating.comentario.trim() + "\"");
                tvComentario.setVisibility(View.VISIBLE);
            } else {
                tvComentario.setText("Sin comentarios");
                tvComentario.setVisibility(View.VISIBLE);
            }

            // Mostrar cliente (si está disponible)
            if (rating.clienteEmail != null && !rating.clienteEmail.trim().isEmpty()) {
                tvCliente.setText("Cliente: " + rating.clienteEmail);
                tvCliente.setVisibility(View.VISIBLE);
            } else {
                tvCliente.setVisibility(View.GONE);
            }
        }
    }
}
