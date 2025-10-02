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

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.GeoUtils;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.SolicitudDao;
import com.hfad.encomiendas.data.Zone;
import com.google.maps.android.clustering.ClusterManager;
import com.hfad.encomiendas.ui.ZonaClusterItem.Tipo;
import com.hfad.encomiendas.core.TrackingService;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import com.hfad.encomiendas.data.RecolectorDao;

public class ZonaMapaFullFragment extends Fragment implements OnMapReadyCallback {

    private String fecha; private String zona; private long zoneId = -1L;
    private GoogleMap map; private Polygon drawnPolygon;
    private TextView tvInfoRuta;

    private ClusterManager<ZonaClusterItem> clusterManager;
    private FloatingActionButton btnFollowFull;
    private boolean followEnabled = false;
    private LatLng lastFollowLatLng = null; private int followedRecolectorId = -1;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable(){
        @Override public void run(){ cargarYMostrar(); refreshHandler.postDelayed(this, 20000);} };

    public ZonaMapaFullFragment() {}

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_zona_mapa_full, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        if (getArguments()!=null){
            fecha = getArguments().getString("fecha");
            zona  = getArguments().getString("zona");
            zoneId = (long) getArguments().getInt("zoneId", -1);
        }
        tvInfoRuta = v.findViewById(R.id.tvInfoRuta);
        SupportMapFragment mf = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map_full_container);
        if (mf == null) {
            mf = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction().replace(R.id.map_full_container, mf).commitNow();
        }
        mf.getMapAsync(this);
        btnFollowFull = v.findViewById(R.id.btnFollowFull);
        if (btnFollowFull != null) btnFollowFull.setOnClickListener(_v -> toggleFollow());
    }

    @Override public void onMapReady(@NonNull GoogleMap googleMap) { map = googleMap; cargarYMostrar(); }

    @Override public void onResume(){ super.onResume(); refreshHandler.postDelayed(refreshTask, 20000); }
    @Override public void onPause(){ super.onPause(); refreshHandler.removeCallbacks(refreshTask); }

    private void toggleFollow(){
        followEnabled = !followEnabled;
        if (btnFollowFull != null){
            btnFollowFull.setContentDescription(followEnabled? getString(R.string.follow_on): getString(R.string.follow_off));
            btnFollowFull.setAlpha(followEnabled?1f:0.6f);
            if (followEnabled && lastFollowLatLng != null && map!=null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(lastFollowLatLng, map.getCameraPosition().zoom));
        }
    }

    private void initCluster(){
        if (map == null) return;
        clusterManager = new ClusterManager<>(requireContext(), map);
        map.setOnCameraIdleListener(clusterManager);
        map.setOnMarkerClickListener(clusterManager);
        map.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE && followEnabled){
                followEnabled = false; if (btnFollowFull!=null){ btnFollowFull.setAlpha(0.6f); btnFollowFull.setContentDescription(getString(R.string.follow_off)); }
            }
        });
    }

    private void cargarYMostrar(){
        if (TextUtils.isEmpty(fecha) || TextUtils.isEmpty(zona) || map == null) return;
        map.clear(); clusterManager = null; initCluster();
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            List<Solicitud> pendientes = db.solicitudDao().listUnassignedByFechaZona(fecha, zona);
            List<AsignacionDao.RutaPunto> ruta = db.asignacionDao().rutaByFechaZona(fecha, zona);
            List<RecolectorDao.RecolectorPos> recs = db.recolectorDao().positionsByZona(zona);
            Zone z = zoneId>0 ? db.zoneDao().getById(zoneId) : null;
            runOnUi(() -> dibujarMapa(pendientes, ruta, recs, z));
        });
    }

    private void dibujarMapa(List<Solicitud> pendientes, List<AsignacionDao.RutaPunto> ruta, List<RecolectorDao.RecolectorPos> recs, Zone zonaEntity){
        if (clusterManager == null) return;
        clusterManager.clearItems(); drawnPolygon = null;
        LatLngBounds.Builder b = new LatLngBounds.Builder(); boolean has=false;
        // Pendientes
        if (pendientes!=null) for (Solicitud s: pendientes){
            if (s.lat==null||s.lon==null) continue; LatLng p = new LatLng(s.lat,s.lon);
            clusterManager.addItem(new ZonaClusterItem(p, "Pendiente", s.direccion, ZonaClusterItem.Tipo.PENDIENTE));
            b.include(p); has=true; }
        // Ruta
        List<LatLng> poly = new ArrayList<>();
        if (ruta!=null) for (AsignacionDao.RutaPunto rp: ruta){
            if (rp.lat==null||rp.lon==null) continue; LatLng p = new LatLng(rp.lat,rp.lon);
            clusterManager.addItem(new ZonaClusterItem(p, "Parada "+(rp.orden==null?"?":rp.orden), rp.direccion, ZonaClusterItem.Tipo.PARADA));
            poly.add(p); b.include(p); has=true; }
        if (poly.size()>=2) map.addPolyline(new PolylineOptions().addAll(poly).width(10f).color(0xFF2196F3));
        // Recolectores + ETA
        LatLng firstRuta = poly.isEmpty()? null : poly.get(0);
        double bestDistKm = Double.MAX_VALUE; LatLng bestPos=null; int bestId=-1;
        if (recs!=null) for (RecolectorDao.RecolectorPos r: recs){
            if (r.lat==null||r.lon==null) continue; LatLng pr = new LatLng(r.lat,r.lon);
            clusterManager.addItem(new ZonaClusterItem(pr, "Recolector #"+r.id, null, ZonaClusterItem.Tipo.RECOLECTOR));
            b.include(pr); has=true;
            if (firstRuta!=null){
                map.addPolyline(new PolylineOptions().add(pr, firstRuta).width(6f).color(0xFF4CAF50));
                double d = TrackingService.haversine(pr.latitude, pr.longitude, firstRuta.latitude, firstRuta.longitude);
                if (d < bestDistKm){ bestDistKm=d; bestPos=pr; bestId=r.id; }
            }
        }
        // Polígono zona
        if (zonaEntity!=null){
            List<LatLng> pts = GeoUtils.jsonToPolygon(zonaEntity.polygonJson);
            if (pts.size()>=3){
                try { drawnPolygon = map.addPolygon(new PolygonOptions().addAll(pts).strokeColor(0xFF673AB7).strokeWidth(6f).fillColor(0x22673AB7)); } catch (Exception ignored) {}
                for (LatLng p: pts){ b.include(p); has=true; }
            }
        }
        // ETA info ruta
        int pendientesC = pendientes==null?0:pendientes.size();
        int rutaC = ruta==null?0:ruta.size();
        int recsC = recs==null?0:recs.size();
        if (tvInfoRuta!=null){
            String etaStr="";
            if (bestPos!=null){ double speed=25.0; long etaMin=Math.round(bestDistKm/speed*60); etaStr = "  ETA ~"+etaMin+"m Dist "+String.format(java.util.Locale.getDefault(),"%.1fkm", bestDistKm); }
            tvInfoRuta.setText("Pend: "+pendientesC+" Ruta: "+rutaC+" Recs: "+recsC+etaStr);
        }
        if (followEnabled && bestPos!=null){ lastFollowLatLng=bestPos; followedRecolectorId=bestId; map.animateCamera(CameraUpdateFactory.newLatLng(bestPos)); }
        else if (bestPos!=null){ lastFollowLatLng=bestPos; followedRecolectorId=bestId; }
        if (has){ try { map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 140)); } catch (Exception ignore) {} }
        else map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(4.65,-74.1), 11f));
        clusterManager.cluster();
    }

    private void runOnUi(Runnable r){ if (isAdded()) requireActivity().runOnUiThread(r); }
    private void toast(String t){ if (isAdded()) Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show(); }
}
