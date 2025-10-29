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

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.FechaCount;
import com.hfad.encomiendas.data.RecolectorStats;
import com.hfad.encomiendas.data.RolCount;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EstadisticasFragment extends Fragment {

    private TextView tvTotalSolicitudes;
    private TextView tvPendientes;
    private TextView tvAsignadas;
    private TextView tvRecolectadas;
    private TextView tvTotalUsuarios;
    private TextView tvTotalRecolectores;
    private TextView tvRecolectoresActivos;
    private TextView tvTiempoPromedio;

    private RecyclerView rvUsuariosPorRol;
    private RecyclerView rvSolicitudesPorDia;
    private RecyclerView rvTopRecolectores;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_estadisticas, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar vistas
        tvTotalSolicitudes = view.findViewById(R.id.tv_total_solicitudes);
        tvPendientes = view.findViewById(R.id.tv_pendientes);
        tvAsignadas = view.findViewById(R.id.tv_asignadas);
        tvRecolectadas = view.findViewById(R.id.tv_recolectadas);
        tvTotalUsuarios = view.findViewById(R.id.tv_total_usuarios);
        tvTotalRecolectores = view.findViewById(R.id.tv_total_recolectores);
        tvRecolectoresActivos = view.findViewById(R.id.tv_recolectores_activos);
        tvTiempoPromedio = view.findViewById(R.id.tv_tiempo_promedio);

        rvUsuariosPorRol = view.findViewById(R.id.rv_usuarios_por_rol);
        rvSolicitudesPorDia = view.findViewById(R.id.rv_solicitudes_por_dia);
        rvTopRecolectores = view.findViewById(R.id.rv_top_recolectores);

        // Configurar RecyclerViews
        rvUsuariosPorRol.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSolicitudesPorDia.setLayoutManager(new LinearLayoutManager(getContext()));
        rvTopRecolectores.setLayoutManager(new LinearLayoutManager(getContext()));

        // Cargar estadísticas
        cargarEstadisticas();
    }

    private void cargarEstadisticas() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());

            // Estadísticas de solicitudes
            int totalSolicitudes = db.solicitudDao().getTotalSolicitudes();
            int pendientes = db.solicitudDao().countSolicitudesByEstado("PENDIENTE");
            int asignadas = db.solicitudDao().countSolicitudesByEstado("ASIGNADA");
            int recolectadas = db.solicitudDao().countSolicitudesByEstado("RECOLECTADA");

            // Estadísticas de usuarios
            int totalUsuarios = db.userDao().getTotalUsuarios();
            List<RolCount> usuariosPorRol = db.userDao().getCountByRol();

            // Estadísticas de recolectores
            int totalRecolectores = db.recolectorDao().getTotalRecolectores();
            int recolectoresActivos = db.recolectorDao().getTotalRecolectoresActivos();
            List<RecolectorStats> topRecolectores = db.recolectorDao().getRecolectorStats();

            // Tiempo promedio de recolección
            Long avgMillis = db.solicitudDao().getAvgTiempoRecoleccion();
            String tiempoPromedio = calcularTiempoPromedio(avgMillis);

            // Solicitudes últimos 7 días
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -7);
            long startMillis = cal.getTimeInMillis();
            List<FechaCount> solicitudesPorDia = db.solicitudDao().getSolicitudesLast7Days(startMillis);

            // Actualizar UI en el hilo principal
            requireActivity().runOnUiThread(() -> {
                tvTotalSolicitudes.setText(String.valueOf(totalSolicitudes));
                tvPendientes.setText(String.valueOf(pendientes));
                tvAsignadas.setText(String.valueOf(asignadas));
                tvRecolectadas.setText(String.valueOf(recolectadas));
                tvTotalUsuarios.setText(String.valueOf(totalUsuarios));
                tvTotalRecolectores.setText(String.valueOf(totalRecolectores));
                tvRecolectoresActivos.setText(String.valueOf(recolectoresActivos));
                tvTiempoPromedio.setText(tiempoPromedio);

                // Configurar adapters
                rvUsuariosPorRol.setAdapter(new RolCountAdapter(usuariosPorRol));
                rvSolicitudesPorDia.setAdapter(new FechaCountAdapter(solicitudesPorDia));
                rvTopRecolectores.setAdapter(new RecolectorStatsAdapter(topRecolectores));
            });
        }).start();
    }

    private String calcularTiempoPromedio(Long millis) {
        if (millis == null || millis == 0) return "N/A";

        long horas = TimeUnit.MILLISECONDS.toHours(millis);
        long minutos = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;

        if (horas > 0) {
            return horas + "h " + minutos + "m";
        } else {
            return minutos + " minutos";
        }
    }

    // ==================== ADAPTERS ====================

    private static class RolCountAdapter extends RecyclerView.Adapter<RolCountAdapter.ViewHolder> {
        private final List<RolCount> items;

        RolCountAdapter(List<RolCount> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_estadistica_simple, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RolCount item = items.get(position);
            holder.tvLabel.setText(item.rol);
            holder.tvValue.setText(String.valueOf(item.count));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvLabel, tvValue;

            ViewHolder(View v) {
                super(v);
                tvLabel = v.findViewById(R.id.tv_label);
                tvValue = v.findViewById(R.id.tv_value);
            }
        }
    }

    private static class FechaCountAdapter extends RecyclerView.Adapter<FechaCountAdapter.ViewHolder> {
        private final List<FechaCount> items;

        FechaCountAdapter(List<FechaCount> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_estadistica_simple, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FechaCount item = items.get(position);
            holder.tvLabel.setText(item.fecha);
            holder.tvValue.setText(String.valueOf(item.count));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvLabel, tvValue;

            ViewHolder(View v) {
                super(v);
                tvLabel = v.findViewById(R.id.tv_label);
                tvValue = v.findViewById(R.id.tv_value);
            }
        }
    }

    private static class RecolectorStatsAdapter extends RecyclerView.Adapter<RecolectorStatsAdapter.ViewHolder> {
        private final List<RecolectorStats> items;

        RecolectorStatsAdapter(List<RecolectorStats> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_estadistica_simple, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RecolectorStats item = items.get(position);
            holder.tvLabel.setText(item.nombre);
            holder.tvValue.setText(item.completadas + " completadas");
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvLabel, tvValue;

            ViewHolder(View v) {
                super(v);
                tvLabel = v.findViewById(R.id.tv_label);
                tvValue = v.findViewById(R.id.tv_value);
            }
        }
    }
}
