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

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.data.SolicitudDao;
import com.hfad.encomiendas.ui.adapters.PendienteDetalleAdapter;
import com.hfad.encomiendas.ui.adapters.ZonaDetalleAdapter;

import java.util.List;
import java.util.concurrent.Executors;

public class ZonaDetalleFragment extends Fragment {

    private String fecha;
    private String zona;

    private TextView tvTitulo;
    private RecyclerView rvPendientes, rvAsignadas;
    private PendienteDetalleAdapter pendientesAdapter;
    private ZonaDetalleAdapter asignadasAdapter;

    public ZonaDetalleFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_zona_detalle, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        fecha = (getArguments() != null) ? getArguments().getString("fecha") : null;
        zona  = (getArguments() != null) ? getArguments().getString("zona")  : null;

        tvTitulo      = v.findViewById(R.id.tvTituloZona);
        rvPendientes  = v.findViewById(R.id.rvPendientes);
        rvAsignadas   = v.findViewById(R.id.rvAsignadas);

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
        cargar();
    }

    private void cargar() {
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

    private void runOnUi(Runnable r) { if (!isAdded()) return; requireActivity().runOnUiThread(r); }
    private void toast(String t) { if (!isAdded()) return; Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show(); }
}
