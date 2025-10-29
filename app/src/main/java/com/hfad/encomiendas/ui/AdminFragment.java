package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.SessionManager;

public class AdminFragment extends Fragment {

    private TextView tvAdminEmail;
    private CardView cardEstadisticas;
    private CardView cardGestionUsuarios;
    private CardView cardConfiguracion;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin, container, false);

        initViews(view);
        setupListeners();
        loadAdminInfo();

        return view;
    }

    private void initViews(View view) {
        tvAdminEmail = view.findViewById(R.id.tvAdminEmail);
        cardEstadisticas = view.findViewById(R.id.cardEstadisticas);
        cardGestionUsuarios = view.findViewById(R.id.cardGestionUsuarios);
        cardConfiguracion = view.findViewById(R.id.cardConfiguracion);
    }

    private void setupListeners() {
        cardEstadisticas.setOnClickListener(v -> {
            NavHostFragment.findNavController(this).navigate(R.id.action_admin_to_estadisticas);
        });

        cardGestionUsuarios.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Gestión de Usuarios - En desarrollo", Toast.LENGTH_SHORT).show();
        });

        cardConfiguracion.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Configuración del Sistema - En desarrollo", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadAdminInfo() {
        SessionManager session = new SessionManager(requireContext());
        String email = session.getEmail();

        if (email != null && !email.isEmpty()) {
            tvAdminEmail.setText(email);
        }
    }
}
