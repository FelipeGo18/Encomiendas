package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.TrackingEventDao;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.Executors;

public class SolicitudMapaFragment extends Fragment implements OnMapReadyCallback {

    private long solicitudId = -1;
    private MapView mapView; private GoogleMap gMap; private Marker markerRecolector;
    private TextView tvInfo, btnCerrar;
    private View fabCenter;
    private Double lastLat, lastLon; private String lastWhen;

    private final Runnable refresco = new Runnable() {
        @Override public void run() { cargarUbicacion(); mapView.postDelayed(this, 30_000); }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_solicitud_mapa, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        solicitudId = getArguments() != null ? getArguments().getLong("solicitudId", -1) : -1;
        mapView = v.findViewById(R.id.mapFull);
        tvInfo  = v.findViewById(R.id.tvInfo);
        btnCerrar = v.findViewById(R.id.btnCerrar);
        fabCenter = v.findViewById(R.id.fabCenter);

        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        }
        if (btnCerrar != null) btnCerrar.setOnClickListener(vw -> requireActivity().onBackPressed());
        if (fabCenter != null) fabCenter.setOnClickListener(vw -> center());
        cargarUbicacion();
    }

    private void cargarUbicacion() {
        if (solicitudId <= 0 || !isAdded()) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                TrackingEventDao.LastLoc loc = AppDatabase.getInstance(requireContext())
                        .trackingEventDao().lastLocationForShipment(solicitudId);
                if (loc != null && loc.lat != null && loc.lon != null) {
                    lastLat = loc.lat; lastLon = loc.lon; lastWhen = loc.whenIso;
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        actualizarUi(); pintar();
                    });
                }
            } catch (Exception ignore) {}
        });
    }

    private void actualizarUi() {
        if (tvInfo == null) return;
        String delta = deltaHuman(lastWhen);
        tvInfo.setText("Recolector: " + (lastLat==null?"-":String.format(Locale.getDefault(),"%.5f, %.5f", lastLat,lastLon)) + (TextUtils.isEmpty(delta)?"":" ("+delta+")"));
    }

    private String deltaHuman(String iso) {
        if (iso == null) return "";
        try {
            String base = iso.replace('Z',' ').trim();
            if (base.length() >= 19) base = base.substring(0,19);
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            long t = f.parse(base).getTime();
            long diff = Math.max(0, System.currentTimeMillis() - t);
            long mins = diff/60000L;
            if (mins < 1) return "hace instantes";
            if (mins < 60) return "hace "+mins+" min";
            long hrs=mins/60; if (hrs<24) return "hace "+hrs+" h";
            long d=hrs/24; return "hace "+d+" d";
        } catch(Exception e){return "";}
    }

    private void pintar() {
        if (gMap == null || lastLat==null || lastLon==null) return;
        LatLng p = new LatLng(lastLat, lastLon);
        if (markerRecolector == null) {
            markerRecolector = gMap.addMarker(new MarkerOptions().position(p).title("Recolector"));
        } else markerRecolector.setPosition(p);
        gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(p, 15f));
    }

    private void center() { pintar(); }

    @Override public void onMapReady(@NonNull GoogleMap googleMap) { this.gMap = googleMap; pintar(); }

    @Override public void onResume() { super.onResume(); if (mapView!=null) mapView.onResume(); mapView.postDelayed(refresco, 30_000); }
    @Override public void onPause() { super.onPause(); if (mapView!=null) mapView.onPause(); mapView.removeCallbacks(refresco); }
    @Override public void onDestroyView() { super.onDestroyView(); if (mapView!=null) mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView!=null) mapView.onLowMemory(); }
}

