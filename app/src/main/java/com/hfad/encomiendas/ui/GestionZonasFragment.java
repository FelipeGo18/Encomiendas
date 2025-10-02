package com.hfad.encomiendas.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Zone;
import com.hfad.encomiendas.ui.adapters.ZoneListAdapter;

import java.util.List;
import java.util.concurrent.Executors;

/** Gestiona la lista de zonas (CRUD básico + acceso a edición de polígono y detalle). */
public class GestionZonasFragment extends Fragment {

    private RecyclerView rv;
    private TextView tvEmpty;
    private ZoneListAdapter adapter;

    public GestionZonasFragment() {}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gestion_zonas, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        rv = v.findViewById(R.id.rvZones);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ZoneListAdapter(new ZoneListAdapter.Listener() {
            @Override public void onEdit(Zone z) { dialogNombre(z); }
            @Override public void onDelete(Zone z) { confirmarDesactivar(z); }
            @Override public void onEditPolygon(Zone z) { abrirEditorPoligono(z.id); }
            @Override public void onOpenDetalle(Zone z) { abrirDetalleZona(z); }
        });
        rv.setAdapter(adapter);

        FloatingActionButton fab = v.findViewById(R.id.fabAddZone);
        if (fab != null) fab.setOnClickListener(_v -> dialogNombre(null));

        cargarZonas();
    }

    private void cargarZonas(){
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Zone> list = AppDatabase.getInstance(requireContext()).zoneDao().listActivas();
            runUi(() -> {
                adapter.setData(list);
                tvEmpty.setVisibility(list == null || list.isEmpty()? View.VISIBLE:View.GONE);
            });
        });
    }

    private void dialogNombre(@Nullable Zone existente){
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (existente != null) et.setText(existente.nombre);
        new AlertDialog.Builder(requireContext())
                .setTitle(existente == null? "Nueva zona" : "Renombrar zona")
                .setView(et)
                .setPositiveButton("Guardar", (d,w)-> {
                    String nombre = et.getText()==null?"":et.getText().toString().trim();
                    if (nombre.isEmpty()){ toast("Nombre vacío"); return; }
                    if (existente==null) crearZona(nombre); else renombrarZona(existente, nombre);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void crearZona(String nombre){
        Executors.newSingleThreadExecutor().execute(() -> {
            long now = System.currentTimeMillis();
            Zone z = new Zone();
            z.nombre = nombre;
            z.polygonJson = "[]";
            z.createdAt = now; z.updatedAt = now;
            try {
                AppDatabase.getInstance(requireContext()).zoneDao().insert(z);
                runUi(() -> { toast("Zona creada"); cargarZonas(); });
            } catch (Exception e){ runUi(() -> toast("Error: "+e.getMessage())); }
        });
    }

    private void renombrarZona(Zone z, String nuevoNombre){
        Executors.newSingleThreadExecutor().execute(() -> {
            z.nombre = nuevoNombre;
            z.updatedAt = System.currentTimeMillis();
            try {
                AppDatabase.getInstance(requireContext()).zoneDao().update(z);
                runUi(() -> { toast("Actualizada"); cargarZonas(); });
            } catch (Exception e){ runUi(() -> toast("Error: "+e.getMessage())); }
        });
    }

    private void confirmarDesactivar(Zone z){
        new AlertDialog.Builder(requireContext())
                .setTitle("Desactivar zona")
                .setMessage("¿Seguro que deseas desactivarla? Se ocultará de la lista.")
                .setPositiveButton("Sí", (d,w)-> desactivarZona(z))
                .setNegativeButton("No", null)
                .show();
    }

    private void desactivarZona(Zone z){
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase.getInstance(requireContext()).zoneDao().softDelete(z.id, System.currentTimeMillis());
                runUi(() -> { toast("Zona desactivada"); cargarZonas(); });
            } catch (Exception e){ runUi(() -> toast("Error: "+e.getMessage())); }
        });
    }

    private void abrirEditorPoligono(long zoneId){
        Bundle b = new Bundle();
        b.putInt("zoneId", (int) zoneId);
        androidx.navigation.Navigation.findNavController(requireView())
                .navigate(R.id.action_gestionZonas_to_zoneMapEditor, b);
    }

    private void abrirDetalleZona(Zone z){
        Bundle b = new Bundle();
        b.putString("fecha", new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date()));
        b.putString("zona", z.nombre);
        b.putInt("zoneId", (int) z.id);
        androidx.navigation.Navigation.findNavController(requireView())
                .navigate(R.id.zonaDetalleFragment, b);
    }

    private void runUi(Runnable r){ if (isAdded()) requireActivity().runOnUiThread(r); }
    private void toast(String t){ if (isAdded()) Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show(); }
}
