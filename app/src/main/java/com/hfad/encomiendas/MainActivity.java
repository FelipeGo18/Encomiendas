package com.hfad.encomiendas;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.hfad.encomiendas.core.PasswordUtils;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Recolector;
import com.hfad.encomiendas.data.RecolectorDao;
import com.hfad.encomiendas.data.User;
import com.hfad.encomiendas.data.UserDao;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private NavController navController;
    private boolean routedAtStart = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // contiene topAppBar + nav_host_fragment

        // Toolbar como ActionBar
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        // Navigation Host
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHost == null) {
            throw new IllegalStateException("El layout activity_main debe incluir un FragmentContainerView con id @id/nav_host_fragment");
        }
        navController = navHost.getNavController();

        // Seed de datos demo (usuarios con rol y recolectores + solicitudes/asignaciones)
        seedDemoData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Si ya hay sesión, enrutar por rol y limpiar login del back stack
        if (!routedAtStart) {
            SessionManager sm = new SessionManager(this);
            if (sm.isLoggedIn()) {
                navigateByRole(sm.getRole());
                routedAtStart = true;
            }
        }
    }

    // ---------- AppBar Menu ----------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            doLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doLogout() {
        // 1) limpiar sesión
        new SessionManager(this).logout();
        routedAtStart = false;

        // 2) regresar a Login y limpiar la pila
        try {
            NavOptions opts = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                    .build();
            navController.navigate(R.id.loginFragment, null, opts);
        } catch (Exception e) {
            Log.e(TAG, "Fallo navegando a login tras logout. Reset del grafo.", e);
            navController.setGraph(R.navigation.nav_graph);
        }
    }
    // ----------------------------------

    private void navigateByRole(String roleRaw) {
        String role = (roleRaw == null || roleRaw.isEmpty()) ? "REMITENTE" : roleRaw.toUpperCase();
        int destId;
        switch (role) {
            case "ASIGNADOR":
                destId = R.id.asignadorFragment; break;
            case "RECOLECTOR":
                destId = R.id.misAsignacionesFragment; break;
            default: // REMITENTE
                destId = R.id.solicitarRecoleccionFragment; break;
        }
        try {
            NavOptions opts = new NavOptions.Builder()
                    .setPopUpTo(R.id.loginFragment, true)
                    .build();
            navController.navigate(destId, null, opts);
        } catch (Exception e) {
            Log.e(TAG, "Error navegando por rol: " + role, e);
        }
    }

    private void seedDemoData() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());

                // Recolectores base
                RecolectorDao rdao = db.recolectorDao();
                if (rdao.listAll().isEmpty()) {
                    Recolector r1 = new Recolector();
                    r1.nombre = "Juan R.";
                    r1.municipio = "Bogotá";
                    r1.zona = "Chicó";
                    r1.vehiculo = "MOTO";
                    r1.capacidad = 10; r1.cargaActual = 0; r1.activo = true;
                    r1.userEmail = "recolector@gmail.com";
                    r1.createdAt = System.currentTimeMillis();
                    db.recolectorDao().insert(r1);

                    Recolector r2 = new Recolector();
                    r2.nombre = "Ana P.";
                    r2.municipio = "Bogotá";
                    r2.zona = "Chapinero";
                    r2.vehiculo = "BICI";
                    r2.capacidad = 8; r2.cargaActual = 0; r2.activo = true;
                    r2.userEmail = "recolector2@gmail.com";
                    r2.createdAt = System.currentTimeMillis();
                    db.recolectorDao().insert(r2);


                    Log.d(TAG, "Seed recolectores OK");
                }

                // Usuarios con rol
                UserDao udao = db.userDao();

                if (udao.findByEmail("asignador@gmail.com") == null) {
                    User admin = new User();
                    admin.email = "asignador@gmail.com";
                    admin.passwordHash = PasswordUtils.sha256("123456"); // DEMO
                    admin.rol = "ASIGNADOR";
                    admin.createdAt = System.currentTimeMillis();
                    udao.insert(admin);
                    Log.d(TAG, "Seed usuario ASIGNADOR OK");
                }

                if (udao.findByEmail("recolector@gmail.com") == null) {
                    User recUser = new User();
                    recUser.email = "recolector@gmail.com";
                    recUser.passwordHash = PasswordUtils.sha256("123456"); // DEMO
                    recUser.rol = "RECOLECTOR";
                    recUser.createdAt = System.currentTimeMillis();
                    udao.insert(recUser);
                    Log.d(TAG, "Seed usuario RECOLECTOR OK");
                }else if (udao.findByEmail("recolector2@gmail.com") == null) {
                    User recUser = new User();
                    recUser.email = "recolector2@gmail.com";
                    recUser.passwordHash = PasswordUtils.sha256("123456"); // DEMO
                    recUser.rol = "RECOLECTOR";
                    recUser.createdAt = System.currentTimeMillis();
                    udao.insert(recUser);
                    Log.d(TAG, "Seed usuario RECOLECTOR OK");
                }

                // Solicitudes de demo + asignaciones automáticas para HOY
                com.hfad.encomiendas.core.DemoSeeder.seed(getApplicationContext());

            } catch (Exception e) {
                Log.e(TAG, "Error en seed de demo", e);
            }
        }).start();
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController controller = (navHostFragment != null) ? navHostFragment.getNavController() : null;
        return (controller != null && controller.navigateUp()) || super.onSupportNavigateUp();
    }
}
