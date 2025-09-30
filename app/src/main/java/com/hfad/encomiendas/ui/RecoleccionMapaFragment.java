package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.hfad.encomiendas.BuildConfig;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Asignacion;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.TrackingEventDao;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Mapa a pantalla completa para una asignación de recolección.
 * Muestra: marcador del recolector (última ubicación registrada) + marcador del destino (dirección a recoger) + ruta.
 */
public class RecoleccionMapaFragment extends Fragment implements OnMapReadyCallback {

    private static final double BOGOTA_LAT = 4.7110;
    private static final double BOGOTA_LON = -74.0721;
    private static final long REFRESH_MS = 30_000L;

    private int asignacionId = -1;
    private long solicitudId = -1;
    private Double destinoLat, destinoLon; // destino

    private MapView mapView; private GoogleMap gMap;
    private Marker markerRecolector, markerDestino;
    private Polyline routePolyline;
    private Double lastRecolectorLat, lastRecolectorLon;

    private View btnCenter; private ImageButton btnBack; private TextView tvStatus;

    private final Runnable refresco = new Runnable() {
        @Override public void run() {
            cargarLastLocation();
            if (mapView != null) mapView.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recoleccion_mapa, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        asignacionId = getArguments() != null ? getArguments().getInt("asignacionId", -1) : -1;
        mapView = v.findViewById(R.id.mapFullReco);
        btnCenter = v.findViewById(R.id.fabCenterReco);
        btnBack = v.findViewById(R.id.btnBackReco);
        tvStatus = v.findViewById(R.id.tvStatusReco);

        if (mapView != null) { mapView.onCreate(savedInstanceState); mapView.getMapAsync(this); }
        if (btnBack != null) btnBack.setOnClickListener(v1 -> requireActivity().onBackPressed());
        if (btnCenter != null) btnCenter.setOnClickListener(v12 -> centerCamera(true));

        cargarDatosIniciales();
        cargarLastLocation();
    }

    private void cargarDatosIniciales() {
        if (asignacionId <= 0) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                Asignacion a = db.asignacionDao().getById(asignacionId);
                if (a != null) {
                    solicitudId = a.solicitudId;
                }
                if (solicitudId > 0) {
                    Solicitud s = db.solicitudDao().byId(solicitudId);
                    if (s != null) { destinoLat = s.lat; destinoLon = s.lon; }
                }
                if (getActivity()!=null) getActivity().runOnUiThread(() -> {
                    if (gMap != null) pintarMarcadores(false);
                });
            } catch (Exception ignore) {}
        });
    }

    private void cargarLastLocation() {
        if (solicitudId <= 0) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                TrackingEventDao.LastLoc loc = AppDatabase.getInstance(requireContext())
                        .trackingEventDao().lastLocationForShipment(solicitudId);
                if (loc != null && loc.lat != null && loc.lon != null) {
                    lastRecolectorLat = loc.lat; lastRecolectorLon = loc.lon;
                    if (getActivity()!=null) getActivity().runOnUiThread(() -> {
                        pintarMarcadores(false); intentarRuta();
                        actualizarStatus();
                    });
                }
            } catch (Exception ignore) {}
        });
    }

    private void actualizarStatus() {
        if (tvStatus == null) return;
        String txt;
        if (lastRecolectorLat != null) {
            txt = String.format(Locale.getDefault(), "Recolector: %.5f, %.5f", lastRecolectorLat, lastRecolectorLon);
        } else {
            txt = "Sin ubicación de recolector aún";
        }
        tvStatus.setText(txt);
    }

    private void pintarMarcadores(boolean force) {
        if (gMap == null) return;
        if (lastRecolectorLat != null && lastRecolectorLon != null) {
            LatLng posR = new LatLng(lastRecolectorLat, lastRecolectorLon);
            if (markerRecolector == null) markerRecolector = gMap.addMarker(new MarkerOptions().position(posR).title("Recolector"));
            else markerRecolector.setPosition(posR);
        }
        if (destinoLat != null && destinoLon != null) {
            LatLng posD = new LatLng(destinoLat, destinoLon);
            if (markerDestino == null) markerDestino = gMap.addMarker(new MarkerOptions().position(posD).title("Destino"));
        }
        if (force) centerCamera(false);
    }

    private void centerCamera(boolean animate) {
        if (gMap == null) return;
        LatLng target;
        if (lastRecolectorLat != null && destinoLat != null) {
            target = new LatLng( (lastRecolectorLat+destinoLat)/2.0, (lastRecolectorLon+destinoLon)/2.0 );
            if (animate) gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 13f)); else gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target,13f));
        } else if (lastRecolectorLat != null) {
            target = new LatLng(lastRecolectorLat, lastRecolectorLon);
            if (animate) gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 14f)); else gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target,14f));
        } else if (destinoLat != null) {
            target = new LatLng(destinoLat, destinoLon);
            if (animate) gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 14f)); else gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target,14f));
        } else {
            target = new LatLng(BOGOTA_LAT, BOGOTA_LON);
            if (animate) gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 11f)); else gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target,11f));
        }
    }

    /* ===== Ruta ===== */
    private boolean routeRequested = false;
    private Double lastRouteFromLat, lastRouteFromLon;
    private void intentarRuta() {
        if (gMap == null || destinoLat == null || destinoLon == null || lastRecolectorLat == null) return;
        if (lastRouteFromLat != null && lastRouteFromLon != null) {
            double d = Math.hypot(lastRouteFromLat - lastRecolectorLat, lastRouteFromLon - lastRecolectorLon);
            if (d < 0.0003 && routePolyline != null) return;
        }
        if (routeRequested) return;
        routeRequested = true;
        final double oLat = lastRecolectorLat, oLon = lastRecolectorLon, dLat = destinoLat, dLon = destinoLon;
        Executors.newSingleThreadExecutor().execute(() -> fetchRoute(oLat, oLon, dLat, dLon));
    }

    private void fetchRoute(double oLat, double oLon, double dLat, double dLon) {
        List<LatLng> decoded = null;
        String key = BuildConfig.MAPS_API_KEY;
        boolean canApi = key != null && !key.trim().isEmpty();
        if (canApi) {
            String urlStr = String.format(Locale.US,
                    "https://maps.googleapis.com/maps/api/directions/json?origin=%f,%f&destination=%f,%f&mode=driving&key=%s",
                    oLat,oLon,dLat,dLon,key);
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(8000); conn.setReadTimeout(10000); conn.connect();
                if (conn.getResponseCode()==200) {
                    InputStream is = conn.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line);
                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray routes = root.optJSONArray("routes");
                    if (routes != null && routes.length()>0) {
                        JSONObject r0 = routes.getJSONObject(0);
                        JSONObject poly = r0.getJSONObject("overview_polyline");
                        decoded = decodePolyline(poly.getString("points"));
                    }
                }
            } catch (Exception ignore) {} finally { if (conn!=null) conn.disconnect(); }
        }
        if (decoded == null || decoded.isEmpty()) {
            decoded = new ArrayList<>(); decoded.add(new LatLng(oLat,oLon)); decoded.add(new LatLng(dLat,dLon));
        }
        final List<LatLng> finalDecoded = decoded;
        if (getActivity()!=null) getActivity().runOnUiThread(() -> drawRoute(finalDecoded, oLat, oLon));
    }

    private void drawRoute(List<LatLng> pts, double fromLat, double fromLon) {
        routeRequested = false; lastRouteFromLat = fromLat; lastRouteFromLon = fromLon;
        if (gMap == null || pts == null || pts.isEmpty()) return;
        if (routePolyline != null) routePolyline.remove();
        PolylineOptions opts = new PolylineOptions().width(10f).color(0xFF1976D2).geodesic(true);
        opts.addAll(pts);
        routePolyline = gMap.addPolyline(opts);
    }

    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do { b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1)); lat += dlat;
            shift = 0; result = 0;
            do { b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1)); lng += dlng;
            double latD = lat / 1E5; double lonD = lng / 1E5; poly.add(new LatLng(latD, lonD));
        }
        return poly;
    }

    @Override public void onMapReady(@NonNull GoogleMap googleMap) {
        this.gMap = googleMap;
        centerCamera(false);
        pintarMarcadores(true);
        intentarRuta();
    }

    @Override public void onResume() { super.onResume(); if (mapView!=null) mapView.onResume(); if (mapView!=null) mapView.postDelayed(refresco, REFRESH_MS); }
    @Override public void onPause() { super.onPause(); if (mapView!=null) { mapView.onPause(); mapView.removeCallbacks(refresco);} }
    @Override public void onDestroyView() { super.onDestroyView(); if (mapView!=null) mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView!=null) mapView.onLowMemory(); }
}

