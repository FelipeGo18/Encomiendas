package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.PasswordUtils;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.User;

import java.util.concurrent.Executors;

public class LoginFragment extends Fragment {

    private TextInputLayout tilEmail, tilPass;
    private TextInputEditText etEmail, etPass;
    private MaterialButton btnLogin, btnIrARegistro;

    public LoginFragment() {}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        tilEmail     = v.findViewById(R.id.tilEmail);
        tilPass      = v.findViewById(R.id.tilPassword);
        etEmail      = v.findViewById(R.id.etEmail);
        etPass       = v.findViewById(R.id.etPassword);
        btnLogin     = v.findViewById(R.id.btnLogin);
        // <<< OJO: el id real del XML es btnIrARegistro
        btnIrARegistro = v.findViewById(R.id.btnIrARegistro);

        if (btnLogin != null) {
            btnLogin.setOnClickListener(view -> doLogin());
        }
        if (btnIrARegistro != null) {
            btnIrARegistro.setOnClickListener(view ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_login_to_registro));
        }
    }

    private void doLogin() {
        String email = etEmail != null && etEmail.getText()!=null ? etEmail.getText().toString().trim() : "";
        String pass  = etPass  != null && etPass.getText()!=null  ? etPass.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) { if (tilEmail!=null) tilEmail.setError("Requerido"); return; }
        if (TextUtils.isEmpty(pass))  { if (tilPass !=null)  tilPass.setError("Requerido"); return; }
        if (tilEmail!=null) tilEmail.setError(null);
        if (tilPass !=null)  tilPass.setError(null);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                User u = db.userDao().findByEmail(email);
                String hash = PasswordUtils.sha256(pass);

                if (u == null || u.passwordHash == null || !u.passwordHash.equals(hash)) {
                    runOnUi(() -> { if (tilPass!=null) tilPass.setError("Credenciales inválidas"); });
                    return;
                }

                // Guardar sesión con ROL
                String role = (u.rol == null ? "" : u.rol.trim());
                new SessionManager(requireContext()).login(email, role);

                // Navegar por rol
                int dest = R.id.homeDashboardFragment; // default REMITENTE
                switch (role.toUpperCase()) {
                    case "ADMIN":
                        dest = R.id.adminFragment; break;
                    case "OPERADOR":
                    case "OPERADOR_HUB":
                        dest = R.id.hubDashboardFragment; break;
                    case "REPARTIDOR":
                        dest = R.id.repartidorDashboardFragment; break;
                    case "ASIGNADOR":
                        dest = R.id.asignadorFragment; break;
                    case "RECOLECTOR":
                        dest = R.id.misAsignacionesFragment; break;
                }
                int finalDest = dest;
                runOnUi(() -> {
                    NavOptions opts = new NavOptions.Builder()
                            .setPopUpTo(R.id.loginFragment, true)
                            .build();
                    NavHostFragment.findNavController(this).navigate(finalDest, null, opts);
                });

            } catch (Exception e) {
                runOnUi(() -> { if (tilPass!=null) tilPass.setError("Error: " + e.getMessage()); });
            }
        });
    }

    private void runOnUi(Runnable r) { if (!isAdded()) return; requireActivity().runOnUiThread(r); }
}