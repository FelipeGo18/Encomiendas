package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.data.Recolector;
import com.hfad.encomiendas.data.RecolectorDao;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MisAsignacionesFragment extends Fragment {

    private SwipeRefreshLayout swipe;
    private RecyclerView rv;
    private AsignacionesAdapter adapter;

    public MisAsignacionesFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mis_asignaciones, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        swipe = v.findViewById(R.id.swipeRefresh);
        rv = v.findViewById(R.id.rvAsignaciones);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new AsignacionesAdapter(asignacionId -> {
            NavController nav = NavHostFragment.findNavController(this);
            Bundle args = new Bundle();
            args.putInt("asignacionId", asignacionId);
            nav.navigate(R.id.action_misAsignaciones_to_detalle, args);
        });
        rv.setAdapter(adapter);

        if (swipe != null) swipe.setOnRefreshListener(this::loadData);

        loadData();
    }

    private void loadData() {
        if (swipe != null) swipe.setRefreshing(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                SessionManager sm = new SessionManager(requireContext());
                String email = sm.getEmail();

                RecolectorDao rdao = db.recolectorDao();
                Recolector rec = (email == null) ? null : rdao.getByUserEmail(email);
                int recolectorId = (rec != null) ? rec.id : 1; // fallback

                String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                List<AsignacionDao.AsignacionDetalle> items =
                        db.asignacionDao().listDetalleByRecolectorFecha(recolectorId, hoy);

                runOnUi(() -> {
                    adapter.setData(items == null ? new ArrayList<>() : items);
                    if (swipe != null) swipe.setRefreshing(false);
                });

            } catch (Exception e) {
                runOnUi(() -> {
                    if (swipe != null) swipe.setRefreshing(false);
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /* ---------------- Adapter interno ---------------- */

    private static class AsignacionesAdapter extends RecyclerView.Adapter<VH> {
        interface OnItemClick { void onClick(int asignacionId); }

        private final List<AsignacionDao.AsignacionDetalle> data = new ArrayList<>();
        private final OnItemClick clickCb;

        AsignacionesAdapter(OnItemClick cb) { this.clickCb = cb; }

        void setData(List<AsignacionDao.AsignacionDetalle> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_asignacion_recolector, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AsignacionDao.AsignacionDetalle m = data.get(pos);

            // Título
            String ord = (m.ordenRuta == null) ? "—" : String.valueOf(m.ordenRuta);
            h.tvTitulo.setText("#" + (pos + 1) + " • " + (m.estado == null ? "—" : m.estado) + " • Orden:" + ord);

            // Subtítulo
            String tam = (m.tamanoPaquete == null ? "—" : m.tamanoPaquete);
            String co  = (m.ciudadOrigen  == null ? "—" : m.ciudadOrigen);
            String cd  = (m.ciudadDestino == null ? "—" : m.ciudadDestino);
            h.tvSub.setText("Paquete " + tam + " — " + co + "  →  " + cd);

            // Columnas (origen/destino/pago/valor)
            h.tvOrigenDir.setText( nn(m.direccion) );
            h.tvDestinoDir.setText( nn(m.destinoDir) );
            h.tvPago.setText( nn(m.pago) );
            h.tvValor.setText( nn(m.valor) );

            // Rango hora si viene en el modelo
            String rango = (m.horaDesde == null ? "" : m.horaDesde) + "–" + (m.horaHasta == null ? "" : m.horaHasta);
            h.tvDetalle.setText(rango);

            h.itemView.setOnClickListener(v ->
            { if (clickCb != null) clickCb.onClick(m.id); });
        }

        @Override public int getItemCount() { return data.size(); }

        private static @NonNull String nn(String s) { return (s == null || s.trim().isEmpty()) ? "—" : s.trim(); }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitulo, tvSub, tvOrigenDir, tvDestinoDir, tvPago, tvValor, tvDetalle;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitulo    = itemView.findViewById(R.id.tvTitulo);
            tvSub       = itemView.findViewById(R.id.tvSub);
            tvOrigenDir = itemView.findViewById(R.id.tvOrigenDir);
            tvDestinoDir= itemView.findViewById(R.id.tvDestinoDir);
            tvPago      = itemView.findViewById(R.id.tvPago);
            tvValor     = itemView.findViewById(R.id.tvValor);
            tvDetalle   = itemView.findViewById(R.id.tvDetalle);
        }
    }

    private void runOnUi(Runnable r) { if (!isAdded()) return; requireActivity().runOnUiThread(r); }
}