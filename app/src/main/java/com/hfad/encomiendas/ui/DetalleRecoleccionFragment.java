package com.hfad.encomiendas.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.location.LocationManager;
import android.text.TextUtils;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.TrackingService;
import androidx.navigation.fragment.NavHostFragment;
import com.hfad.encomiendas.data.Asignacion;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.ui.adapters.TrackingAdapter;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.hfad.encomiendas.BuildConfig; // para MAPS_API_KEY
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
    // UI encabezado
    private TextView tvTitulo, tvSub, tvEstado;

    // UI seguimiento (US-08)
    private TextView tvEta;
    private RecyclerView rvTimeline;
    private TrackingAdapter trackingAdapter;
import com.hfad.encomiendas.data.TrackingEventDao; // para LastLoc

    // UI foto / firma
    private ImageView ivFoto;
    private MaterialButton btnTomarFoto, btnEditarFoto, btnConfirmarFoto, btnSoloEditar;
    private View llEditarConfirmar, llSoloEditar;

    private ImageView ivFirmaPreview;
    private SignatureView signView;
    private MaterialButton btnGuardarFirma, btnLimpiar;

    // Estado
    private boolean guiaActiva = false;
    private Integer ordenRuta = null;
    private String estado = "";

    private long solicitudId = -1;
    private Double destinoLat = null, destinoLon = null; // opcional si tu Solicitud tiene coords

    // Foto
    private Uri pendingPhotoUri = null;    // tomada pero no confirmada
    private String savedPhotoUri = null;   // confirmada (DB)

    // Firma
    private String firmaB64 = null;

    // Permisos/cámara
    private ActivityResultLauncher<String> reqPermission;
    private ActivityResultLauncher<Uri> takePicture;

    // Ubicación
    private FusedLocationProviderClient fused;

    public DetalleRecoleccionFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detalle_recoleccion, container, false);
    // Mapa recolector
    private MapView mapViewRecolector;
    private GoogleMap googleMap;
    private Marker markerRecolector;
    private Marker markerDestino;
    private Double lastRecolectorLat = null, lastRecolectorLon = null;
    private Handler mapHandler = new Handler(Looper.getMainLooper());
    private final long MAP_REFRESH_MS = 30_000; // 30s
    private final Runnable mapRefreshTask = new Runnable() {
        @Override public void run() {
            cargarUltimaUbicacionRecolector(false);
            mapHandler.postDelayed(this, MAP_REFRESH_MS);
        }
    };

                updatePhotoButtons();
            } else {
                pendingPhotoUri = null;
                toast("No se tomó la foto");
                updatePhotoButtons();
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // Encabezado
        tvTitulo = v.findViewById(R.id.tvTitulo);
        tvSub    = v.findViewById(R.id.tvSub);
        tvEstado = v.findViewById(R.id.tvEstado);

        // Seguimiento (ETA + timeline)
        tvEta = v.findViewById(R.id.tvEta);
        rvTimeline = v.findViewById(R.id.rvTimeline);
        if (rvTimeline != null) {
    // --- Ruta ---
    private Polyline routePolyline;
    private boolean routeRequested = false; // evita múltiples fetch seguidos
    private Double routeFromLat = null, routeFromLon = null; // para detectar cambios

    private static final double BOGOTA_LAT = 4.7110;
    private static final double BOGOTA_LON = -74.0721;
    private boolean initialCameraSet = false;

    private ActivityResultLauncher<String> locationPermLauncher;
    private ActivityResultLauncher<Intent> locationSettingsLauncher;
    private ActivityResultLauncher<String> notifPermLauncher;
    private Runnable pendingLocationAction; // se ejecuta tras obtener permisos y GPS activo

    private boolean navigatingFullMap = false; // evita múltiples intents

        // Firma
        ivFirmaPreview  = v.findViewById(R.id.ivFirmaPreview);
        signView        = v.findViewById(R.id.signView);
        btnGuardarFirma = v.findViewById(R.id.btnGuardarFirma);
        btnLimpiar      = v.findViewById(R.id.btnLimpiar);

        // Listeners
        ivFoto.setOnClickListener(view -> showPreview());
        btnTomarFoto.setOnClickListener(view -> onTomarFoto());
        btnEditarFoto.setOnClickListener(view -> onTomarFoto());
        btnSoloEditar.setOnClickListener(view -> onTomarFoto());
        btnEditarFoto.setOnLongClickListener(v1 -> { borrarFoto(); return true; });
        btnSoloEditar.setOnLongClickListener(v12 -> { borrarFoto(); return true; });
        btnGuardarFirma.setOnClickListener(view -> onGuardarFirma());
        btnLimpiar.setOnClickListener(view -> signView.clear());
        btnConfirmarFoto.setOnClickListener(view -> onConfirmarFoto());

        cargarDetalle();
    }

    /* ================== Carga de datos ================== */
    private void cargarDetalle() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                AsignacionDao.AsignacionDetalle d = db.asignacionDao().getDetalleById(asignacionId);
                Asignacion a = db.asignacionDao().getById(asignacionId);


        locationPermLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                if (!isLocationEnabled()) {
                    promptEnableLocationServices();
                } else {
                    runPendingLocationAction();
                }
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    showRationaleUbicacion();
                } else {
                    showIrAjustesAppDialog();
                }
            }
        });
        locationSettingsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
            // Al volver de ajustes GPS
            if (isLocationEnabled()) runPendingLocationAction();
            else toast("La ubicación sigue desactivada");
        });
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Notificaciones")
                            .setMessage("Necesitas habilitar notificaciones para recibir actualizaciones. ¿Intentar de nuevo?")
                            .setPositiveButton("Solicitar", (d,w)-> notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                            .setNegativeButton("Ahora no", null)
                            .show();
                }
            });
        }
                        try { ivFoto.setImageURI(Uri.parse(savedPhotoUri)); } catch (Exception ignore) {}
                    }

                    updatePhotoButtons();
                    updateSignatureSection();
                    updateUiEnabled();

                    refreshTimelineAndEta();
                });

            } catch (Exception e) {
                runOnUi(() -> toast("Error cargando: " + e.getMessage()));
            }
        });
    }

    /* ================== Seguimiento (US-08) ================== */
    private void refreshTimelineAndEta() {
        if (tracking == null || solicitudId <= 0) return;
        tracking.loadTimelineAndEta(solicitudId, (events, eta) -> {
            if (tvEta != null) tvEta.setText("ETA: " + (eta != null && eta.eta != null ? eta.eta : "—"));
            if (trackingAdapter != null) trackingAdapter.submit(events);
        });
    }

    /* ================== Foto ================== */
    private void onTomarFoto() {
        if (guiaActiva) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            reqPermission.launch(Manifest.permission.CAMERA);
        } else {
            lanzarCamara();
        }
    }

    private void lanzarCamara() {
        // Mapa recolector
        mapViewRecolector = v.findViewById(R.id.map_recolector);
        if (mapViewRecolector != null) {
            mapViewRecolector.onCreate(savedInstanceState);
            mapViewRecolector.getMapAsync(gMap -> {
                googleMap = gMap;
                // Mapa mini: sin gestos ni controles para evitar scroll accidental
                googleMap.getUiSettings().setAllGesturesEnabled(false);
                googleMap.getUiSettings().setZoomControlsEnabled(false);
                googleMap.getUiSettings().setMapToolbarEnabled(false);
                // Cámara inicial Bogotá
                if (!initialCameraSet) {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(BOGOTA_LAT, BOGOTA_LON), 11f));
                    initialCameraSet = true;
                }
                if (lastRecolectorLat != null && lastRecolectorLon != null) {
                    pintarMarcadores(true);
                } else {
                    cargarUltimaUbicacionRecolector(true);
                    // Intentar obtener ubicación del dispositivo para trazar ruta temprana
                    intentarUbicacionDispositivoComoRecolector();
                }
            });
            // Listener sobre el propio MapView
            mapViewRecolector.setOnClickListener(view -> openMapaCompleto());
        }
        // Listener adicional sobre el contenedor para garantizar el click
        View frameMapaMini = v.findViewById(R.id.frameMapaMini);
        if (frameMapaMini != null) {
            frameMapaMini.setOnClickListener(view -> openMapaCompleto());
        }

                    if (solicitudId > 0) {
                        logEventWithLocation(solicitudId, "EVIDENCE_PHOTO", "Foto confirmada",
                                () -> tracking.loadEvents(solicitudId, evs -> {
                                    if (trackingAdapter != null) trackingAdapter.submit(evs);
                                }));
                    }

                    verificarYActivarGuia();
                });
            } catch (Exception e) {
                runOnUi(() -> toast("Error guardando foto: " + e.getMessage()));
        ensureNotificationPermissionIfNeeded();

    }

    private void borrarFoto() {
    private void openMapaCompleto() {
        if (navigatingFullMap) return; // evitar doble click rápido
        if (!isAdded()) return;
        navigatingFullMap = true;
        int id = asignacionId;
        Toast.makeText(requireContext(), "Abriendo mapa (#"+id+")", Toast.LENGTH_SHORT).show();
        Bundle args = new Bundle();
        args.putInt("asignacionId", id);
        try {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_detalle_to_recoleccionMapa, args);
        } catch (Exception primaryEx) {
            try {
                // Fallback: navegar directo al destino si la acción falla
                NavHostFragment.findNavController(this)
                        .navigate(R.id.recoleccionMapaFragment, args);
            } catch (Exception fallbackEx) {
                navigatingFullMap = false;
                Toast.makeText(requireContext(), "No se pudo navegar: "+ fallbackEx.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        // Liberar el flag tras un breve retraso por seguridad
        mapHandler.postDelayed(() -> navigatingFullMap = false, 1200);
    }

                (!TextUtils.isEmpty(savedPhotoUri) ? Uri.parse(savedPhotoUri) : null);
        if (toShow == null) return;

        ImageView iv = new ImageView(requireContext());
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageURI(toShow);

        new AlertDialog.Builder(requireContext())
                .setView(iv)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    /* ================== Firma & Guía ================== */
    private void onGuardarFirma() {
        if (guiaActiva) return;
        if (signView.isEmpty()) { toast("Dibuja la firma primero"); return; }

        new AlertDialog.Builder(requireContext())
                .setTitle("Confirmar firma")
                .setMessage("¿Deseas guardar esta firma como evidencia de recolección?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> guardarFirmaDefinitiva())
                .show();
    }

    private void guardarFirmaDefinitiva() {
        final String b64 = signView.getBitmapBase64();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                db.asignacionDao().guardarFirma(asignacionId, b64);
                runOnUi(() -> {
                    firmaB64 = b64;
                    toast("Firma guardada");
                    updateSignatureSection();

                    // Evento con ubicación (si hay permiso)
                    if (solicitudId > 0) {
                        logEventWithLocation(solicitudId, "SIGNATURE_SAVED", "Firma guardada",
                                () -> tracking.loadEvents(solicitudId, evs -> {
                                    if (trackingAdapter != null) trackingAdapter.submit(evs);
                                }));
                    }
                });
                verificarYActivarGuia();
            } catch (Exception e) {
                runOnUi(() -> toast("Error guardando firma: " + e.getMessage()));
            }
        });
    }

    private void verificarYActivarGuia() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                Asignacion a = db.asignacionDao().getById(asignacionId);

                boolean hayFoto  = (a != null && !TextUtils.isEmpty(a.evidenciaFotoUri));
                boolean hayFirma = (a != null && !TextUtils.isEmpty(a.firmaBase64));
                if (hayFoto && hayFirma && (a != null) && !a.guiaActiva) {
                    runOnUi(() -> new AlertDialog.Builder(requireContext())
            // tras refrescar timeline, refrescamos ubicación (puede haber nuevo evento con lat/lon)
            cargarUltimaUbicacionRecolector(false);
                            .setNegativeButton("No", null)
                            .setPositiveButton("Sí", (d,w) -> Executors.newSingleThreadExecutor().execute(() -> {
                                db.asignacionDao().activarGuia(asignacionId);
                                db.solicitudDao().marcarRecolectadaPorAsignacion(asignacionId);

                                runOnUi(() -> {
                                    guiaActiva = true;
                                    tvEstado.setText("GUÍA ACTIVADA (RECOLECTADA)");
                                    updateUiEnabled();
                                    updateSignatureSection();
                                });

                                long sid = solicitudId;
                                if (sid <= 0) {
                                    Asignacion ax = db.asignacionDao().getById(asignacionId);
                                    if (ax != null && ax.solicitudId > 0) sid = ax.solicitudId;
                                    if (sid <= 0) {
                                        try {
                                            Solicitud sx = db.solicitudDao().byAsignacionId(asignacionId);
                                            if (sx != null) sid = sx.id;
                                            if (sx != null) { destinoLat = sx.lat; destinoLon = sx.lon; }
                                        } catch (Exception ignore) {}
                                    }
                                }
                    requireContext().getPackageName() + ".fileprovider",
                                if (sid > 0) {
                                    // Evento con ubicación
                                    logEventWithLocation(sid, "PICKED_UP", "Guía activada y recolectada", null);

                                    // ETA (cálculo local simple)
                                    String etaIso;
                                    if (destinoLat != null && destinoLon != null) {
                                        // En demo: usa mismas coords para no fallar; ajusta si tienes origen real.
                                        double origenLat = destinoLat;
                                        double origenLon = destinoLon;
                                        double km;
                                        try {
                                            km = TrackingService.haversine(origenLat, origenLon, destinoLat, destinoLon);
                                            if (km <= 0) km = 5.0;
                                        } catch (Exception ex) { km = 5.0; }
                                        etaIso = TrackingService.calcEtaIso(km, 22);
                                    } else {
                                        etaIso = TrackingService.calcEtaIso(5.0, 20);
                                    }

                                    final long fsid = sid;
                                    tracking.upsertEta(sid, etaIso, "calc",
                                            () -> {
                                                if (tvEta != null) tvEta.setText("ETA: " + etaIso);
                                                tracking.loadEvents(fsid, evs -> {
                                                    if (trackingAdapter != null) trackingAdapter.submit(evs);
                                                });
                                            });
                                }
                            }))
                            .show());
                }
            } catch (Exception e) {
                runOnUi(() -> toast("Error activando guía: " + e.getMessage()));
            }
        });
    }

    /* ================== Helper: firma UI ================== */
    private void updateSignatureSection() {
        boolean hasFirma = !TextUtils.isEmpty(firmaB64);

        if (hasFirma) {
            Bitmap bmp = decodeB64(firmaB64);
            ivFirmaPreview.setImageBitmap(bmp);
            ivFirmaPreview.setVisibility(View.VISIBLE);

            signView.setVisibility(View.GONE);
            btnGuardarFirma.setVisibility(View.GONE);
            btnLimpiar.setVisibility(View.GONE);
        } else {
            ivFirmaPreview.setVisibility(View.GONE);

            signView.setVisibility(View.VISIBLE);
            btnGuardarFirma.setVisibility(View.VISIBLE);
            btnLimpiar.setVisibility(View.VISIBLE);
        }

        boolean enableDrawing = !guiaActiva && !hasFirma;
        signView.setEnabled(enableDrawing);
        btnGuardarFirma.setEnabled(enableDrawing);
        btnLimpiar.setEnabled(enableDrawing);
        float a = enableDrawing ? 1f : 0.5f;
        signView.setAlpha(a); btnGuardarFirma.setAlpha(a); btnLimpiar.setAlpha(a);
    }

    private Bitmap decodeB64(String b64) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateUiEnabled() {
        boolean enabled = !guiaActiva;

        ivFoto.setClickable(true);
        btnTomarFoto.setEnabled(enabled);
        btnEditarFoto.setEnabled(enabled);
        btnConfirmarFoto.setEnabled(enabled);
        btnSoloEditar.setEnabled(enabled);

        float alpha = enabled ? 1f : 0.5f;
        btnTomarFoto.setAlpha(alpha);
        btnEditarFoto.setAlpha(alpha);
        btnConfirmarFoto.setAlpha(alpha);
        btnSoloEditar.setAlpha(alpha);
    }

    /* ================== Helper: tracking con ubicación ================== */
    private void logEventWithLocation(long sid, String type, String detail, @Nullable Runnable afterUi) {
        if (tracking == null) return;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(loc -> {
                        Double lat = (loc != null) ? loc.getLatitude() : null;
                        Double lon = (loc != null) ? loc.getLongitude() : null;
                        tracking.logEvent(sid, type, detail, lat, lon,
                                () -> { if (afterUi != null) runOnUi(afterUi); });
                    })
                    .addOnFailureListener(e -> {
                        tracking.logEvent(sid, type, detail, null, null,
                                () -> { if (afterUi != null) runOnUi(afterUi); });
                    });
        } else {
            // sin permiso → registramos sin coordenadas
            tracking.logEvent(sid, type, detail, null, null,
                    () -> { if (afterUi != null) runOnUi(afterUi); });
        }
    }

    /* ================== Utiles ================== */
    private void runOnUi(Runnable r) { if (!isAdded()) return; requireActivity().runOnUiThread(r); }
    private void toast(String s) { if (!isAdded()) return; Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show(); }
}
