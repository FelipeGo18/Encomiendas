package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.material.button.MaterialButton;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.AsignadorService;
import com.hfad.encomiendas.core.GeoUtils;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.SolicitudDao;
import com.hfad.encomiendas.data.Zone;
import com.hfad.encomiendas.ui.adapters.PendienteDetalleAdapter;
import com.hfad.encomiendas.ui.adapters.ZonaDetalleAdapter;
import com.google.maps.android.clustering.ClusterManager;
import com.hfad.encomiendas.ui.ZonaClusterItem.Tipo;
import com.hfad.encomiendas.core.TrackingService;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import com.hfad.encomiendas.data.RecolectorDao;

public class ZonaDetalleFragment extends Fragment implements OnMapReadyCallback {

    private String fecha;
    private String zona;
    private long zoneId = -1L;

    private TextView tvTitulo;
    private RecyclerView rvPendientes, rvAsignadas;
    private PendienteDetalleAdapter pendientesAdapter;
    private ZonaDetalleAdapter asignadasAdapter;
    private MaterialButton btnGenerarRuta;
    private MaterialButton btnGenerarRutaPoligono; // nuevo
    private GoogleMap map;
    private Polygon drawnPolygon; // referencia
    private ClusterManager<ZonaClusterItem> clusterManager;
    private FloatingActionButton btnFollow;
    private FloatingActionButton btnExpandMap; // Nuevo botón para expandir mapa
    private boolean followEnabled = false;
    private LatLng lastFollowLatLng = null;
    private int followedRecolectorId = -1;
    private TextView tvInfoMini; // para ETA / resumen rápido

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            cargarMapa();
            refreshHandler.postDelayed(this, 20000); // refresco cada 20s
        }
    };

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
        zoneId = (getArguments()!=null) ? (long) getArguments().getInt("zoneId", -1) : -1L;

        tvTitulo       = v.findViewById(R.id.tvTituloZona);
        rvPendientes   = v.findViewById(R.id.rvPendientes);
        rvAsignadas    = v.findViewById(R.id.rvAsignadas);
        btnGenerarRuta = v.findViewById(R.id.btnGenerarRuta);
        btnGenerarRutaPoligono = v.findViewById(R.id.btnGenerarRutaPoligono);
        tvInfoMini = v.findViewById(R.id.tvInfoMini);
        btnFollow = v.findViewById(R.id.btnFollow);
        btnExpandMap = v.findViewById(R.id.btnExpandMap); // Nuevo botón para expandir mapa

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
        if (btnGenerarRutaPoligono != null) {
            btnGenerarRutaPoligono.setOnClickListener(view -> abrirPreviewPoligono());
            btnGenerarRutaPoligono.setVisibility(View.GONE); // se mostrará tras cargar polígono válido
        }
        FrameLayout mapContainer = v.findViewById(R.id.map_container);
        if (mapContainer != null) {
            mapContainer.setOnClickListener(_v -> abrirMapaFullscreen());
            mapContainer.setForeground(requireContext().getDrawable(android.R.drawable.list_selector_background));
        }
        if (btnFollow != null) btnFollow.setOnClickListener(_v -> toggleFollow());

        // Botón para expandir mapa a pantalla completa
        if (btnExpandMap != null) {
            btnExpandMap.setOnClickListener(_v -> abrirMapaFullscreen());
            btnExpandMap.setContentDescription("Expandir mapa a pantalla completa");
        }

        cargarListas();
    }

    private void abrirMapaFullscreen(){
        Bundle b = new Bundle();
        b.putString("fecha", fecha);
        b.putString("zona", zona);
        b.putInt("zoneId", (int) zoneId);
        if (isAdded()) androidx.navigation.Navigation.findNavController(requireView())
                .navigate(R.id.zonaMapaFullFragment, b);
    }

    @Override public void onResume() {
        super.onResume();
        cargarMapa();
        refreshHandler.postDelayed(refreshTask, 20000);
    }

    @Override public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshTask);
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

    private void abrirPreviewPoligono(){
        if (zoneId <= 0) { toast("Zona sin polígono"); return; }
        Bundle b = new Bundle();
        b.putInt("zoneId", (int) zoneId);
        b.putString("fecha", fecha);
        androidx.navigation.Navigation.findNavController(requireView())
                .navigate(R.id.poligonoRutaPreviewFragment, b);
    }

    private void maybeEnablePoligonoButton(boolean enable){
        if (btnGenerarRutaPoligono != null) btnGenerarRutaPoligono.setVisibility(enable?View.VISIBLE:View.GONE);
    }

    private void dibujarPoligonoZonaIfAny(){
        if (map == null || zoneId <= 0) { maybeEnablePoligonoButton(false); return; }
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            Zone z = db.zoneDao().getById(zoneId);
            if (z == null) { runOnUi(() -> maybeEnablePoligonoButton(false)); return; }
            List<LatLng> pts = GeoUtils.jsonToPolygon(z.polygonJson);
            boolean enable = pts.size() >= 3;
            runOnUi(() -> {
                if (enable) {
                    // dibujar
                    if (drawnPolygon != null) drawnPolygon.remove();
                    PolygonOptions opts = new PolygonOptions().addAll(pts)
                            .strokeColor(0xFF673AB7).strokeWidth(6f).fillColor(0x33673AB7);
                    try {
                        drawnPolygon = map.addPolygon(opts);
                    } catch (Exception ignore) {}
                    // ajustar cámara si no hay markers todavía
                    try {
                        LatLngBounds.Builder b = new LatLngBounds.Builder();
                        for (LatLng p: pts) b.include(p);
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120));
                    } catch (Exception ignore) {}
                }
                maybeEnablePoligonoButton(enable);
            });
        });
    }

    private void toggleFollow(){
        followEnabled = !followEnabled;
        if (btnFollow != null) {
            btnFollow.setContentDescription(followEnabled? getString(R.string.follow_on): getString(R.string.follow_off));
            btnFollow.setAlpha(followEnabled?1f:0.6f);
        }
        if (followEnabled && lastFollowLatLng != null && map != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(lastFollowLatLng, map.getCameraPosition().zoom));
        }
    }

    private void initClusterIfNeeded(){
        if (map == null || clusterManager != null) return;
        clusterManager = new ClusterManager<>(requireContext(), map);
        map.setOnCameraIdleListener(clusterManager);
        map.setOnMarkerClickListener(clusterManager);
        map.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE && followEnabled) {
                followEnabled = false; if (btnFollow!=null) { btnFollow.setAlpha(0.6f); btnFollow.setContentDescription(getString(R.string.follow_off)); }
            }
        });
    }

    private void cargarMapa() {
        if (map == null || TextUtils.isEmpty(fecha) || TextUtils.isEmpty(zona)) return;
        map.clear(); drawnPolygon = null; clusterManager = null; // rebuild cluster
        initClusterIfNeeded();
        final String fFecha = fecha;
        final String fZona  = zona;
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            final List<Solicitud> pendientesLL = db.solicitudDao().listUnassignedByFechaZona(fFecha, fZona == null ? "" : fZona);
            final List<AsignacionDao.RutaPunto> ruta = db.asignacionDao().rutaByFechaZona(fFecha, fZona == null ? "" : fZona);
            final List<RecolectorDao.RecolectorPos> recs = db.recolectorDao().positionsByZona(fZona == null? "" : fZona);
            runOnUi(() -> dibujarTodo(pendientesLL, ruta, recs));
        });
    }

    private void dibujarTodo(List<Solicitud> pendientesLL, List<AsignacionDao.RutaPunto> ruta, List<RecolectorDao.RecolectorPos> recs){
        initClusterIfNeeded();
        if (clusterManager == null) return;
        clusterManager.clearItems();
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        boolean hasBounds = false;
        List<LatLng> routePoints = new ArrayList<>();

        // Pendientes
        if (pendientesLL != null) for (Solicitud s: pendientesLL){
            if (s.lat==null||s.lon==null) continue; LatLng p = new LatLng(s.lat,s.lon);
            clusterManager.addItem(new ZonaClusterItem(p, "Pendiente", shortDir(s.direccion), Tipo.PENDIENTE));
            bounds.include(p); hasBounds=true;
        }

        // Ruta - recopilar puntos ordenados
        if (ruta != null) for (AsignacionDao.RutaPunto rp: ruta){
            if (rp.lat==null||rp.lon==null) continue; LatLng p = new LatLng(rp.lat,rp.lon);
            String title = "Parada "+ (rp.orden==null?"?":rp.orden);
            clusterManager.addItem(new ZonaClusterItem(p, title, shortDir(rp.direccion), Tipo.PARADA));
            routePoints.add(p); bounds.include(p); hasBounds=true;
        }

        // Dibujar ruta real usando Google Directions API
        if (routePoints.size() >= 2) {
            dibujarRutaReal(routePoints);
        }

        // Recolectores y conexión al primer punto
        LatLng firstStop = routePoints.isEmpty()? null : routePoints.get(0);
        double bestDistKm = Double.MAX_VALUE; LatLng bestPos = null; int bestId = -1;
        if (recs != null) for (RecolectorDao.RecolectorPos r: recs){
            if (r.lat==null||r.lon==null) continue; LatLng pr = new LatLng(r.lat,r.lon);
            clusterManager.addItem(new ZonaClusterItem(pr, "Recolector #"+r.id, null, Tipo.RECOLECTOR));
            bounds.include(pr); hasBounds=true;
            if (firstStop!=null){
                // Usar ruta real para conectar recolector con primera parada
                dibujarRutaRecolectorAPrimeraParada(pr, firstStop);
                double d = TrackingService.haversine(pr.latitude, pr.longitude, firstStop.latitude, firstStop.longitude);
                if (d < bestDistKm){ bestDistKm = d; bestPos = pr; bestId = r.id; }
            }
        }
        // Polígono y botón
        dibujarPoligonoZonaIfAny();

        // ETA
        if (tvInfoMini != null){
            if (bestPos != null){
                double speedKmh = 25.0; // asumido
                double hours = bestDistKm / speedKmh; long etaMin = Math.round(hours * 60);
                tvInfoMini.setText("ETA ~"+etaMin+" min / Dist " + String.format(java.util.Locale.getDefault(),"%.1f km", bestDistKm));
            } else tvInfoMini.setText("");
        }
        if (followEnabled && bestPos != null){ lastFollowLatLng = bestPos; followedRecolectorId = bestId; map.animateCamera(CameraUpdateFactory.newLatLng(bestPos)); }
        else if (bestPos != null){ lastFollowLatLng = bestPos; followedRecolectorId = bestId; }

        // Ajustar cámara
        if (hasBounds){
            try { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120)); } catch (Exception ignore) {}
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(4.65, -74.1), 11f));
        }
        clusterManager.cluster();
    }

    /**
     * Dibuja una ruta real que sigue las calles entre múltiples puntos
     */
    private void dibujarRutaReal(List<LatLng> routePoints) {
        if (routePoints.size() < 2) return;

        // Si solo hay 2 puntos, hacer una ruta directa
        if (routePoints.size() == 2) {
            com.hfad.encomiendas.core.DirectionsHelper.getRoute(
                routePoints.get(0),
                routePoints.get(1),
                new com.hfad.encomiendas.core.DirectionsHelper.DirectionsCallback() {
                    @Override
                    public void onRouteReady(PolylineOptions polylineOptions, String duration, String distance) {
                        runOnUi(() -> {
                            if (map != null) {
                                map.addPolyline(polylineOptions);
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.w("ZonaDetalle", "Error obteniendo ruta: " + error);
                        // Fallback a línea recta
                        runOnUi(() -> {
                            if (map != null) {
                                map.addPolyline(new PolylineOptions()
                                    .addAll(routePoints)
                                    .width(8f)
                                    .color(0xFF2196F3)
                                    .pattern(java.util.Arrays.asList(
                                        new com.google.android.gms.maps.model.Dash(20),
                                        new com.google.android.gms.maps.model.Gap(10)
                                    )));
                            }
                        });
                    }
                }
            );
        } else {
            // Para múltiples puntos, usar ruta optimizada
            LatLng origin = routePoints.get(0);
            LatLng destination = routePoints.get(routePoints.size() - 1);
            List<LatLng> waypoints = routePoints.subList(1, routePoints.size() - 1);

            com.hfad.encomiendas.core.DirectionsHelper.getOptimizedRoute(
                origin, waypoints, destination,
                new com.hfad.encomiendas.core.DirectionsHelper.DirectionsCallback() {
                    @Override
                    public void onRouteReady(PolylineOptions polylineOptions, String duration, String distance) {
                        runOnUi(() -> {
                            if (map != null) {
                                map.addPolyline(polylineOptions);
                                // Actualizar info con datos reales
                                if (tvInfoMini != null) {
                                    String currentText = tvInfoMini.getText().toString();
                                    tvInfoMini.setText(currentText + " | Ruta: " + duration + " / " + distance);
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.w("ZonaDetalle", "Error obteniendo ruta optimizada: " + error);
                        // Fallback a línea recta conectando todos los puntos
                        runOnUi(() -> {
                            if (map != null) {
                                map.addPolyline(new PolylineOptions()
                                    .addAll(routePoints)
                                    .width(8f)
                                    .color(0xFF2196F3)
                                    .pattern(java.util.Arrays.asList(
                                        new com.google.android.gms.maps.model.Dash(20),
                                        new com.google.android.gms.maps.model.Gap(10)
                                    )));
                            }
                        });
                    }
                }
            );
        }
    }

    /**
     * Dibuja una ruta real desde la posición del recolector hasta la primera parada
     */
    private void dibujarRutaRecolectorAPrimeraParada(LatLng recolectorPos, LatLng primeraParada) {
        com.hfad.encomiendas.core.DirectionsHelper.getRoute(
            recolectorPos,
            primeraParada,
            new com.hfad.encomiendas.core.DirectionsHelper.DirectionsCallback() {
                @Override
                public void onRouteReady(PolylineOptions polylineOptions, String duration, String distance) {
                    runOnUi(() -> {
                        if (map != null) {
                            // Personalizar la polyline para la ruta del recolector
                            polylineOptions.width(6f).color(0xFF4CAF50);
                            map.addPolyline(polylineOptions);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    Log.w("ZonaDetalle", "Error obteniendo ruta del recolector: " + error);
                    // Fallback a línea recta punteada
                    runOnUi(() -> {
                        if (map != null) {
                            map.addPolyline(new PolylineOptions()
                                .add(recolectorPos, primeraParada)
                                .width(6f)
                                .color(0xFF4CAF50)
                                .pattern(java.util.Arrays.asList(
                                    new com.google.android.gms.maps.model.Dash(15),
                                    new com.google.android.gms.maps.model.Gap(8)
                                )));
                        }
                    });
                }
            }
        );
    }
    private String shortDir(String d) {
        if (TextUtils.isEmpty(d)) return "—";
        return d.length() <= 60 ? d : d.substring(0, 57) + "...";
    }
    private void runOnUi(Runnable r) { if (!isAdded()) return; requireActivity().runOnUiThread(r); }
    private void toast(String t) { if (!isAdded()) return; Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show(); }
}
