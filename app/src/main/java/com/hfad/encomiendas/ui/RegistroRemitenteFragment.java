package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.hfad.encomiendas.R;

public class RegistroRemitenteFragment extends Fragment {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registro_remitente, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tilEmail = view.findViewById(R.id.tilEmail);
        tilPassword = view.findViewById(R.id.tilPassword);
        etEmail = view.findViewById(R.id.etEmail);
        etPassword = view.findViewById(R.id.etPassword);

        MaterialButton btnRegistrar = view.findViewById(R.id.btnRegistrar);
        MaterialButton btnIrARecoleccion = view.findViewById(R.id.btnIrARecoleccion);

        btnRegistrar.setOnClickListener(v -> {
            clearErrors();
            String email = safeText(etEmail);
            String pwd   = safeText(etPassword);

            boolean ok = true;

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError("Ingresa un correo válido");
                ok = false;
            }
            if (pwd.length() < 6) {
                tilPassword.setError("Mínimo 6 caracteres");
                ok = false;
            }

            if (!ok) return;

            // MOCK de registro: aquí luego conectarás backend/Room/Firebase
            Toast.makeText(requireContext(), "Registro exitoso (mock)", Toast.LENGTH_SHORT).show();
        });

        btnIrARecoleccion.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.action_registro_to_recoleccion));
    }

    private String safeText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void clearErrors() {
        tilEmail.setError(null);
        tilPassword.setError(null);
    }
}
