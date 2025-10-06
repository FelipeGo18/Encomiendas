package com.hfad.encomiendas.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.LocationPeriodicWorker;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.core.TrackingForegroundService;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.data.Recolector;
import com.hfad.encomiendas.data.RecolectorDao;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MisAsignacionesFragment extends Fragment {

    private SwipeRefreshLayout swipe;
    private RecyclerView rv;
    private AsignacionesAdapter adapter;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private ActivityResultLauncher<String[]> permisosMultiLauncher;
    private Integer currentRecolectorId = null;
    private boolean tieneFine = false;
    private boolean tieneCoarse = false;

    public MisAsignacionesFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mis_asignaciones, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        swipe = v.findViewById(R.id.swipeRefresh);
        rv = v.findViewById(R.id.rvAsignaciones);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new AsignacionesAdapter(asignacionId -> {
            NavController nav = NavHostFragment.findNavController(this);
            Bundle args = new Bundle();
            args.putInt("asignacionId", asignacionId);
            nav.navigate(R.id.action_misAsignaciones_to_detalle, args);
        });
        rv.setAdapter(adapter);

        if (swipe != null) swipe.setOnRefreshListener(this::loadData);

        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());

        permisosMultiLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), res -> {
            Boolean fine = res.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
            Boolean coarse = res.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
            tieneFine = Boolean.TRUE.equals(fine);
            tieneCoarse = Boolean.TRUE.equals(coarse) || tieneFine; // fine implica coarse
            if (tieneFine || tieneCoarse) {
                iniciarLocationUpdates();
            } else {
                Toast.makeText(requireContext(), "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
            }
        });

        prepararCallbackUbicacion();
        loadData();
    }

    private void prepararCallbackUbicacion(){
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult.getLastLocation() == null || currentRecolectorId == null) return;
                double lat = locationResult.getLastLocation().getLatitude();
                double lon = locationResult.getLastLocation().getLongitude();
                long ts = System.currentTimeMillis();
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        AppDatabase.getInstance(requireContext()).recolectorDao().updateLocation(currentRecolectorId, lat, lon, ts);
                    } catch (Exception ignored) {}
                });
            }
        };
    }

    private void solicitarPermisoUbicacionYArrancar(){
        tieneFine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        tieneCoarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (tieneFine || tieneCoarse) {
            iniciarLocationUpdates();
        } else {
            permisosMultiLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void iniciarLocationUpdates(){
        if (fusedClient == null || locationCallback == null) return;

        // Verificar permisos antes de intentar usar la ubicación
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            long interval = tieneFine ? 15000L : 30000L;
            float minDist = tieneFine ? 25f : 60f;
            int priority = tieneFine ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            LocationRequest req = new LocationRequest.Builder(priority, interval)
                    .setMinUpdateDistanceMeters(minDist)
                    .build();
            fusedClient.requestLocationUpdates(req, locationCallback, requireActivity().getMainLooper());
        } catch (SecurityException ignored) {
            Toast.makeText(requireContext(), "Error de permisos de ubicación", Toast.LENGTH_SHORT).show();
        }
    }

    private void scheduleWorker(String email){
        if (email == null || email.trim().isEmpty()) return;
        Data data = new Data.Builder().putString(LocationPeriodicWorker.KEY_EMAIL, email).build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(LocationPeriodicWorker.class, 30, java.util.concurrent.TimeUnit.MINUTES)
                .setInputData(data)
                .build();
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork("loc_periodic", ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    private void iniciarServicioForegroundSiAplica(){
        if (currentRecolectorId == null) return;

        // Verificar permisos de ubicación
        boolean fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean bg = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (fine) {
            Intent i = new Intent(requireContext(), TrackingForegroundService.class);
            i.setAction(TrackingForegroundService.ACTION_START);
            i.putExtra("recolectorId", currentRecolectorId);
            try {
                requireContext().startService(i);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Error iniciando servicio de tracking", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void detenerLocationUpdates(){
        if (fusedClient != null && locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
        }
    }

    private void loadData() {
        if (swipe != null) swipe.setRefreshing(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                SessionManager sm = new SessionManager(requireContext());
                String email = sm.getEmail();

                RecolectorDao rdao = db.recolectorDao();
                Recolector rec = (email == null) ? null : rdao.getByUserEmail(email);
                int recolectorId = (rec != null) ? rec.id : 1; // fallback

                String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                List<AsignacionDao.AsignacionDetalle> items =
                        db.asignacionDao().listDetalleByRecolectorFecha(recolectorId, hoy);

                runOnUi(() -> {
                    adapter.setData(items == null ? new ArrayList<>() : items);
                    if (swipe != null) swipe.setRefreshing(false);
                });

            } catch (Exception e) {
                runOnUi(() -> {
                    if (swipe != null) swipe.setRefreshing(false);
                    Toast.makeText(requireContext(), "Error cargando datos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onResume(){
        super.onResume();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SessionManager sm = new SessionManager(requireContext());
                String email = sm.getEmail();
                RecolectorDao rdao = AppDatabase.getInstance(requireContext()).recolectorDao();
                Recolector r = (email == null) ? null : rdao.getByUserEmail(email);
                currentRecolectorId = (r == null) ? null : r.id;
                if (email != null) scheduleWorker(email);
                runOnUi(() -> {
                    solicitarPermisoUbicacionYArrancar();
                    iniciarServicioForegroundSiAplica();
                });
            } catch (Exception e) {
                runOnUi(() -> Toast.makeText(requireContext(), "Error en onResume: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public void onPause(){
        super.onPause();
        detenerLocationUpdates();
        // No detenemos el worker; servicio foreground se puede parar manualmente aparte
    }

    /* ---------------- Adapter interno ---------------- */

    private static class AsignacionesAdapter extends RecyclerView.Adapter<VH> {
        interface OnItemClick { void onClick(int asignacionId); }

        private final List<AsignacionDao.AsignacionDetalle> data = new ArrayList<>();
        private final OnItemClick clickCb;

        AsignacionesAdapter(OnItemClick cb) { this.clickCb = cb; }

        void setData(List<AsignacionDao.AsignacionDetalle> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_asignacion_recolector, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AsignacionDao.AsignacionDetalle m = data.get(pos);

            // Título
            String ord = (m.ordenRuta == null) ? "—" : String.valueOf(m.ordenRuta);
            h.tvTitulo.setText("#" + (pos + 1) + " • " + (m.estado == null ? "—" : m.estado) + " • Orden:" + ord);

            // Subtítulo
            String tam = (m.tamanoPaquete == null ? "—" : m.tamanoPaquete);
            String co  = (m.ciudadOrigen  == null ? "—" : m.ciudadOrigen);
            String cd  = (m.ciudadDestino == null ? "—" : m.ciudadDestino);
            h.tvSub.setText("Paquete " + tam + " — " + co + "  →  " + cd);

            // Columnas (origen/destino/pago/valor)
            h.tvOrigenDir.setText(nn(m.direccion));
            h.tvDestinoDir.setText(nn(m.destinoDir));
            h.tvPago.setText(nn(m.pago));
            h.tvValor.setText(nn(m.valor));

            // Rango hora si viene en el modelo
            String rango = (m.horaDesde == null ? "" : m.horaDesde) + "–" + (m.horaHasta == null ? "" : m.horaHasta);
            h.tvDetalle.setText(rango);

            h.itemView.setOnClickListener(v -> {
                if (clickCb != null) clickCb.onClick(m.id);
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        private static @NonNull String nn(String s) {
            return (s == null || s.trim().isEmpty()) ? "—" : s.trim();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitulo, tvSub, tvOrigenDir, tvDestinoDir, tvPago, tvValor, tvDetalle;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitulo    = itemView.findViewById(R.id.tvTitulo);
            tvSub       = itemView.findViewById(R.id.tvSub);
            tvOrigenDir = itemView.findViewById(R.id.tvOrigenDir);
            tvDestinoDir= itemView.findViewById(R.id.tvDestinoDir);
            tvPago      = itemView.findViewById(R.id.tvPago);
            tvValor     = itemView.findViewById(R.id.tvValor);
            tvDetalle   = itemView.findViewById(R.id.tvDetalle);
        }
    }

    private void runOnUi(Runnable r) {
        if (isAdded()) {
            requireActivity().runOnUiThread(r);
        }
    }
}
