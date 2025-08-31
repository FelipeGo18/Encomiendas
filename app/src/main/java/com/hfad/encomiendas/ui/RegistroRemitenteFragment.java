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
import com.hfad.encomiendas.core.PasswordUtils;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.User;
import com.hfad.encomiendas.data.UserDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegistroRemitenteFragment extends Fragment {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registro_remitente, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tilEmail = view.findViewById(R.id.tilEmail);
        tilPassword = view.findViewById(R.id.tilPassword);
        etEmail = view.findViewById(R.id.etEmail);
        etPassword = view.findViewById(R.id.etPassword);

        // Botón principal: crear cuenta
        MaterialButton btnRegistrar = view.findViewById(R.id.btnRegistrar);
        btnRegistrar.setOnClickListener(v -> doRegister(view));

        // Botón secundario: ir a iniciar sesión
        MaterialButton btnIrALogin = view.findViewById(R.id.btnIrALogin);
        btnIrALogin.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_registro_to_login));
    }

    private void doRegister(View root) {
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

        String hash = PasswordUtils.sha256(pwd);
        AppDatabase db = AppDatabase.getInstance(requireContext());

        executor.execute(() -> {
            UserDao dao = db.userDao();
            User existing = dao.findByEmail(email);
            if (existing != null) {
                requireActivity().runOnUiThread(() ->
                        tilEmail.setError("Ese correo ya está registrado"));
                return;
            }

            User u = new User();
            u.email = email;
            u.passwordHash = hash;
            u.createdAt = System.currentTimeMillis();
            dao.insert(u);

            // Éxito: ir a Login y prellenar el correo (no inicia sesión automática)
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "Cuenta creada. Inicia sesión.", Toast.LENGTH_SHORT).show();
                Bundle args = new Bundle();
                args.putString("email_prefill", email);
                Navigation.findNavController(root)
                        .navigate(R.id.action_registro_to_login, args);
            });
        });
    }

    private String safeText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void clearErrors() {
        tilEmail.setError(null);
        tilPassword.setError(null);
    }
}
