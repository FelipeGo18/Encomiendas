package com.hfad.encomiendas.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.api.ApiClient;
import com.hfad.encomiendas.api.UserApi;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.FechaCount;
import com.hfad.encomiendas.data.RolCount;
import com.hfad.encomiendas.data.User;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminFragment extends Fragment {

    private static final String TAG = "AdminFragment";

    private TextView tvAdminEmail;
    private TextView tvTotalSolicitudes;
    private TextView tvTotalUsuarios;
    private TextView tvRecolectoresActivos;
    private CardView cardEstadisticas;
    private CardView cardGestionUsuarios;
    private CardView cardConfiguracion;

    // Nuevo botón de sincronización
    private com.google.android.material.button.MaterialButton btnSincronizarAPI;

    private BarChart chartSolicitudesPorEstado;
    private PieChart chartUsuariosPorRol;
    private LineChart chartSolicitudesUltimos7Dias;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin, container, false);

        initViews(view);
        setupListeners();
        loadAdminInfo();
        loadStatistics();

        return view;
    }

    private void initViews(View view) {
        tvAdminEmail = view.findViewById(R.id.tvAdminEmail);
        tvTotalSolicitudes = view.findViewById(R.id.tvTotalSolicitudes);
        tvTotalUsuarios = view.findViewById(R.id.tvTotalUsuarios);
        tvRecolectoresActivos = view.findViewById(R.id.tvRecolectoresActivos);

        cardEstadisticas = view.findViewById(R.id.cardEstadisticas);
        cardGestionUsuarios = view.findViewById(R.id.cardGestionUsuarios);
        cardConfiguracion = view.findViewById(R.id.cardConfiguracion);

        // Inicializar botón de sincronización
        btnSincronizarAPI = view.findViewById(R.id.btnSincronizarAPI);

        chartSolicitudesPorEstado = view.findViewById(R.id.chartSolicitudesPorEstado);
        chartUsuariosPorRol = view.findViewById(R.id.chartUsuariosPorRol);
        chartSolicitudesUltimos7Dias = view.findViewById(R.id.chartSolicitudesUltimos7Dias);
    }

    private void setupListeners() {
        cardEstadisticas.setOnClickListener(v -> {
            NavHostFragment.findNavController(this).navigate(R.id.action_admin_to_estadisticas);
        });

        cardGestionUsuarios.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Gestión de Usuarios - En desarrollo", Toast.LENGTH_SHORT).show();
        });

        cardConfiguracion.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Configuración del Sistema - En desarrollo", Toast.LENGTH_SHORT).show();
        });

        // Listener para botón de sincronización
        btnSincronizarAPI.setOnClickListener(v -> {
            sincronizarRoomConAPI();
        });
    }

    /**
     * Sincronizar datos BIDIRECCIONAL: Room ↔ API
     * 1. Sube usuarios de Room a la API
     * 2. Descarga usuarios de la API a Room
     */
    private void sincronizarRoomConAPI() {
        btnSincronizarAPI.setEnabled(false);
        btnSincronizarAPI.setText("🔄 Sincronizando...");

        Toast.makeText(requireContext(), "Iniciando sincronización bidireccional...", Toast.LENGTH_SHORT).show();

        // Usar sincronización bidireccional
        com.hfad.encomiendas.api.SyncHelper.syncBidirectional(requireContext(),
            new com.hfad.encomiendas.api.SyncHelper.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                // ⭐ ENVOLVER EN runOnUiThread para actualizar UI de forma segura
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return; // Verificar que el fragment sigue activo

                    btnSincronizarAPI.setEnabled(true);
                    btnSincronizarAPI.setText("✅ Sincronizado");

                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    Log.d(TAG, message);

                    // Recargar estadísticas con los nuevos datos
                    cargarEstadisticasLocales();

                    // Volver al texto original después de 3 segundos
                    btnSincronizarAPI.postDelayed(() -> {
                        btnSincronizarAPI.setText("🔄 Sincronizar ↕ API");
                    }, 3000);
                });
            }

            @Override
            public void onError(String message) {
                // ⭐ ENVOLVER EN runOnUiThread para actualizar UI de forma segura
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return; // Verificar que el fragment sigue activo

                    btnSincronizarAPI.setEnabled(true);
                    btnSincronizarAPI.setText("❌ Error");

                    Toast.makeText(requireContext(), "Error: " + message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error sincronización: " + message);

                    // Volver al texto original después de 3 segundos
                    btnSincronizarAPI.postDelayed(() -> {
                        btnSincronizarAPI.setText("🔄 Sincronizar ↕ API");
                    }, 3000);
                });
            }

            @Override
            public void onProgress(int usuariosEnviados, int totalUsuarios) {
                // ⭐ ENVOLVER EN runOnUiThread para actualizar UI de forma segura
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return; // Verificar que el fragment sigue activo
                    btnSincronizarAPI.setText("🔄 " + usuariosEnviados + "/" + totalUsuarios);
                });
            }
        });
    }

    private void loadAdminInfo() {
        SessionManager session = new SessionManager(requireContext());
        String email = session.getEmail();

        if (email != null && !email.isEmpty()) {
            tvAdminEmail.setText(email);
        }
    }

    private void loadStatistics() {
        // PRIMERO: Intentar cargar desde la API
        sincronizarUsuariosDesdeAPI();

        // SEGUNDO: Cargar datos locales (Room) mientras tanto
        cargarEstadisticasLocales();
    }

    /**
     * NUEVO MÉTODO: Sincronizar usuarios desde la API y guardar en Room
     */
    private void sincronizarUsuariosDesdeAPI() {
        UserApi api = ApiClient.getUserApi();

        api.getAllUsers().enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call, @NonNull Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<User> usuariosAPI = response.body();
                    Log.d(TAG, "✅ Usuarios obtenidos de la API: " + usuariosAPI.size());

                    // Guardar en Room (cache local) en segundo plano
                    new Thread(() -> {
                        AppDatabase db = AppDatabase.getInstance(requireContext());

                        // Actualizar Room con los datos frescos de la API
                        for (User user : usuariosAPI) {
                            try {
                                // Buscar por EMAIL (que es único), no por ID
                                User existente = db.userDao().findByEmail(user.email);

                                if (existente != null) {
                                    // Actualizar usuario existente manteniendo el ID local
                                    user.id = existente.id; // Mantener el ID de Room
                                    db.userDao().update(user);
                                    Log.d(TAG, "📝 Usuario actualizado: " + user.email);
                                } else {
                                    // Insertar nuevo usuario
                                    db.userDao().insert(user);
                                    Log.d(TAG, "➕ Usuario nuevo insertado: " + user.email);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "❌ Error sincronizando usuario " + user.email + ": " + e.getMessage());
                            }
                        }

                        Log.d(TAG, "💾 Usuarios sincronizados en Room");

                        // Recargar estadísticas con datos actualizados
                        requireActivity().runOnUiThread(() -> {
                            cargarEstadisticasLocales();
                            Toast.makeText(requireContext(),
                                         "Datos actualizados desde el servidor",
                                         Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                } else {
                    Log.e(TAG, "❌ Error en API: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                Log.e(TAG, "❌ Error de conexión: " + t.getMessage());
                // No hacer nada - los datos locales ya se están mostrando
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(),
                                 "Usando datos guardados (sin conexión)",
                                 Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * RENOMBRADO: Método original que carga desde Room (datos locales)
     */
    private void cargarEstadisticasLocales() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());

            // Estadísticas de solicitudes (estas siguen siendo locales por ahora)
            int totalSolicitudes = db.solicitudDao().getTotalSolicitudes();
            int pendientes = db.solicitudDao().countSolicitudesByEstado("PENDIENTE");
            int asignadas = db.solicitudDao().countSolicitudesByEstado("ASIGNADA");
            int recolectadas = db.solicitudDao().countSolicitudesByEstado("RECOLECTADA");
            int enTransito = db.solicitudDao().countSolicitudesByEstado("EN_TRANSITO");
            int entregadas = db.solicitudDao().countSolicitudesByEstado("ENTREGADA");

            // Estadísticas de usuarios (ahora desde Room, pero sincronizadas con la API)
            int totalUsuarios = db.userDao().getTotalUsuarios();
            List<RolCount> usuariosPorRol = db.userDao().getCountByRol();

            // Estadísticas de recolectores
            int recolectoresActivos = db.recolectorDao().getTotalRecolectoresActivos();

            // Solicitudes últimos 7 días
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -7);
            long startMillis = cal.getTimeInMillis();
            List<FechaCount> solicitudesPorDia = db.solicitudDao().getSolicitudesLast7Days(startMillis);

            // Actualizar UI en el hilo principal
            requireActivity().runOnUiThread(() -> {
                tvTotalSolicitudes.setText(String.valueOf(totalSolicitudes));
                tvTotalUsuarios.setText(String.valueOf(totalUsuarios));
                tvRecolectoresActivos.setText(String.valueOf(recolectoresActivos));

                // Configurar gráficas
                setupBarChart(pendientes, asignadas, recolectadas, enTransito, entregadas);
                setupPieChart(usuariosPorRol);
                setupLineChart(solicitudesPorDia);
            });
        }).start();
    }

    private void setupBarChart(int pendientes, int asignadas, int recolectadas, int enTransito, int entregadas) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, pendientes));
        entries.add(new BarEntry(1, asignadas));
        entries.add(new BarEntry(2, recolectadas));
        entries.add(new BarEntry(3, enTransito));
        entries.add(new BarEntry(4, entregadas));

        BarDataSet dataSet = new BarDataSet(entries, "Solicitudes");

        // Colores para cada barra
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#FF9800")); // Pendiente - Naranja
        colors.add(Color.parseColor("#2196F3")); // Asignada - Azul
        colors.add(Color.parseColor("#4CAF50")); // Recolectada - Verde
        colors.add(Color.parseColor("#9C27B0")); // En Tránsito - Morado
        colors.add(Color.parseColor("#4CAF50")); // Entregada - Verde
        dataSet.setColors(colors);

        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.BLACK);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.9f);

        chartSolicitudesPorEstado.setData(barData);
        chartSolicitudesPorEstado.getDescription().setEnabled(false);
        chartSolicitudesPorEstado.setFitBars(true);
        chartSolicitudesPorEstado.animateY(1000);

        // Configurar eje X
        String[] labels = {"Pendiente", "Asignada", "Recolectada", "En Tránsito", "Entregada"};
        XAxis xAxis = chartSolicitudesPorEstado.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setTextSize(10f);

        // Configurar leyenda
        Legend legend = chartSolicitudesPorEstado.getLegend();
        legend.setEnabled(false);

        chartSolicitudesPorEstado.invalidate();
    }

    private void setupPieChart(List<RolCount> usuariosPorRol) {
        ArrayList<PieEntry> entries = new ArrayList<>();

        for (RolCount rolCount : usuariosPorRol) {
            entries.add(new PieEntry(rolCount.count, getRolLabel(rolCount.rol)));
        }

        PieDataSet dataSet = new PieDataSet(entries, "Usuarios por Rol");

        // Colores para cada rol
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#2196F3")); // REMITENTE - Azul
        colors.add(Color.parseColor("#4CAF50")); // RECOLECTOR - Verde
        colors.add(Color.parseColor("#FF9800")); // REPARTIDOR - Naranja
        colors.add(Color.parseColor("#9C27B0")); // HUB - Morado
        colors.add(Color.parseColor("#F44336")); // ADMIN - Rojo
        colors.add(Color.parseColor("#00BCD4")); // ASIGNADOR - Cian
        dataSet.setColors(colors);

        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setSliceSpace(3f);

        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        chartUsuariosPorRol.setData(pieData);
        chartUsuariosPorRol.getDescription().setEnabled(false);
        chartUsuariosPorRol.setDrawHoleEnabled(true);
        chartUsuariosPorRol.setHoleColor(Color.WHITE);
        chartUsuariosPorRol.setHoleRadius(40f);
        chartUsuariosPorRol.setTransparentCircleRadius(45f);
        chartUsuariosPorRol.setCenterText("Usuarios\npor Rol");
        chartUsuariosPorRol.setCenterTextSize(14f);
        chartUsuariosPorRol.setEntryLabelColor(Color.BLACK);
        chartUsuariosPorRol.setEntryLabelTextSize(11f);
        chartUsuariosPorRol.animateY(1000);

        // Configurar leyenda
        Legend legend = chartUsuariosPorRol.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setTextSize(10f);

        chartUsuariosPorRol.invalidate();
    }

    private void setupLineChart(List<FechaCount> solicitudesPorDia) {
        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        for (int i = 0; i < solicitudesPorDia.size(); i++) {
            FechaCount fc = solicitudesPorDia.get(i);
            entries.add(new Entry(i, fc.count));

            // fc.fecha ya es un String, no necesita formato
            if (fc.fecha != null && fc.fecha.length() >= 10) {
                // Si es formato yyyy-MM-dd, extraer solo día/mes
                try {
                    String[] parts = fc.fecha.split("-");
                    if (parts.length >= 3) {
                        labels.add(parts[2] + "/" + parts[1]); // dd/MM
                    } else {
                        labels.add(fc.fecha);
                    }
                } catch (Exception e) {
                    labels.add(fc.fecha);
                }
            } else {
                labels.add(fc.fecha != null ? fc.fecha : "");
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, "Solicitudes");
        dataSet.setColor(Color.parseColor("#2196F3"));
        dataSet.setCircleColor(Color.parseColor("#2196F3"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(10f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#2196F3"));
        dataSet.setFillAlpha(50);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);

        chartSolicitudesUltimos7Dias.setData(lineData);
        chartSolicitudesUltimos7Dias.getDescription().setEnabled(false);
        chartSolicitudesUltimos7Dias.animateX(1000);

        // Configurar eje X
        XAxis xAxis = chartSolicitudesUltimos7Dias.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(10f);

        // Configurar leyenda
        Legend legend = chartSolicitudesUltimos7Dias.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setTextSize(10f);

        chartSolicitudesUltimos7Dias.invalidate();
    }

    private String getRolLabel(String rol) {
        switch (rol) {
            case "REMITENTE": return "Remitente";
            case "RECOLECTOR": return "Recolector";
            case "REPARTIDOR": return "Repartidor";
            case "HUB": return "Admin";
            case "ADMIN": return "Admin";
            case "ASIGNADOR": return "Asignador";
            default: return rol;
        }
    }
}
