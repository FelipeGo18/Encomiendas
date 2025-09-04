package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

public class MisAsignacionesFragment extends Fragment {

    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<Asignacion> asignaciones = new ArrayList<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mis_asignaciones, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        listView = view.findViewById(R.id.lvAsignaciones);
        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);

        view.findViewById(R.id.btnRefrescar).setOnClickListener(v -> cargar());

        listView.setOnItemClickListener((parent, v, pos, id) -> {
            int asignacionId = asignaciones.get(pos).id;
            Bundle args = new Bundle();
            args.putInt("asignacionId", asignacionId);
            Navigation.findNavController(view).navigate(R.id.detalleRecoleccionFragment, args);
        });

        cargar();
    }

    private void cargar() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            SessionManager sm = new SessionManager(requireContext());
            String email = sm.getEmail();

            Recolector reco = db.recolectorDao().getByUserEmail(email);
            if (reco == null) {
                runOnUi(() -> {
                    adapter.clear();
                    adapter.add("No hay un recolector vinculado a " + email);
                    adapter.notifyDataSetChanged();
                });
                return;
            }
            String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            asignaciones = db.asignacionDao().listByRecolectorAndFecha(reco.id, hoy);

            List<String> rows = new ArrayList<>();
            for (Asignacion a : asignaciones) {
                rows.add("#" + a.id + " • " + a.estado + " • OTP:" + a.otp + " • Orden:" + a.ordenRuta);
            }
            runOnUi(() -> {
                adapter.clear();
                if (rows.isEmpty()) rows.add("No tienes asignaciones para hoy (" + hoy + ")");
                adapter.addAll(rows);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void runOnUi(Runnable r) {
        requireActivity().runOnUiThread(r);
    }
}
