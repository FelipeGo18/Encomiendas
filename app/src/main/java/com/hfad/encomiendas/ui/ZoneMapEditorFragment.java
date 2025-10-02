package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.GeoUtils;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Zone;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Editor de polígono de zona (placeholder programático).
 * Próximo paso: integrar Google Map y permitir añadir vértices.
 */
public class ZoneMapEditorFragment extends Fragment implements OnMapReadyCallback {

    private MapView mapView;
    private GoogleMap gMap;
    private final List<LatLng> puntos = new ArrayList<>();
    private Polygon polygon;
    private TextView tvInfo;
    private long zoneId = -1L;
    private Zone currentZone;

    public ZoneMapEditorFragment() {}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_zone_map_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        if (getArguments()!=null) zoneId = (long) getArguments().getInt("zoneId", -1);
        mapView = v.findViewById(R.id.mapZoneEditor);
        tvInfo  = v.findViewById(R.id.tvPolyInfo);
        Button btnUndo = v.findViewById(R.id.btnUndoPoint);
        Button btnClear= v.findViewById(R.id.btnClearPolygon);
        Button btnSave = v.findViewById(R.id.btnSavePolygon);

        if (mapView!=null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        }

        btnUndo.setOnClickListener(v1 -> {
            if (!puntos.isEmpty()) {
                puntos.remove(puntos.size()-1);
                redraw();
            }
        });
        btnClear.setOnClickListener(v12 -> { puntos.clear(); redraw(); });
        btnSave.setOnClickListener(v13 -> guardarPoligono());

        cargarZona();
    }

    private void cargarZona(){
        if (zoneId <= 0) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            Zone z = db.zoneDao().getById(zoneId);
            currentZone = z;
            if (z!=null) {
                List<LatLng> existing = GeoUtils.jsonToPolygon(z.polygonJson);
                if (!existing.isEmpty()) {
                    puntos.clear();
                    puntos.addAll(existing);
                }
            }
            runOnUi(this::redraw);
        });
    }

    private void guardarPoligono(){
        if (currentZone == null) {
            toast("Zona no encontrada");
            return;
        }
        if (puntos.size()<3) { toast("Necesitas mínimo 3 puntos"); return; }
        String json = GeoUtils.polygonToJson(puntos);
        currentZone.polygonJson = json;
        currentZone.updatedAt = System.currentTimeMillis();
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(requireContext()).zoneDao().update(currentZone);
            runOnUi(() -> {
                toast("Polígono guardado");
                requireActivity().onBackPressed();
            });
        });
    }

    @Override public void onMapReady(@NonNull GoogleMap googleMap) {
        gMap = googleMap;
        gMap.getUiSettings().setZoomControlsEnabled(true);
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(4.65, -74.1), 11f));
        gMap.setOnMapClickListener(latLng -> {
            puntos.add(latLng);
            redraw();
        });
        redraw();
    }

    private void redraw(){
        if (gMap==null) return;
        gMap.clear();
        for (int i=0;i<puntos.size();i++) {
            LatLng p = puntos.get(i);
            gMap.addMarker(new MarkerOptions().position(p).title("#"+(i+1)));
        }
        if (puntos.size()>=3) {
            PolygonOptions opts = new PolygonOptions().addAll(puntos)
                    .strokeWidth(6f).strokeColor(0xFF673AB7).fillColor(0x33673AB7);
            polygon = gMap.addPolygon(opts);
        } else {
            polygon = null;
        }
        if (tvInfo!=null) tvInfo.setText(puntos.size()+" puntos");
    }

    private void runOnUi(Runnable r){ if (isAdded()) requireActivity().runOnUiThread(r); }
    private void toast(String t){ if (isAdded()) Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show(); }

    // ==== Lifecycle MapView ====
    @Override public void onResume(){ super.onResume(); if (mapView!=null) mapView.onResume(); }
    @Override public void onPause(){ super.onPause(); if (mapView!=null) mapView.onPause(); }
    @Override public void onDestroy(){ if (mapView!=null) mapView.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory(){ super.onLowMemory(); if (mapView!=null) mapView.onLowMemory(); }
    @Override public void onSaveInstanceState(@NonNull Bundle outState){ super.onSaveInstanceState(outState); if (mapView!=null) mapView.onSaveInstanceState(outState); }
}
