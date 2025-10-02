package com.hfad.encomiendas.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.AsignadorService;
import com.hfad.encomiendas.core.RoutePlanningPoligonoService;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Recolector;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.ui.adapters.RutaParadasAdapter;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;

public class PoligonoRutaPreviewFragment extends Fragment implements OnMapReadyCallback {

    private long zoneId; private String fecha;
    private GoogleMap gMap;
    private RutaParadasAdapter adapter;
    private TextView tvResumenRuta, tvHoraInicio;
    private View btnConfirmar;
    private long horaInicioMillis = -1L;
    private RoutePlanningPoligonoService.PlanResult planResult;

    public PoligonoRutaPreviewFragment() {}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_poligono_ruta_preview, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        if (getArguments()!=null){
            zoneId = (long) getArguments().getInt("zoneId", -1);
            fecha  = getArguments().getString("fecha");
        }
        tvResumenRuta = v.findViewById(R.id.tvResumenRuta);
        tvHoraInicio  = v.findViewById(R.id.tvHoraInicio);
        btnConfirmar  = v.findViewById(R.id.btnConfirmarRuta);
        RecyclerView rv = v.findViewById(R.id.rvParadas);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RutaParadasAdapter();
        rv.setAdapter(adapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP|ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                adapter.move(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                dibujarRuta(); actualizarResumen();
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
            @Override public boolean isLongPressDragEnabled(){ return true; }
        });
        touchHelper.attachToRecyclerView(rv);

        tvHoraInicio.setOnClickListener(_v -> seleccionarHora());
        btnConfirmar.setOnClickListener(_v -> confirmarAsignacion());

        SupportMapFragment mf = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.mapPreviewContainer);
        if (mf == null) {
            mf = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction().replace(R.id.mapPreviewContainer, mf).commitNow();
        }
        mf.getMapAsync(this);
        cargarPlan();
    }

    private void seleccionarHora(){
        Calendar cal = Calendar.getInstance();
        int h = cal.get(Calendar.HOUR_OF_DAY); int m = cal.get(Calendar.MINUTE);
        new TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
            Calendar sel = Calendar.getInstance();
            sel.set(Calendar.HOUR_OF_DAY, hourOfDay); sel.set(Calendar.MINUTE, minute); sel.set(Calendar.SECOND,0);
            horaInicioMillis = sel.getTimeInMillis();
            tvHoraInicio.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
        }, h, m, DateFormat.is24HourFormat(requireContext())).show();
    }

    private void cargarPlan(){
        if (zoneId <= 0 || fecha == null) { toast("Argumentos inválidos"); return; }
        Executors.newSingleThreadExecutor().execute(() -> {
            planResult = new RoutePlanningPoligonoService(requireContext()).plan(fecha, zoneId);
            runOnUi(() -> {
                adapter.setData(planResult.ordenOptimizado);
                actualizarResumen();
                dibujarRuta();
                if (planResult.ordenOptimizado.isEmpty()) btnConfirmar.setEnabled(false);
            });
        });
    }

    private void actualizarResumen(){
        int n = adapter.getItemCount();
        double dist = planResult == null ? 0 : planResult.distanciaTotalM;
        tvResumenRuta.setText("Paradas: " + n + "  •  Distancia aprox: " + String.format(java.util.Locale.getDefault(), "%.1f km", dist/1000.0));
    }

    private void dibujarRuta(){
        if (gMap == null) return;
        gMap.clear();
        List<Solicitud> list = adapter.getData();
        LatLngBounds.Builder b = new LatLngBounds.Builder();
        boolean has = false;
        for (int i=0;i<list.size();i++) {
            Solicitud s = list.get(i);
            if (s.lat==null||s.lon==null) continue;
            LatLng p = new LatLng(s.lat,s.lon);
            gMap.addMarker(new MarkerOptions().position(p).title("#"+(i+1)));
            b.include(p); has=true;
        }
        if (list.size()>=2){
            PolylineOptions po = new PolylineOptions();
            for (Solicitud s: list) if (s.lat!=null&&s.lon!=null) po.add(new LatLng(s.lat,s.lon));
            po.width(8f);
            gMap.addPolyline(po);
        }
        if (has){
            try { gMap.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120)); } catch (Exception ignore) {}
        }
    }

    private void confirmarAsignacion(){
        if (adapter.getItemCount()==0){ toast("Sin paradas"); return; }
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            Recolector reco = db.recolectorDao().findByZona("");
            if (reco == null) {
                List<Recolector> all = db.recolectorDao().listAll();
                if (!all.isEmpty()) reco = all.get(0);
            }
            if (reco == null) { runOnUi(() -> toast("No hay recolectores")); return; }
            List<Solicitud> orden = adapter.getData();
            AsignadorService svc = new AsignadorService(requireContext());
            int n = svc.assignRutaOrdenada(fecha, reco.id, orden, horaInicioMillis <=0 ? null : horaInicioMillis, true);
            runOnUi(() -> {
                toast(n+" asignadas");
                androidx.navigation.Navigation.findNavController(requireView()).popBackStack();
            });
        });
    }

    @Override public void onMapReady(@NonNull GoogleMap googleMap) { gMap = googleMap; dibujarRuta(); }

    private void runOnUi(Runnable r){ if (isAdded()) requireActivity().runOnUiThread(r); }
    private void toast(String t){ if (isAdded()) Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show(); }
}
