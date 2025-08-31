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
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.User;
import com.hfad.encomiendas.data.UserDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginFragment extends Fragment {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tilEmail = view.findViewById(R.id.tilEmailLogin);
        tilPassword = view.findViewById(R.id.tilPasswordLogin);
        etEmail = view.findViewById(R.id.etEmailLogin);
        etPassword = view.findViewById(R.id.etPasswordLogin);

        // Prefill si venimos del registro
        if (getArguments() != null) {
            String emailPrefill = getArguments().getString("email_prefill", "");
            if (!emailPrefill.isEmpty()) etEmail.setText(emailPrefill);
        }

        MaterialButton btnLogin = view.findViewById(R.id.btnIniciarSesion);
        MaterialButton btnIrARegistro = view.findViewById(R.id.btnIrARegistro);

        btnLogin.setOnClickListener(v -> intentarLogin(view));
        btnIrARegistro.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_login_to_registro));
    }

    private void intentarLogin(View root) {
        clearErrors();

        String email = safeText(etEmail);
        String pwd   = safeText(etPassword);

        boolean ok = true;
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Correo inválido"); ok = false;
        }
        if (pwd.length() < 6) {
            tilPassword.setError("Mínimo 6 caracteres"); ok = false;
        }
        if (!ok) return;

        String hash = PasswordUtils.sha256(pwd);
        AppDatabase db = AppDatabase.getInstance(requireContext());
        SessionManager sm = new SessionManager(requireContext());

        executor.execute(() -> {
            UserDao dao = db.userDao();
            User u = dao.login(email, hash);
            requireActivity().runOnUiThread(() -> {
                if (u == null) {
                    tilPassword.setError("Credenciales incorrectas");
                    Toast.makeText(requireContext(), "Correo o contraseña inválidos", Toast.LENGTH_SHORT).show();
                } else {
                    sm.login(email);
                    Toast.makeText(requireContext(), "Bienvenido", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(root).navigate(R.id.action_login_to_recoleccion);
                }
            });
        });
    }

    private void clearErrors() {
        tilEmail.setError(null);
        tilPassword.setError(null);
    }

    private String safeText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
