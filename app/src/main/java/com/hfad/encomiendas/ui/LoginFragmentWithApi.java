package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.api.ApiHelper;
import com.hfad.encomiendas.core.PasswordUtils;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.User;

import java.util.concurrent.Executors;

/**
 * Fragment de Login con soporte para API y fallback a Room
 *
 * CONFIGURACIÓN:
 * - USE_API = true: Usa la API REST de GlassFish (recomendado)
 * - USE_API = false: Usa solo Room (base de datos local)
 */
public class LoginFragmentWithApi extends Fragment {

    // 🔥 CONFIGURACIÓN: Cambia esto para usar API o Room
    private static final boolean USE_API = true;

    // Declarar variables de UI
    private TextInputLayout tilEmail, tilPass;
    private TextInputEditText etEmail, etPass;
    private MaterialButton btnLogin, btnIrARegistro;
    private CircularProgressIndicator progressBar;

    public LoginFragmentWithApi() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        tilEmail = v.findViewById(R.id.tilEmail);
        tilPass = v.findViewById(R.id.tilPassword);
        etEmail = v.findViewById(R.id.etEmail);
        etPass = v.findViewById(R.id.etPassword);
        btnLogin = v.findViewById(R.id.btnLogin);
        btnIrARegistro = v.findViewById(R.id.btnIrARegistro);

        // Progress bar (si existe en tu layout)
        progressBar = v.findViewById(R.id.progressBar);

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
        String email = etEmail != null && etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String pass = etPass != null && etPass.getText() != null ? etPass.getText().toString().trim() : "";

        // Validaciones
        if (TextUtils.isEmpty(email)) {
            if (tilEmail != null) tilEmail.setError("Requerido");
            return;
        }
        if (TextUtils.isEmpty(pass)) {
            if (tilPass != null) tilPass.setError("Requerido");
            return;
        }
        if (tilEmail != null) tilEmail.setError(null);
        if (tilPass != null) tilPass.setError(null);

        // Mostrar loading
        setLoading(true);

        // Calcular hash de la contraseña
        String passwordHash = PasswordUtils.sha256(pass);

        if (USE_API) {
            // 🚀 USAR API REST DE GLASSFISH
            loginWithApi(email, passwordHash);
        } else {
            // 📱 USAR ROOM (base de datos local)
            loginWithRoom(email, passwordHash);
        }
    }

    /**
     * Login usando la API REST de GlassFish
     */
    private void loginWithApi(String email, String passwordHash) {
        ApiHelper.login(requireContext(), email, passwordHash, new ApiHelper.ApiCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (!isAdded()) return;

                setLoading(false);

                // Guardar sesión
                String role = (user.rol == null ? "REMITENTE" : user.rol.trim());
                new SessionManager(requireContext()).login(email, role);

                // Opcionalmente, guardar en Room para uso offline
                saveUserToRoom(user);

                // Navegar por rol
                navigateByRole(role);

                Toast.makeText(requireContext(), "Bienvenido " + email, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                setLoading(false);

                // Si la API falla, intentar con Room como fallback
                Toast.makeText(requireContext(), "API no disponible, usando datos locales...", Toast.LENGTH_SHORT).show();
                loginWithRoom(email, passwordHash);
            }
        });
    }

    /**
     * Login usando Room (base de datos local)
     */
    private void loginWithRoom(String email, String passwordHash) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                User u = db.userDao().findByEmail(email);

                if (u == null || u.passwordHash == null || !u.passwordHash.equals(passwordHash)) {
                    runOnUi(() -> {
                        setLoading(false);
                        if (tilPass != null) tilPass.setError("Credenciales inválidas");
                    });
                    return;
                }

                // Guardar sesión con ROL
                String role = (u.rol == null ? "" : u.rol.trim());
                new SessionManager(requireContext()).login(email, role);

                // Navegar por rol
                runOnUi(() -> {
                    setLoading(false);
                    navigateByRole(role);
                    Toast.makeText(requireContext(), "Bienvenido " + email, Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                runOnUi(() -> {
                    setLoading(false);
                    if (tilPass != null) tilPass.setError("Error: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Guardar usuario en Room para uso offline
     */
    private void saveUserToRoom(User user) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                // Verificar si ya existe
                User existing = db.userDao().findByEmail(user.email);
                if (existing == null) {
                    db.userDao().insert(user);
                } else {
                    // Actualizar datos
                    user.id = existing.id; // Mantener ID local
                    db.userDao().update(user);
                }
            } catch (Exception e) {
                // Ignorar errores al guardar en caché
            }
        });
    }

    /**
     * Navegar según el rol del usuario
     */
    private void navigateByRole(String roleRaw) {
        String role = (roleRaw == null ? "" : roleRaw.trim().toUpperCase());
        int dest = R.id.homeDashboardFragment; // default REMITENTE

        switch (role) {
            case "ADMIN":
                dest = R.id.adminFragment;
                break;
            case "OPERADOR":
            case "OPERADOR_HUB":
                dest = R.id.hubDashboardFragment;
                break;
            case "REPARTIDOR":
                dest = R.id.repartidorDashboardFragment;
                break;
            case "ASIGNADOR":
                dest = R.id.asignadorFragment;
                break;
            case "RECOLECTOR":
                dest = R.id.misAsignacionesFragment;
                break;
        }

        NavOptions opts = new NavOptions.Builder()
                .setPopUpTo(R.id.loginFragment, true)
                .build();
        NavHostFragment.findNavController(this).navigate(dest, null, opts);
    }

    /**
     * Mostrar/ocultar loading
     */
    private void setLoading(boolean loading) {
        if (btnLogin != null) btnLogin.setEnabled(!loading);
        if (btnIrARegistro != null) btnIrARegistro.setEnabled(!loading);
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void runOnUi(Runnable r) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(r);
    }
}
