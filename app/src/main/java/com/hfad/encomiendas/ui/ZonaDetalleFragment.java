package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.AsignadorService;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.SolicitudDao;
import com.hfad.encomiendas.ui.adapters.PendienteDetalleAdapter;
import com.hfad.encomiendas.ui.adapters.ZonaDetalleAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ZonaDetalleFragment extends Fragment implements OnMapReadyCallback {

    private String fecha;
    private String zona;

    private TextView tvTitulo;
    private RecyclerView rvPendientes, rvAsignadas;
    private PendienteDetalleAdapter pendientesAdapter;
    private ZonaDetalleAdapter asignadasAdapter;
    private MaterialButton btnGenerarRuta;

    private GoogleMap map;

    public ZonaDetalleFragment() {}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_zona_detalle, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        fecha = (getArguments() != null) ? getArguments().getString("fecha") : null;
        zona  = (getArguments() != null) ? getArguments().getString("zona")  : null;

        tvTitulo       = v.findViewById(R.id.tvTituloZona);
        rvPendientes   = v.findViewById(R.id.rvPendientes);
        rvAsignadas    = v.findViewById(R.id.rvAsignadas);
        btnGenerarRuta = v.findViewById(R.id.btnGenerarRuta);

        rvPendientes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvAsignadas.setLayoutManager(new LinearLayoutManager(requireContext()));

        pendientesAdapter = new PendienteDetalleAdapter();
        asignadasAdapter = new ZonaDetalleAdapter(det -> {
            Bundle b = new Bundle();
            b.putInt("asignacionId", det.id);
            androidx.navigation.Navigation.findNavController(v)
                    .navigate(R.id.detalleRecoleccionFragment, b);
        });

        rvPendientes.setAdapter(pendientesAdapter);
        rvAsignadas.setAdapter(asignadasAdapter);

        tvTitulo.setText("Zona: " + (zona == null ? "—" : zona));

        // Mapa
        SupportMapFragment mf = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map_container);
        if (mf == null) {
            mf = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.map_container, mf)
                    .commitNow();
        }
        mf.getMapAsync(this);

        // CLICK: Generar ruta
        if (btnGenerarRuta != null) {
            btnGenerarRuta.setOnClickListener(view -> generarRuta());
        }

        cargarListas();
    }

    @Override public void onResume() {
        super.onResume();
        cargarMapa();
    }

    // ================== ACCIÓN: GENERAR RUTA ==================
    private void generarRuta() {
        if (TextUtils.isEmpty(fecha) || TextUtils.isEmpty(zona)) {
            toast("Faltan argumentos de fecha/zona");
            return;
        }
        btnGenerarRuta.setEnabled(false);
        btnGenerarRuta.setText("Generando…");

        final String fFecha = fecha;
        final String fZona  = zona;

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int n = new AsignadorService(requireContext())
                        .generarRutasParaFechaZona(fFecha, fZona);

                runOnUi(() -> {
                    toast(n == 0
                            ? "No había pendientes con coordenadas en esta zona."
                            : "Asignadas " + n + " recolecciones.");
                    cargarListas();
                    cargarMapa();
                });
            } catch (Exception e) {
                runOnUi(() -> toast("Error: " + e.getMessage()));
            } finally {
                runOnUi(() -> {
                    btnGenerarRuta.setEnabled(true);
                    btnGenerarRuta.setText("Generar ruta");
                });
            }
        });
    }

    // ================== LISTAS ==================
    private void cargarListas() {
        if (TextUtils.isEmpty(fecha) || TextUtils.isEmpty(zona)) { toast("Argumentos inválidos"); return; }
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            List<SolicitudDao.PendienteDetalle> pend =
                    db.solicitudDao().listPendienteDetalleFullByZonaAndFecha(zona, fecha);
            List<AsignacionDao.AsignacionDetalle> asig =
                    db.asignacionDao().listDetalleByFechaZona(fecha, zona);
            runOnUi(() -> {
                pendientesAdapter.setData(pend);
                asignadasAdapter.setData(asig);
            });
        });
    }

    // ================== MAPA ==================
    @Override public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        cargarMapa();
    }

    private void cargarMapa() {
        if (map == null || TextUtils.isEmpty(fecha) || TextUtils.isEmpty(zona)) return;
        map.clear();

        final String fFecha = fecha;
        final String fZona  = zona;

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            final List<Solicitud> pendientesLL =
                    db.solicitudDao().listUnassignedByFechaZona(fFecha, fZona == null ? "" : fZona);
            final List<AsignacionDao.RutaPunto> ruta =
                    db.asignacionDao().rutaByFechaZona(fFecha, fZona == null ? "" : fZona);

            Log.d("ZONA", "pendientes=" + (pendientesLL==null?0:pendientesLL.size()) +
                    " ruta=" + (ruta==null?0:ruta.size()));

            runOnUi(() -> {
                LatLngBounds.Builder bounds = new LatLngBounds.Builder();
                boolean hasBounds = false;

                // Naranja: pendientes
                if (pendientesLL != null) for (Solicitud s : pendientesLL) {
                    if (s.lat == null || s.lon == null) continue;
                    LatLng p = new LatLng(s.lat, s.lon);
                    map.addMarker(new MarkerOptions()
                            .position(p)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                            .title("Pendiente")
                            .snippet(shortDir(s.direccion)));
                    bounds.include(p);
                    hasBounds = true;
                }

                // Azul: ruta asignada
                List<LatLng> poly = new ArrayList<>();
                if (ruta != null) for (AsignacionDao.RutaPunto rp : ruta) {
                    if (rp.lat == null || rp.lon == null) continue;
                    LatLng p = new LatLng(rp.lat, rp.lon);
                    String title = "Parada " + (rp.orden == null ? "?" : rp.orden);
                    map.addMarker(new MarkerOptions().position(p).title(title).snippet(shortDir(rp.direccion)));
                    poly.add(p);
                    bounds.include(p);
                    hasBounds = true;
                }
                if (poly.size() >= 2) {
                    map.addPolyline(new PolylineOptions().addAll(poly).width(8f));
                }

                if (hasBounds) {
                    try { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120)); }
                    catch (Exception ignore) {}
                } else {
                    // Bogotá por defecto
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(4.65, -74.1), 11f));
                }
            });
        });
    }

    private String shortDir(String d) {
        if (TextUtils.isEmpty(d)) return "—";
        return d.length() <= 60 ? d : d.substring(0, 57) + "...";
    }
    private void runOnUi(Runnable r) { if (!isAdded()) return; requireActivity().runOnUiThread(r); }
    private void toast(String t) { if (!isAdded()) return; Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show(); }
}