package com.hfad.encomiendas.ui;

import android.app.AlertDialog;
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

import com.google.android.material.button.MaterialButton;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.HubService;           // <-- IMPORT
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Manifiesto;
import com.hfad.encomiendas.data.ManifiestoDao;
import com.hfad.encomiendas.data.ManifiestoItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
public class HubDashboardFragment extends Fragment {

    private RecyclerView rv;
    private HubAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_hub_dashboard, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        rv = v.findViewById(R.id.rvManifiestos);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new HubAdapter();
        rv.setAdapter(adapter);

        // aquí lo llamas
        MaterialButton btnClasificar = v.findViewById(R.id.btnClasificar);
        btnClasificar.setOnClickListener(vw -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                int creados = new com.hfad.encomiendas.core.HubService(requireContext())
                        .clasificarGuiasPorDestino();  // NUEVO
                runOnUi(() -> {
                    Toast.makeText(requireContext(), "Clasificados: " + creados, Toast.LENGTH_SHORT).show();
                    cargar(); // refresca la lista de manifiestos
                });
            });
        });
    }

    /** Método para traer los manifiestos */
    private void cargar() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            ManifiestoDao dao = db.manifiestoDao();

            int total = dao.countAll();
            List<Manifiesto> lista = dao.listAbiertosODespachados();

            android.util.Log.d("HUB", "countAll=" + total +
                    "  abiertos+desp=" + (lista == null ? 0 : lista.size()));

            runOnUi(() -> adapter.setData(lista == null ?
                    java.util.Collections.emptyList() : lista));
        });
    }

    private void runOnUi(Runnable r) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(r);
    }

    // ... resto de HubAdapter y ViewHolder

    /* ================== Adapter ================== */
    static class HubAdapter extends RecyclerView.Adapter<VH> {

        private final List<Manifiesto> data = new ArrayList<>();
        private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        void setData(List<Manifiesto> list) { data.clear(); data.addAll(list); notifyDataSetChanged(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_manifiesto, p, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Manifiesto m = data.get(pos);

            h.tvCodigo.setText(n(m.codigo));
            String fecha = df.format(new Date(m.fechaMillis)); // fechaMillis es long
            h.tvInfo.setText(n(m.estado) + " • " + fecha);

            // VER ÍTEMS
            h.btnVerItems.setOnClickListener(v -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(h.itemView.getContext());
                    ManifiestoDao dao = db.manifiestoDao();
                    List<ManifiestoItem> items = dao.listItemsByManifiesto((int) m.id);

                    if (items == null || items.isEmpty()) {
                        new com.hfad.encomiendas.core.HubService(h.itemView.getContext()).clasificarGuiasHoy();
                        items = dao.listItemsByManifiesto((int) m.id);
                    }

                    final List<ManifiestoItem> finalItems = items == null ? new ArrayList<>() : items;
                    h.itemView.post(() -> {
                        if (finalItems.isEmpty()) {
                            Toast.makeText(h.itemView.getContext(), "Sin ítems en este manifiesto", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        for (ManifiestoItem it : finalItems) {
                            sb.append("Guía: ").append(n(it.guia))
                                    .append("  •  Estado: ").append(n(it.estado))
                                    .append("\nDestino: ").append(n(it.destinoCiudad))
                                    .append(" — ").append(n(it.destinoDireccion))
                                    .append("\nOTP: ").append(n(it.otp))
                                    .append("\n\n");
                        }
                        TextView tv = new TextView(h.itemView.getContext());
                        tv.setText(sb.toString().trim());
                        tv.setPadding(32, 24, 32, 24);

                        new AlertDialog.Builder(h.itemView.getContext())
                                .setTitle("Ítems de " + n(m.codigo))
                                .setView(tv)
                                .setPositiveButton("Cerrar", null)
                                .show();
                    });
                });
            });

            // DESPACHAR
            // dentro de onBindViewHolder(...) en HubAdapter
            h.btnDespachar.setOnClickListener(v -> {
                if ("DESPACHADO".equalsIgnoreCase(m.estado)) {
                    Toast.makeText(h.itemView.getContext(), "Ya está despachado", Toast.LENGTH_SHORT).show();
                    return;
                }
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(h.itemView.getContext());
                    ManifiestoDao dao = db.manifiestoDao();

                    // si no hay ítems, clasifica primero
                    if (dao.countItems((int) m.id) == 0) {
                        new com.hfad.encomiendas.core.HubService(h.itemView.getContext()).clasificarGuiasHoy();
                    }

                    long ts = System.currentTimeMillis();
                    dao.despacharManifiesto((int) m.id, ts, "repartidor1@gmail.com");
                    dao.ponerItemsEnRuta((int) m.id);   // asegúrate de tener este método en el DAO

                    h.itemView.post(() ->
                            Toast.makeText(h.itemView.getContext(),
                                    "Despachado a repartidor1@gmail.com", Toast.LENGTH_SHORT).show()
                    );
                });
            });
        }

        @Override public int getItemCount() { return data.size(); }

        private static String n(String s) { return (s == null || s.trim().isEmpty()) ? "—" : s.trim(); }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCodigo, tvInfo;
        MaterialButton btnVerItems, btnDespachar;   // <-- nombres alineados
        VH(@NonNull View item) {
            super(item);
            tvCodigo     = item.findViewById(R.id.tvCodigo);
            tvInfo       = item.findViewById(R.id.tvInfo);
            btnVerItems  = item.findViewById(R.id.btnVerItems);
            btnDespachar = item.findViewById(R.id.btnDespachar);
        }
    }
}