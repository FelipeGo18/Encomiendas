package com.hfad.encomiendas.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException; // CORRECTO
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.hfad.encomiendas.BuildConfig;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.TrackingService;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Asignacion;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.TrackingEventDao;
import com.hfad.encomiendas.ui.adapters.TrackingAdapter;
import com.hfad.encomiendas.ui.widgets.SignatureView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Fragment completo: detalle de recolección con
 * - Encabezado (orden / estado)
 * - Línea de tiempo + ETA
 * - Foto evidencia (tomar / confirmar / editar)
 * - Firma (guardar, vista previa)
 * - Activación de guía cuando hay foto + firma
 * - Mapa mini con ubicación (última) del recolector y destino + ruta aproximada
 */
public class DetalleRecoleccionFragment extends Fragment implements OnMapReadyCallback {

    private static final boolean DEBUG = true; // toggle rápido

    private static final String TAG = "DetalleRecoFrag";
    private static final String ARG_ID = "asignacionId";

    private int asignacionId = -1;

    // UI encabezado
    private TextView tvTitulo, tvSub, tvEstado, tvEta;
    private RecyclerView rvTimeline; private TrackingAdapter trackingAdapter;

    // Foto
    private ImageView ivFoto; private MaterialButton btnTomarFoto, btnEditarFoto, btnConfirmarFoto, btnSoloEditar; private View llEditarConfirmar, llSoloEditar;

    // Firma
    private ImageView ivFirmaPreview; private SignatureView signView; private MaterialButton btnGuardarFirma, btnLimpiar;

    // Mapa mini + botones de expansión
    private MapView mapViewRecolector; private GoogleMap googleMap; private Marker markerRecolector, markerDestino; private Polyline routePolyline;
    private MaterialButton btnVerMapaCompleto;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabExpandMap;
    private boolean initialCameraSet = false;

    // Estado principal
    private boolean guiaActiva = false; private Integer ordenRuta = null; private String estado = "";
    private long solicitudId = -1; private Double destinoLat = null, destinoLon = null;

    // Foto / Firma
    private Uri pendingPhotoUri = null; private String savedPhotoUri = null; private String firmaB64 = null;

    // Launchers permisos / cámara
    private ActivityResultLauncher<String> reqPermissionCam;
    private ActivityResultLauncher<Uri> takePicture;

    // Ubicación y permisos de ubicación / notificaciones
    private FusedLocationProviderClient fused;
    private ActivityResultLauncher<String> locationPermLauncher;
    private ActivityResultLauncher<Intent> locationSettingsLauncher;
    private ActivityResultLauncher<String> notifPermLauncher;
    private Runnable pendingLocationAction;

    // Tracking service
    private TrackingService tracking;

    // Handler refresco mapa
    private final Handler mapHandler = new Handler(Looper.getMainLooper());
    private final long MAP_REFRESH_MS = 30_000;
    private final Runnable mapRefreshTask = new Runnable() { @Override public void run() { cargarUltimaUbicacionRecolector(false); mapHandler.postDelayed(this, MAP_REFRESH_MS);} };

    // Última ubicación recolector
    private Double lastRecolectorLat = null, lastRecolectorLon = null;
    private Double routeFromLat=null, routeFromLon=null; private boolean routeRequested=false;

    // Constantes
    private static final double BOGOTA_LAT = 4.7110; private static final double BOGOTA_LON = -74.0721;

    public DetalleRecoleccionFragment() {}

    public static DetalleRecoleccionFragment newInstance(int asignacionId){
        DetalleRecoleccionFragment f = new DetalleRecoleccionFragment();
        Bundle b = new Bundle(); b.putInt(ARG_ID, asignacionId); f.setArguments(b); return f;
    }

    /* ================== Ciclo de vida ================== */
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detalle_recoleccion, container, false);
    }

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        asignacionId = (getArguments()!=null)? getArguments().getInt(ARG_ID, -1) : -1;
        tracking = new TrackingService(AppDatabase.getInstance(requireContext()));
        fused = LocationServices.getFusedLocationProviderClient(requireContext());
        prepararLaunchers();
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        bindViews(v);
        initMap(savedInstanceState);
        setListeners();
        cargarDetalle();
        ensureNotificationPermissionIfNeeded();
    }

    @Override public void onResume(){ super.onResume(); if (mapViewRecolector!=null) mapViewRecolector.onResume(); mapHandler.postDelayed(mapRefreshTask, 3_000); }
    @Override public void onPause(){ super.onPause(); if (mapViewRecolector!=null) mapViewRecolector.onPause(); mapHandler.removeCallbacks(mapRefreshTask); }
    @Override public void onDestroyView(){ super.onDestroyView(); if (mapViewRecolector!=null) mapViewRecolector.onDestroy(); mapHandler.removeCallbacks(mapRefreshTask); googleMap=null; markerRecolector=null; markerDestino=null; routePolyline=null; }
    @Override public void onLowMemory(){ super.onLowMemory(); if (mapViewRecolector!=null) mapViewRecolector.onLowMemory(); }
    @Override public void onStart(){
        super.onStart();
        if (mapViewRecolector!=null) mapViewRecolector.onStart();
    }
    @Override public void onStop(){
        super.onStop();
        if (mapViewRecolector!=null) mapViewRecolector.onStop();
    }
    @Override public void onSaveInstanceState(@NonNull Bundle outState){
        super.onSaveInstanceState(outState);
        if (mapViewRecolector!=null) mapViewRecolector.onSaveInstanceState(outState);
    }

    /* ================== Preparación ================== */
    private void bindViews(View v){
        tvTitulo = v.findViewById(R.id.tvTitulo); tvSub = v.findViewById(R.id.tvSub); tvEstado = v.findViewById(R.id.tvEstado); tvEta = v.findViewById(R.id.tvEta);
        rvTimeline = v.findViewById(R.id.rvTimeline);
        if (rvTimeline!=null){ rvTimeline.setLayoutManager(new LinearLayoutManager(requireContext())); trackingAdapter = new TrackingAdapter(); rvTimeline.setAdapter(trackingAdapter);}
        ivFoto = v.findViewById(R.id.ivFoto); btnTomarFoto = v.findViewById(R.id.btnTomarFoto); btnEditarFoto=v.findViewById(R.id.btnEditarFoto); btnConfirmarFoto=v.findViewById(R.id.btnConfirmarFoto); btnSoloEditar=v.findViewById(R.id.btnSoloEditar); llEditarConfirmar=v.findViewById(R.id.llEditarConfirmar); llSoloEditar=v.findViewById(R.id.llSoloEditar);
        ivFirmaPreview = v.findViewById(R.id.ivFirmaPreview); signView=v.findViewById(R.id.signView); btnGuardarFirma=v.findViewById(R.id.btnGuardarFirma); btnLimpiar=v.findViewById(R.id.btnLimpiar);
        mapViewRecolector = v.findViewById(R.id.map_recolector);
        btnVerMapaCompleto = v.findViewById(R.id.btnVerMapaCompleto);
        fabExpandMap = v.findViewById(R.id.fabExpandMap);
    }

    private void initMap(Bundle savedInstanceState){
        if (mapViewRecolector != null){
            mapViewRecolector.onCreate(savedInstanceState);
            mapViewRecolector.getMapAsync(this);
            // HABILITAMOS CLICKS y gestos para el mini-mapa
            mapViewRecolector.setClickable(true);
            mapViewRecolector.setOnClickListener(_v -> abrirMapaCompleto());
        }
        View frame = getView()!=null? getView().findViewById(R.id.frameMapaMini) : null;
        if (frame!=null) {
            frame.setOnClickListener(_v -> abrirMapaCompleto());
            // Añadir indicador visual de que es clickeable
            frame.setBackgroundResource(android.R.drawable.btn_default);
        }
    }

    private void prepararLaunchers(){
        reqPermissionCam = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { if (granted) lanzarCamara(); else toast("Permiso cámara denegado"); });
        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (ok && pendingPhotoUri != null){ ivFoto.setImageURI(pendingPhotoUri); mostrarEstadoFotoPendiente(); }
            else { pendingPhotoUri=null; toast("No se tomó la foto"); mostrarEstadoSinFoto(); }
        });
        locationPermLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted){ if (!isLocationEnabled()) promptEnableLocationServices(); else runPendingLocationAction(); }
            else { if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) showRationaleUbicacion(); else showIrAjustesAppDialog(); }
        });
        locationSettingsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> { if (isLocationEnabled()) runPendingLocationAction(); });
        if (Build.VERSION.SDK_INT >= 33){ notifPermLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), g -> {}); }
    }

    private void setListeners(){
        if (btnTomarFoto!=null) btnTomarFoto.setOnClickListener(v -> onTomarFoto());
        if (btnEditarFoto!=null) btnEditarFoto.setOnClickListener(v -> onTomarFoto());
        if (btnSoloEditar!=null) btnSoloEditar.setOnClickListener(v -> onTomarFoto());
        if (btnConfirmarFoto!=null) btnConfirmarFoto.setOnClickListener(v -> onConfirmarFoto());
        if (btnGuardarFirma!=null) btnGuardarFirma.setOnClickListener(v -> onGuardarFirma());
        if (btnLimpiar!=null) btnLimpiar.setOnClickListener(v -> { if (signView!=null) signView.clear(); });
        if (ivFoto!=null) ivFoto.setOnClickListener(v -> showPreviewFoto());
        if (btnVerMapaCompleto != null) btnVerMapaCompleto.setOnClickListener(v -> abrirMapaCompleto());
        if (fabExpandMap != null) fabExpandMap.setOnClickListener(v -> abrirMapaCompleto());
        mostrarEstadoSinFoto(); updateSignatureSection();
    }

    /* ================== Carga inicial ================== */
    private void cargarDetalle(){
        if (asignacionId <= 0){ toast("Asignación inválida"); return; }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                AsignacionDao.AsignacionDetalle d = db.asignacionDao().getDetalleById(asignacionId);
                Asignacion a = db.asignacionDao().getById(asignacionId);
                ordenRuta = (d!=null)? d.ordenRuta : null;
                estado = (a!=null && !TextUtils.isEmpty(a.estado))? a.estado : "—";
                guiaActiva = a!=null && a.guiaActiva;
                savedPhotoUri = (a!=null)? a.evidenciaFotoUri : null;
                pendingPhotoUri = (savedPhotoUri==null)? null : Uri.parse(savedPhotoUri);
                firmaB64 = (a!=null)? a.firmaBase64 : null;
                solicitudId = (a!=null)? a.solicitudId : -1;

                // Obtener coords destino
                destinoLat = null; destinoLon = null;
                if (solicitudId>0){
                    try {
                        Solicitud s = db.solicitudDao().byId(solicitudId);
                        if (s!=null){ destinoLat = s.lat; destinoLon = s.lon; }
                        if (DEBUG) Log.d(TAG, "Coordenadas desde solicitud ID " + solicitudId + ": lat=" + destinoLat + ", lon=" + destinoLon);
                    } catch (Exception ignore) {}
                }
                // Fallback: por asignación (join) si nulas
                if ((destinoLat==null || destinoLon==null) && asignacionId>0){
                    try {
                        Solicitud sx = db.solicitudDao().byAsignacionId(asignacionId);
                        if (sx!=null){ destinoLat = sx.lat; destinoLon = sx.lon; }
                        if (DEBUG) Log.d(TAG, "Coordenadas desde asignacion ID " + asignacionId + ": lat=" + destinoLat + ", lon=" + destinoLon);
                    } catch (Exception ignore) {}
                }

                // COORDENADAS DE PRUEBA FIJAS para garantizar que siempre aparezcan marcadores
                if (destinoLat == null || destinoLon == null) {
                    // Coordenadas de ejemplo en Bogotá
                    destinoLat = 4.6097 + (Math.random() - 0.5) * 0.1; // Zona centro de Bogotá con variación
                    destinoLon = -74.0817 + (Math.random() - 0.5) * 0.1;
                    if (DEBUG) Log.d(TAG, "Usando coordenadas de prueba: lat=" + destinoLat + ", lon=" + destinoLon);
                    toast("Usando coordenadas de prueba para demo");
                }

                final boolean destinoOk = destinoLat!=null && destinoLon!=null;

                runOnUi(() -> {
                    tvTitulo.setText("#"+asignacionId+" • "+estado);
                    tvSub.setText("Orden: "+ (ordenRuta==null?"—":ordenRuta));
                    tvEstado.setText(guiaActiva?"GUÍA ACTIVADA (RECOLECTADA)":"");
                    if (!TextUtils.isEmpty(savedPhotoUri)) try { ivFoto.setImageURI(Uri.parse(savedPhotoUri)); mostrarEstadoFotoConfirmada(); } catch (Exception ignore){}
                    updatePhotoButtons(); updateSignatureSection();

                    if (DEBUG) Log.d(TAG, "Datos cargados - destinoOk: " + destinoOk + ", googleMap ready: " + (googleMap != null));

                    // Si el mapa ya está listo y ahora tenemos coords, pintar markers / ruta
                    if (googleMap!=null && destinoOk) {
                        pintarMarcadores(true);
                        intentarRuta();
                    }
                    refreshTimelineAndEta();
                });
            } catch (Exception e){ Log.e(TAG, "Error cargando detalle", e); runOnUi(() -> toast("Error: "+e.getMessage())); }
        });
    }

    /* ================== Timeline / ETA ================== */
    private void refreshTimelineAndEta(){
        if (tracking==null || solicitudId <=0) return;
        tracking.loadTimelineAndEta(solicitudId, (events, eta) -> {
            if (tvEta!=null) tvEta.setText("ETA: "+ (eta!=null && eta.eta!=null? eta.eta : "—"));
            if (trackingAdapter!=null) trackingAdapter.submit(events);
            cargarUltimaUbicacionRecolector(false);
        });
    }

    /* ================== Foto ================== */
    private void onTomarFoto(){ if (guiaActiva){ toast("Guía activada"); return; } if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) reqPermissionCam.launch(Manifest.permission.CAMERA); else lanzarCamara(); }

    private void lanzarCamara(){
        try {
            File dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES); if (dir==null) dir = requireContext().getFilesDir();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File photo = new File(dir, "evid_"+asignacionId+"_"+ts+".jpg"); if (!photo.exists()) photo.createNewFile();
            pendingPhotoUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName()+".fileprovider", photo);
            takePicture.launch(pendingPhotoUri);
        } catch (IOException e){ toast("No se pudo crear archivo: "+e.getMessage()); }
    }

    private void onConfirmarFoto(){ if (pendingPhotoUri==null){ toast("Toma una foto primero"); return; } savedPhotoUri=pendingPhotoUri.toString(); pendingPhotoUri=null; updatePhotoButtons(); guardarFotoDB(); verificarYActivarGuia(); }

    private void guardarFotoDB(){ Executors.newSingleThreadExecutor().execute(() -> { try { AppDatabase.getInstance(requireContext()).asignacionDao().guardarFoto(asignacionId, savedPhotoUri); } catch (Exception e){ Log.e(TAG, "guardarFoto", e);} }); }

    private void updatePhotoButtons(){ boolean hayPend = (pendingPhotoUri!=null) && (savedPhotoUri==null || !pendingPhotoUri.toString().equals(savedPhotoUri)); boolean hayConf = !TextUtils.isEmpty(savedPhotoUri); btnTomarFoto.setVisibility((!hayPend && !hayConf)?View.VISIBLE:View.GONE); llEditarConfirmar.setVisibility(hayPend?View.VISIBLE:View.GONE); llSoloEditar.setVisibility((!hayPend && hayConf)?View.VISIBLE:View.GONE); }

    private void showPreviewFoto(){ Uri u = (pendingPhotoUri!=null)? pendingPhotoUri : (!TextUtils.isEmpty(savedPhotoUri)? Uri.parse(savedPhotoUri):null); if (u==null) return; ImageView iv = new ImageView(requireContext()); iv.setAdjustViewBounds(true); iv.setImageURI(u); new AlertDialog.Builder(requireContext()).setView(iv).setPositiveButton("Cerrar", null).show(); }

    private void mostrarEstadoSinFoto(){ btnTomarFoto.setVisibility(View.VISIBLE); llEditarConfirmar.setVisibility(View.GONE); llSoloEditar.setVisibility(View.GONE);} private void mostrarEstadoFotoPendiente(){ btnTomarFoto.setVisibility(View.GONE); llEditarConfirmar.setVisibility(View.VISIBLE); llSoloEditar.setVisibility(View.GONE);} private void mostrarEstadoFotoConfirmada(){ btnTomarFoto.setVisibility(View.GONE); llEditarConfirmar.setVisibility(View.GONE); llSoloEditar.setVisibility(View.VISIBLE);}

    /* ================== Firma ================== */
    private void onGuardarFirma(){
        if (guiaActiva && !TextUtils.isEmpty(firmaB64)) { toast("Guía ya activada (firma existente)"); return; }
        if (signView==null) { toast("Vista de firma no disponible"); return; }
        if (signView.isEmpty()) { toast("Dibuja la firma primero"); return; }
        new AlertDialog.Builder(requireContext())
                .setTitle("Confirmar firma")
                .setMessage("¿Guardar esta firma?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d,w)-> guardarFirmaDefinitiva())
                .show();
    }

    private void guardarFirmaDefinitiva(){
        final long tStart = System.currentTimeMillis();
        final int vw = (signView!=null? signView.getWidth():-1);
        final int vh = (signView!=null? signView.getHeight():-1);
        final String b64 = signView.getBitmapBase64();
        if (DEBUG) Log.d(TAG, "guardarFirmaDefinitiva start len="+ (b64==null? -1 : b64.length()) + " view="+vw+"x"+vh);
        toast("Guardando firma...");
        Executors.newSingleThreadExecutor().execute(() -> {
            Asignacion a2 = null;
            boolean ok = false;
            String errMsg = null;
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                db.asignacionDao().guardarFirma(asignacionId, b64);
                // re-leer para verificar persistencia
                a2 = db.asignacionDao().getById(asignacionId);
                ok = (a2 != null && a2.firmaBase64 != null && a2.firmaBase64.length() == b64.length());
            } catch (Exception e){
                errMsg = e.getMessage();
                if (DEBUG) Log.e(TAG, "Error guardando firma", e);
            }
            final Asignacion aCheck = a2; final boolean persisted = ok; final String fErr = errMsg;
            runOnUi(() -> {
                if (fErr != null){
                    toast("Error guardando firma: "+ fErr);
                    return;
                }
                firmaB64 = b64; // actualiza local incluso si persisted=false para que al menos muestre preview
                updateSignatureSection();
                long ms = System.currentTimeMillis()-tStart;
                toast(persisted? "Firma guardada ✓ ("+ms+" ms)" : "Firma NO verificada (revisa logs)");
                if (!persisted && aCheck!=null && aCheck.firmaBase64!=null){
                    // fallback: quizás difiere longitud por compresión; sólo info
                    toast("Leída firma len="+aCheck.firmaBase64.length());
                }
                if (solicitudId>0) logEventWithLocation(solicitudId, "SIGNATURE_SAVED", "Firma guardada", () -> tracking.loadEvents(solicitudId, evs -> { if (trackingAdapter!=null) trackingAdapter.submit(evs); }));
                verificarYActivarGuia();
            });
        });
    }

    private void updateSignatureSection(){ boolean hasFirma = !TextUtils.isEmpty(firmaB64); ivFirmaPreview.setVisibility(hasFirma?View.VISIBLE:View.GONE); signView.setVisibility(!hasFirma && !guiaActiva?View.VISIBLE:View.GONE); btnGuardarFirma.setVisibility(!hasFirma && !guiaActiva?View.VISIBLE:View.GONE); btnLimpiar.setVisibility(!hasFirma && !guiaActiva?View.VISIBLE:View.GONE); signView.setEnabled(!guiaActiva && !hasFirma); if (hasFirma){ try { byte[] data = Base64.decode(firmaB64, Base64.DEFAULT); Bitmap bmp = BitmapFactory.decodeByteArray(data,0,data.length); ivFirmaPreview.setImageBitmap(bmp);} catch (Exception ignore){} } }

    private void verificarYActivarGuia(){ Executors.newSingleThreadExecutor().execute(() -> { try { AppDatabase db = AppDatabase.getInstance(requireContext()); Asignacion a = db.asignacionDao().getById(asignacionId); boolean hayFoto = (a!=null && !TextUtils.isEmpty(a.evidenciaFotoUri)); boolean hayFirma=(a!=null && !TextUtils.isEmpty(a.firmaBase64)); if (hayFoto && hayFirma && a!=null && !a.guiaActiva){ runOnUi(() -> new AlertDialog.Builder(requireContext()).setTitle("Activar guía").setMessage("Se detectó foto y firma. ¿Activar guía y marcar RECOLECTADA?").setNegativeButton("No", null).setPositiveButton("Sí", (d,w)-> Executors.newSingleThreadExecutor().execute(() -> { db.asignacionDao().activarGuia(asignacionId); db.solicitudDao().marcarRecolectadaPorAsignacion(asignacionId); runOnUi(() -> { guiaActiva=true; tvEstado.setText("GUÍA ACTIVADA (RECOLECTADA)"); updateSignatureSection(); }); long sid = solicitudId; if (sid<=0){ Asignacion ax = db.asignacionDao().getById(asignacionId); if (ax!=null) sid = ax.solicitudId; }
                        if (sid>0) logEventWithLocation(sid, "PICKED_UP", "Guía activada", null); })).show()); } } catch (Exception e){ Log.e(TAG, "activarGuia", e);} }); }

    /* ================== Mapa ================== */
    @Override public void onMapReady(@NonNull GoogleMap gm) {
        googleMap = gm;
        if (DEBUG) Log.d(TAG, "onMapReady called");

        try {
            // HABILITAR todos los gestos para que se pueda hacer zoom y mover
            googleMap.getUiSettings().setAllGesturesEnabled(true);
            googleMap.getUiSettings().setZoomControlsEnabled(true); // Botones + y -
            googleMap.getUiSettings().setZoomGesturesEnabled(true); // Pellizcar para zoom
            googleMap.getUiSettings().setScrollGesturesEnabled(true); // Arrastrar
            googleMap.getUiSettings().setTiltGesturesEnabled(true); // Inclinar
            googleMap.getUiSettings().setRotateGesturesEnabled(true); // Rotar
            googleMap.getUiSettings().setMapToolbarEnabled(true); // Toolbar con botones
            googleMap.getUiSettings().setMyLocationButtonEnabled(false); // Desactivar botón mi ubicación para evitar confusión

            if (DEBUG) Log.d(TAG, "Gestos del mapa habilitados correctamente");
        } catch (Exception e){
            if (DEBUG) Log.e(TAG, "Error configurando gestos del mapa", e);
        }

        if (!initialCameraSet){
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(BOGOTA_LAT, BOGOTA_LON), 11f));
            initialCameraSet=true;
        }

        // Aviso si no hay API key configurada
        try {
            String key = BuildConfig.MAPS_API_KEY;
            if (TextUtils.isEmpty(key) || key.startsWith("REEMPLAZA") ) {
                if (DEBUG) Log.w(TAG, "API Key Maps faltante o placeholder");
            }
        } catch (Throwable ignore) {}

        // Si ya tenemos coordenadas de destino, pintar inmediatamente
        if (destinoLat != null && destinoLon != null) {
            if (DEBUG) Log.d(TAG, "Mapa listo y ya tenemos destino, pintando marcadores");
            pintarMarcadores(true);
            intentarRuta();
        }

        cargarUltimaUbicacionRecolector(true);
    }

    private void cargarUltimaUbicacionRecolector(boolean moveCamera){
        if (DEBUG) Log.d(TAG, "cargarUltimaUbicacionRecolector - solicitudId: " + solicitudId);

        // PRIMERA PRIORIDAD: Obtener ubicación actual del dispositivo
        intentarUbicacionDispositivoComoRecolector();

        // SEGUNDA PRIORIDAD: Buscar en tracking events (solo si no hay ubicación del dispositivo)
        if (solicitudId > 0) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    TrackingEventDao.LastLoc loc = AppDatabase.getInstance(requireContext()).trackingEventDao().lastLocationForShipment(solicitudId);
                    if (loc != null && loc.lat != null && loc.lon != null) {
                        if (DEBUG) Log.d(TAG, "Encontrada ubicación en tracking: " + loc.lat + "," + loc.lon);
                        // Solo usar tracking si no tenemos ubicación del dispositivo
                        if (lastRecolectorLat == null || lastRecolectorLon == null) {
                            lastRecolectorLat = loc.lat;
                            lastRecolectorLon = loc.lon;
                            runOnUi(() -> {
                                pintarMarcadores(moveCamera);
                                intentarRuta();
                            });
                        }
                    } else {
                        if (DEBUG) Log.w(TAG, "No hay datos de tracking para solicitud " + solicitudId);
                        // Si no hay tracking y tampoco ubicación del dispositivo, usar posición de prueba
                        runOnUi(() -> {
                            if (lastRecolectorLat == null || lastRecolectorLon == null) {
                                if (destinoLat != null && destinoLon != null) {
                                    lastRecolectorLat = destinoLat + 0.015;
                                    lastRecolectorLon = destinoLon + 0.015;
                                    if (DEBUG) Log.d(TAG, "Usando posición de prueba para mini-mapa: " + lastRecolectorLat + "," + lastRecolectorLon);
                                    pintarMarcadores(moveCamera);
                                    intentarRuta();
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    if (DEBUG) Log.e(TAG, "Error en tracking para mini-mapa", e);
                }
            });
        }
    }

    private void pintarMarcadores(boolean forceMove){
        if (googleMap==null) {
            if (DEBUG) Log.w(TAG, "pintarMarcadores: googleMap es null");
            return;
        }

        if (DEBUG) Log.d(TAG, "Pintando marcadores - recolector: " + lastRecolectorLat + "," + lastRecolectorLon + " destino: " + destinoLat + "," + destinoLon);

        // GARANTIZAR que siempre tengamos posición del recolector
        if (lastRecolectorLat == null || lastRecolectorLon == null) {
            if (destinoLat != null && destinoLon != null) {
                // Posición de prueba: un poco al noreste del destino
                lastRecolectorLat = destinoLat + 0.015; // ~1.5km al norte
                lastRecolectorLon = destinoLon + 0.015; // ~1.5km al este
                if (DEBUG) Log.d(TAG, "Asignando posición de prueba al recolector: " + lastRecolectorLat + "," + lastRecolectorLon);
            }
        }

        // Marcador del recolector (azul) - SIEMPRE mostrar
        if (lastRecolectorLat != null && lastRecolectorLon != null) {
            LatLng p = new LatLng(lastRecolectorLat, lastRecolectorLon);
            if (markerRecolector == null) {
                markerRecolector = googleMap.addMarker(new MarkerOptions()
                    .position(p)
                    .title("Recolector")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            } else {
                markerRecolector.setPosition(p);
            }
            if (DEBUG) Log.d(TAG, "Marcador recolector pintado en: " + lastRecolectorLat + "," + lastRecolectorLon);
        }

        // Marcador del destino (rojo) - SIEMPRE mostrar
        if (destinoLat != null && destinoLon != null) {
            LatLng d = new LatLng(destinoLat, destinoLon);
            if (markerDestino == null) {
                markerDestino = googleMap.addMarker(new MarkerOptions()
                    .position(d)
                    .title("Punto de Recojo")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            } else {
                markerDestino.setPosition(d);
            }
            if (DEBUG) Log.d(TAG, "Marcador destino pintado en: " + destinoLat + "," + destinoLon);
        }

        // FORZAR dibujo de ruta inmediatamente después de pintar marcadores
        if (lastRecolectorLat != null && lastRecolectorLon != null && destinoLat != null && destinoLon != null) {
            if (DEBUG) Log.d(TAG, "Forzando dibujo de ruta inmediatamente");
            // Dibujar ruta simple inmediatamente (línea recta)
            dibujarRutaSimple();
            // Luego intentar ruta con API
            intentarRutaConAPI();
        }

        if (forceMove) {
            LatLng focus;
            if (lastRecolectorLat != null && destinoLat != null) {
                focus = new LatLng((lastRecolectorLat + destinoLat) / 2.0, (lastRecolectorLon + destinoLon) / 2.0);
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(focus, 13f));
                if (DEBUG) Log.d(TAG, "Centrando mapa entre recolector y destino");
            } else if (destinoLat != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(destinoLat, destinoLon), 14f));
                if (DEBUG) Log.d(TAG, "Centrando mapa en destino");
            } else if (lastRecolectorLat != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastRecolectorLat, lastRecolectorLon), 14f));
                if (DEBUG) Log.d(TAG, "Centrando mapa en recolector");
            }
        }
    }

    // Nuevo método para dibujar ruta simple inmediatamente
    private void dibujarRutaSimple() {
        if (googleMap == null || lastRecolectorLat == null || lastRecolectorLon == null || destinoLat == null || destinoLon == null) {
            if (DEBUG) Log.w(TAG, "No se puede dibujar ruta simple - faltan datos");
            return;
        }

        // Remover ruta anterior
        if (routePolyline != null) {
            routePolyline.remove();
        }

        // Crear línea recta simple
        List<LatLng> puntos = new ArrayList<>();
        puntos.add(new LatLng(lastRecolectorLat, lastRecolectorLon));
        puntos.add(new LatLng(destinoLat, destinoLon));

        PolylineOptions opts = new PolylineOptions()
            .width(6f)
            .color(0xFF1976D2) // Azul
            .geodesic(true);
        opts.addAll(puntos);

        routePolyline = googleMap.addPolyline(opts);
        if (DEBUG) Log.d(TAG, "Ruta simple dibujada: " + puntos.size() + " puntos");
    }

    // Método separado para intentar ruta con API (opcional)
    private void intentarRutaConAPI() {
        if (googleMap == null || destinoLat == null || destinoLon == null || lastRecolectorLat == null || lastRecolectorLon == null) return;

        if (routeRequested) return;
        routeRequested = true;

        final double oLat = lastRecolectorLat, oLon = lastRecolectorLon, dLat = destinoLat, dLon = destinoLon;
        Executors.newSingleThreadExecutor().execute(() -> fetchRoute(oLat, oLon, dLat, dLon));
    }

    private void intentarRuta() {
        // Este método ahora solo llama a dibujarRutaSimple para garantizar que SIEMPRE haya una línea
        if (DEBUG) Log.d(TAG, "intentarRuta llamado");
        dibujarRutaSimple();
    }

    private void fetchRoute(double oLat,double oLon,double dLat,double dLon){
        List<LatLng> decoded=null;
        String key = BuildConfig.MAPS_API_KEY;
        boolean canCall = key!=null && !key.trim().isEmpty();
        if (canCall){
            String urlStr = String.format(Locale.US, "https://maps.googleapis.com/maps/api/directions/json?origin=%f,%f&destination=%f,%f&mode=driving&key=%s", oLat,oLon,dLat,dLon,key);
            HttpURLConnection conn=null;
            try {
                URL url = new URL(urlStr);
                conn=(HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.connect();
                if (conn.getResponseCode()==200){
                    InputStream is=conn.getInputStream();
                    BufferedReader br=new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb=new StringBuilder();
                    String line;
                    while((line=br.readLine())!=null) sb.append(line);
                    JSONObject root=new JSONObject(sb.toString());
                    JSONArray routes = root.optJSONArray("routes");
                    if (routes!=null && routes.length()>0){
                        JSONObject r0 = routes.getJSONObject(0);
                        JSONObject poly = r0.getJSONObject("overview_polyline");
                        String pts = poly.getString("points");
                        decoded = decodePolyline(pts);
                    }
                }
            } catch (Exception e){
                Log.w(TAG,"Directions fallback", e);
            } finally {
                if (conn!=null) conn.disconnect();
            }
        }
        if (decoded==null || decoded.isEmpty()){
            decoded=new ArrayList<>();
            decoded.add(new LatLng(oLat,oLon));
            decoded.add(new LatLng(dLat,dLon));
        }
        List<LatLng> finalDecoded = decoded;
        runOnUi(() -> drawRoute(finalDecoded,oLat,oLon));
    }

    private void drawRoute(List<LatLng> points,double fromLat,double fromLon){
        routeRequested=false;
        routeFromLat=fromLat;
        routeFromLon=fromLon;
        if (googleMap==null || points==null || points.isEmpty()) return;
        if (routePolyline!=null) routePolyline.remove();

        PolylineOptions opts = new PolylineOptions()
            .width(8f)
            .color(0xFF1976D2)
            .geodesic(true);
        opts.addAll(points);
        routePolyline=googleMap.addPolyline(opts);

        if (DEBUG) Log.d(TAG, "Ruta dibujada con " + points.size() + " puntos");
    }

    private List<LatLng> decodePolyline(String encoded){
        List<LatLng> poly=new ArrayList<>();
        int index=0,len=encoded.length();
        int lat=0,lng=0;
        while(index<len){
            int b,shift=0,result=0;
            do {
                b=encoded.charAt(index++)-63;
                result|=(b & 0x1f)<<shift;
                shift+=5;
            } while(b>=0x20);
            int dlat=((result & 1)!=0 ? ~(result>>1):(result>>1));
            lat+=dlat;
            shift=0;
            result=0;
            do {
                b=encoded.charAt(index++)-63;
                result|=(b & 0x1f)<<shift;
                shift+=5;
            } while(b>=0x20);
            int dlng=((result & 1)!=0 ? ~(result>>1):(result>>1));
            lng+=dlng;
            double latD=lat/1E5;
            double lonD=lng/1E5;
            poly.add(new LatLng(latD,lonD));
        }
        return poly;
    }

    private void abrirMapaCompleto(){
        if (!isAdded()) return;
        if (DEBUG) Log.d(TAG, "Intentando abrir mapa completo para asignacion " + asignacionId);

        // Mostrar toast de confirmación
        toast("Abriendo mapa en pantalla completa...");

        try {
            Bundle args = new Bundle();
            args.putInt("asignacionId", asignacionId);

            // Verificar si existe el fragment de destino
            try {
                NavHostFragment.findNavController(this).navigate(R.id.recoleccionMapaFragment, args);
                if (DEBUG) Log.d(TAG, "Navegación exitosa a recoleccionMapaFragment");
                return;
            } catch (Exception navEx) {
                if (DEBUG) Log.w(TAG, "No se pudo navegar a recoleccionMapaFragment: " + navEx.getMessage());
            }

            // Fallback 1: Intentar con acción
            try {
                NavHostFragment.findNavController(this).navigate(R.id.action_detalle_to_recoleccionMapa, args);
                if (DEBUG) Log.d(TAG, "Navegación exitosa con acción");
                return;
            } catch (Exception actionEx) {
                if (DEBUG) Log.w(TAG, "No se pudo navegar con acción: " + actionEx.getMessage());
            }

            // Fallback 2: Mostrar diálogo con información del mapa
            mostrarDialogoMapaCompleto();

        } catch (Exception e){
            if (DEBUG) Log.e(TAG, "Error general en navegación", e);
            mostrarDialogoMapaCompleto();
        }
    }

    private void mostrarDialogoMapaCompleto() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Información del Mapa")
            .setMessage("Ubicación del recolector: " +
                (lastRecolectorLat != null ? String.format("%.4f, %.4f", lastRecolectorLat, lastRecolectorLon) : "No disponible") +
                "\n\nDestino: " +
                (destinoLat != null ? String.format("%.4f, %.4f", destinoLat, destinoLon) : "No disponible") +
                "\n\nPuedes hacer zoom y mover el mapa directamente aquí.")
            .setPositiveButton("Entendido", null)
            .setNeutralButton("Centrar Mapa", (d, w) -> {
                if (googleMap != null && destinoLat != null && destinoLon != null) {
                    LatLng center = lastRecolectorLat != null ?
                        new LatLng((lastRecolectorLat + destinoLat) / 2.0, (lastRecolectorLon + destinoLon) / 2.0) :
                        new LatLng(destinoLat, destinoLon);
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 14f));
                }
            })
            .show();
    }

    /* ================== Ubicación / permisos ================== */
    private boolean hasLocationPermission(){
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled(){
        try {
            LocationManager lm = (LocationManager) requireContext().getSystemService(Activity.LOCATION_SERVICE);
            return lm!=null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        } catch (Exception e){
            return false;
        }
    }

    private void ensureLocationFlow(@Nullable Runnable after){
        if (after!=null) pendingLocationAction=after;
        if (!hasLocationPermission()){
            locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        if (!isLocationEnabled()){
            promptEnableLocationServices();
            return;
        }
        runPendingLocationAction();
    }

    private void runPendingLocationAction(){
        Runnable r = pendingLocationAction;
        pendingLocationAction=null;
        if (r!=null) r.run();
    }

    private void promptEnableLocationServices(){
        new AlertDialog.Builder(requireContext())
            .setTitle("Activar ubicación")
            .setMessage("Activa GPS para mostrar ruta")
            .setPositiveButton("Configurar", (d,w)-> locationSettingsLauncher.launch(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void showRationaleUbicacion(){
        new AlertDialog.Builder(requireContext())
            .setTitle("Permiso ubicación")
            .setMessage("Se necesita para ruta aproximada")
            .setPositiveButton("Conceder", (d,w)-> locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION))
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void showIrAjustesAppDialog(){
        new AlertDialog.Builder(requireContext())
            .setTitle("Permiso denegado")
            .setMessage("Ve a Ajustes > Permisos para habilitar ubicación")
            .setPositiveButton("Abrir", (d,w)-> {
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:"+requireContext().getPackageName()));
                    startActivity(i);
                } catch (ActivityNotFoundException ex){
                    toast("No se pudo abrir");
                }
            })
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private void ensureNotificationPermissionIfNeeded(){
        if (Build.VERSION.SDK_INT>=33){
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                if (notifPermLauncher!=null) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void intentarUbicacionDispositivoComoRecolector(){
        // Evita recalcular si ya tenemos última ubicación
        if (lastRecolectorLat!=null && lastRecolectorLon!=null) return;
        // Verificación explícita de permiso para satisfacer lint
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Lanzar flujo normal de permisos
            if (locationPermLauncher != null) {
                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            return;
        }
        if (fused == null) return;
        try {
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(loc -> {
                        if (loc != null) {
                            lastRecolectorLat = loc.getLatitude();
                            lastRecolectorLon = loc.getLongitude();
                            if (googleMap != null) {
                                pintarMarcadores(true);
                                intentarRuta();
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "No se obtuvo ubicación dispositivo", e));
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException al solicitar ubicación", se);
        }
    }

    private void logEventWithLocation(long sid,String type,String detail,@Nullable Runnable afterUi){
        if (tracking==null) return;
        ensureLocationFlow(() -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(loc->{
                        Double lat=(loc!=null)?loc.getLatitude():null;
                        Double lon=(loc!=null)?loc.getLongitude():null;
                        tracking.logEvent(sid,type,detail,lat,lon, ()-> {
                            if (afterUi!=null) runOnUi(afterUi);
                        });
                    })
                    .addOnFailureListener(e-> tracking.logEvent(sid,type,detail,null,null, ()-> {
                        if (afterUi!=null) runOnUi(afterUi);
                    }));
            } else {
                tracking.logEvent(sid,type,detail,null,null, ()-> {
                    if (afterUi!=null) runOnUi(afterUi);
                });
            }
        });
    }

    /* ================== Helpers ================== */
    private void runOnUi(Runnable r){
        if (isAdded()) requireActivity().runOnUiThread(r);
    }

    private void toast(String s){
        if (isAdded()) Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
    }
}
