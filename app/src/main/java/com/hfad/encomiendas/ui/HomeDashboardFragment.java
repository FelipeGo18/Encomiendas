package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.SolicitudDao;
import com.hfad.encomiendas.data.TrackingEventDao;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HomeDashboardFragment extends Fragment {

    private SwipeRefreshLayout swRefresh;
    private TextView tvHola, tvCountPendientes, tvCountAsignadas, tvCountRecolectadas, tvEmpty;
    private RecyclerView rvSolicitudes;
    private MaterialButton btnCrear;

    private UltimasAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        swRefresh          = v.findViewById(R.id.swRefresh);
        tvHola             = v.findViewById(R.id.tvHola);
        tvCountPendientes  = v.findViewById(R.id.tvCountPendientes);
        tvCountAsignadas   = v.findViewById(R.id.tvCountAsignadas);
        tvCountRecolectadas= v.findViewById(R.id.tvCountRecolectadas);
        tvEmpty            = v.findViewById(R.id.tvEmpty);
        rvSolicitudes      = v.findViewById(R.id.rvSolicitudes);
        btnCrear           = v.findViewById(R.id.btnCrear);

        rvSolicitudes.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new UltimasAdapter(AppDatabase.getInstance(requireContext()));
        rvSolicitudes.setAdapter(adapter);

        SessionManager sm = new SessionManager(requireContext());
        if (!TextUtils.isEmpty(sm.getEmail())) {
            tvHola.setText("¡Hola, " + sm.getEmail() + "!");
        }

        if (swRefresh != null) swRefresh.setOnRefreshListener(this::cargarPanel);
        if (btnCrear != null) {
            btnCrear.setOnClickListener(vw -> {
                NavController nav = NavHostFragment.findNavController(this);
                try { nav.navigate(R.id.action_home_to_solicitar); }
                catch (Exception ignore) { nav.navigate(R.id.solicitarRecoleccionFragment); }
            });
        }

        cargarPanel();
    }

    private void cargarPanel() {
        if (swRefresh != null) swRefresh.setRefreshing(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                SessionManager sm = new SessionManager(requireContext());
                String email = sm.getEmail();
                if (TextUtils.isEmpty(email)) {
                    runOnUi(() -> {
                        if (swRefresh != null) swRefresh.setRefreshing(false);
                        Toast.makeText(requireContext(), "Sesión no válida", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                com.hfad.encomiendas.data.User u = db.userDao().findByEmail(email);
                if (u == null) {
                    runOnUi(() -> {
                        if (swRefresh != null) swRefresh.setRefreshing(false);
                        Toast.makeText(requireContext(), "Usuario no encontrado", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                int cPend = db.solicitudDao().countByEstado(u.id, "PENDIENTE");
                int cAsig = db.solicitudDao().countByEstado(u.id, "ASIGNADA");
                int cReco = db.solicitudDao().countByEstado(u.id, "RECOLECTADA");

                List<SolicitudDao.SolicitudConEta> ultimas =
                        db.solicitudDao().listAllByUserWithEta(u.id, 20);

                runOnUi(() -> {
                    tvCountPendientes.setText(String.valueOf(cPend));
                    tvCountAsignadas.setText(String.valueOf(cAsig));
                    tvCountRecolectadas.setText(String.valueOf(cReco));

                    adapter.setData(ultimas == null ? new ArrayList<>() : ultimas);
                    tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    if (swRefresh != null) swRefresh.setRefreshing(false);
                });

            } catch (Exception e) {
                runOnUi(() -> {
                    if (swRefresh != null) swRefresh.setRefreshing(false);
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /* ==================== Adapter ==================== */

    private static class UltimasAdapter extends RecyclerView.Adapter<VH> {
        private final List<SolicitudDao.SolicitudConEta> data = new ArrayList<>();
        private final SimpleDateFormat dfFecha = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        private final SimpleDateFormat dfHora  = new SimpleDateFormat("h:mm a", Locale.getDefault());
        private final AppDatabase db;

        UltimasAdapter(AppDatabase db) { this.db = db; }

        void setData(List<SolicitudDao.SolicitudConEta> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_solicitud_dashboard, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            SolicitudDao.SolicitudConEta it = data.get(position);
            Solicitud s = it.s;

            h.tvGuiaEstado.setText(nn(s.guia) + "  •  " + nn(s.estado));

            String fecha = dfFecha.format(new Date(s.ventanaInicioMillis));
            String hIni  = dfHora.format(new Date(s.ventanaInicioMillis));
            String hFin  = dfHora.format(new Date(s.ventanaFinMillis));
            h.tvRango.setText(fecha + ", " + hIni + " - " + hFin);

            h.tvEta.setText("ETA: " + prettyEta(it.eta));
            h.tvDireccion.setText( normalizeAddress(s.direccion) );

            String destino = firstNonEmpty(meta(s.notas, "DestinoDir"), meta(s.notas, "Destino"));
            h.tvDestino.setText("Destino: " + nn(destino));

            h.tvUbicacion.setText("Ubicación: —");
            long sid = s.id;
            Executors.newSingleThreadExecutor().execute(() -> {
                TrackingEventDao.LastLoc loc = db.trackingEventDao().lastLocationForShipment(sid);
                String txt = "—";
                if (loc != null && loc.lat != null && loc.lon != null) {
                    txt = String.format(Locale.getDefault(), "%.5f, %.5f", loc.lat, loc.lon);
                }
                final String fin = "Ubicación: " + txt;
                h.itemView.post(() -> h.tvUbicacion.setText(fin));
            });
        }

        @Override public int getItemCount() { return data.size(); }

        private static String nn(String s) { return (s == null || s.trim().isEmpty()) ? "—" : s.trim(); }

        private static String meta(String notas, String key) {
            if (notas == null) return "";
            Pattern p = Pattern.compile(
                    "\\b" + Pattern.quote(key) + "\\s*:\\s*(.*?)\\s*(?:\\|\\s*|(?=[A-ZÁÉÍÓÚÑ][\\wÁÉÍÓÚÑáéíóúñ ]*:\\s*)|$)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
            Matcher m = p.matcher(notas);
            if (m.find()) return m.group(1).trim();

            Pattern p2 = Pattern.compile("\\b" + Pattern.quote(key) + "\\s*:\\s*(.*)$",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m2 = p2.matcher(notas);
            return m2.find() ? m2.group(1).trim() : "";
        }

        private static String firstNonEmpty(String... arr) {
            if (arr == null) return "";
            for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
            return "";
        }

        private static String normalizeAddress(String raw) {
            if (raw == null) return "—";
            String[] parts = raw.split(",");
            List<String> cleaned = new ArrayList<>();
            Set<String> seenCanon = new LinkedHashSet<>();
            String prevCanon = null;

            for (String p : parts) {
                String t = p.trim();
                if (t.isEmpty()) continue;
                String canon = Normalizer.normalize(t, Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                        .replaceAll("[^a-zA-Z\\s]", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("\\s+", " ")
                        .trim();
                if (prevCanon != null && (canon.equals(prevCanon) ||
                        canon.startsWith(prevCanon) || prevCanon.startsWith(canon))) continue;
                if (seenCanon.contains(canon)) continue;
                cleaned.add(t);
                seenCanon.add(canon);
                prevCanon = canon;
            }
            return cleaned.isEmpty() ? raw.trim() : TextUtils.join(", ", cleaned);
        }

        private static String prettyEta(String iso){
            if (iso == null || iso.trim().isEmpty()) return "—";
            int t = iso.indexOf('T');
            if (t >= 0 && iso.length() >= t + 6) return iso.substring(t + 1, t + 6);
            return iso;
        }
    }

    private static class VH extends RecyclerView.ViewHolder {
        TextView tvGuiaEstado, tvRango, tvEta, tvDireccion, tvDestino, tvUbicacion;
        VH(@NonNull View itemView) {
            super(itemView);
            tvGuiaEstado = itemView.findViewById(R.id.tvGuiaEstado);
            tvRango      = itemView.findViewById(R.id.tvRango);
            tvEta        = itemView.findViewById(R.id.tvEta);
            tvDireccion  = itemView.findViewById(R.id.tvDireccion);
            tvDestino    = itemView.findViewById(R.id.tvDestino);
            tvUbicacion  = itemView.findViewById(R.id.tvUbicacion);
        }
    }

    private void runOnUi(Runnable r) { if (!isAdded()) return; requireActivity().runOnUiThread(r); }
}
