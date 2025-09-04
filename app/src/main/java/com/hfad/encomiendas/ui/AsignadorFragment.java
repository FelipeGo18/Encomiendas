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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Recolector;
import com.hfad.encomiendas.data.AsignacionDao.ZonaAsignada;   // <-- IMPORT CORRECTO (clase anidada)
import com.hfad.encomiendas.data.ZonaPendiente;                // <-- si tu ZonaPendiente es top-level; si es anidada en SolicitudDao, cámbialo
import com.hfad.encomiendas.ui.adapters.ZonaStatAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class                                                                                   AsignadorFragment extends Fragment {

    private TextInputEditText etFecha;
    private TextView tvResumen;
    private RecyclerView rvZonas;
    private ZonaStatAdapter zonasAdapter;

    public AsignadorFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_asignador, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etFecha   = view.findViewById(R.id.etFechaAsignador);
        tvResumen = view.findViewById(R.id.tvResumen);
        rvZonas   = view.findViewById(R.id.rvZonas);

        rvZonas.setLayoutManager(new LinearLayoutManager(requireContext()));
        zonasAdapter = new ZonaStatAdapter(new ZonaStatAdapter.Listener() {
            @Override public void onItemClick(ZonaStatAdapter.ZonaItem item) {
                // Tap en tarjeta -> ver detalle de esa zona
                Bundle b = new Bundle();
                b.putString("fecha", textOf(etFecha));
                b.putString("zona", item.zona);
                androidx.navigation.Navigation.findNavController(requireView())
                        .navigate(R.id.zonaDetalleFragment, b);
            }
            @Override public void onAsignarZona(ZonaStatAdapter.ZonaItem item) {
                asignarZona(item.zona);
            }
        });
        rvZonas.setAdapter(zonasAdapter);

        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        etFecha.setText(hoy);

        // Botón demo (si existe en el layout)
        MaterialButton btnCargarDemo = view.findViewById(R.id.btnCargarDemo);
        if (btnCargarDemo != null) {
            btnCargarDemo.setOnClickListener(v -> cargarDemoParaFecha(textOf(etFecha)));
        }

        // Ocultar botón legacy "btnGenerarRutas" si estuviera en algún layout anterior,
        // sin referenciar R.id directamente (evita error si no existe).
        int legacyId = getResources().getIdentifier("btnGenerarRutas", "id", requireContext().getPackageName());
        if (legacyId != 0) {
            View legacy = view.findViewById(legacyId);
            if (legacy != null) legacy.setVisibility(View.GONE);
        }

        refrescarResumenYMapa(textOf(etFecha));
    }

    private void asignarZona(String zona) {
        final String fecha = textOf(etFecha);
        if (TextUtils.isEmpty(fecha) || TextUtils.isEmpty(zona)) {
            toast("Fecha/zona inválida"); return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            com.hfad.encomiendas.core.AsignadorService svc =
                    new com.hfad.encomiendas.core.AsignadorService(requireContext());
            int n = svc.generarRutasParaFechaZona(fecha, zona);
            runOnUi(() -> {
                toast("Asignadas " + n + " en " + zona);
                refrescarResumenYMapa(fecha);
            });
        });
    }

    private void cargarDemoParaFecha(String fecha) {
        Executors.newSingleThreadExecutor().execute(() -> {
            com.hfad.encomiendas.core.DemoSeeder.seed(requireContext());
            runOnUi(() -> {
                toast("Demo cargada para hoy (si no existía)");
                refrescarResumenYMapa(fecha);
            });
        });
    }

    private void refrescarResumenYMapa(String fecha) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());

            int pendientes = db.solicitudDao().countUnassignedByFecha(fecha);
            int asignadas  = db.asignacionDao().countByFecha(fecha);

            StringBuilder sb = new StringBuilder();
            sb.append("Fecha ").append(fecha)
                    .append("\nPendientes: ").append(pendientes)
                    .append("\nAsignadas: ").append(asignadas);

            List<Recolector> recs = db.recolectorDao().listAll();
            for (Recolector r : recs) {
                int c = db.asignacionDao().countByFechaAndRecolector(fecha, r.id);
                sb.append("\n• ").append(r.nombre)
                        .append(" (").append(nn(r.zona)).append(" / ").append(nn(r.vehiculo)).append(")")
                        .append(": ").append(c);
            }
            String txtResumen = sb.toString();

            // OJO: aquí usamos la clase anidada del DAO para que coincidan los tipos
            List<ZonaAsignada> listA = db.asignacionDao().countAsignadasPorZona(fecha);
            List<ZonaPendiente> listP = db.solicitudDao().countPendientesPorZona(fecha);

            Map<String, ZonaStatAdapter.ZonaItem> map = new LinkedHashMap<>();
            if (listA != null) {
                for (ZonaAsignada za : listA) {
                    String z = norm(za.zona);
                    map.put(z, new ZonaStatAdapter.ZonaItem(z, za.asignadas, 0));
                }
            }
            if (listP != null) {
                for (ZonaPendiente zp : listP) {
                    String z = norm(zp.zona);
                    ZonaStatAdapter.ZonaItem it = map.get(z);
                    if (it == null) it = new ZonaStatAdapter.ZonaItem(z, 0, zp.pendientes);
                    else it.pendientes = zp.pendientes;
                    map.put(z, it);
                }
            }

            List<ZonaStatAdapter.ZonaItem> items = new ArrayList<>(map.values());
            items.sort((o1, o2) -> Integer.compare(o2.pendientes, o1.pendientes));

            runOnUi(() -> {
                tvResumen.setText(txtResumen);
                zonasAdapter.setData(items);
            });
        });
    }

    // utils
    private String textOf(TextInputEditText et) { return et.getText() == null ? "" : et.getText().toString().trim(); }
    private void runOnUi(Runnable r) { if (isAdded()) requireActivity().runOnUiThread(r); }
    private void toast(String msg) { if (isAdded()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show(); }
    private String nn(String s) { return (s == null || s.trim().isEmpty()) ? "—" : s.trim(); }
    private String norm(String s) { return (s == null || s.trim().isEmpty()) ? "(sin zona)" : s.trim(); }
}
