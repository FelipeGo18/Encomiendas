package com.hfad.encomiendas;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.hfad.encomiendas.core.NotificationHelper;
import com.hfad.encomiendas.core.SessionManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private NavController navController;

    private ActivityResultLauncher<String> notifPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        com.hfad.encomiendas.core.NotificationHelper.ensureChannels(this);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        // Canal de notificaciones
        NotificationHelper.ensureChannels(this);

        // Permiso de notificaciones (13+)
        notifPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { /* opcional: feedback */ }
        );
        requestPostNotificationsIfNeeded();

        // NavHost y controller
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHost == null) throw new IllegalStateException("Falta @id/nav_host_fragment");
        navController = navHost.getNavController();

        // ⚠ SOLO al primer create: configura el grafo y rutea por rol.
        if (savedInstanceState == null) {
            NavInflater inflater = navController.getNavInflater();
            NavGraph graph = inflater.inflate(R.navigation.nav_graph);
            graph.setStartDestination(R.id.loginFragment);
            navController.setGraph(graph);

            SessionManager sm = new SessionManager(this);
            if (sm.isLoggedIn()) {
                navigateByRole(sm.getRole());
            }
        }

        // Datos demo si quieres, no afecta el nav state
        seedDemoData();
    }

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
        new SessionManager(this).logout();
        try {
            NavOptions opts = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                    .build();
            navController.navigate(R.id.loginFragment, null, opts);
        } catch (Exception e) {
            Log.e(TAG, "Reset nav tras logout", e);
            navController.setGraph(R.navigation.nav_graph);
        }
    }

    private void navigateByRole(String roleRaw) {
        String role = (roleRaw == null ? "" : roleRaw.trim().toUpperCase());
        int destId;
        switch (role) {
            case "OPERADOR":
            case "OPERADOR_HUB":
                destId = R.id.hubDashboardFragment; break;
            case "REPARTIDOR":
                destId = R.id.repartidorDashboardFragment; break;
            case "ASIGNADOR":
                destId = R.id.asignadorFragment; break;
            case "RECOLECTOR":
                destId = R.id.misAsignacionesFragment; break;
            default:
                destId = R.id.homeDashboardFragment; break;
        }
        try {
            NavOptions opts = new NavOptions.Builder()
                    .setPopUpTo(R.id.loginFragment, true)
                    .build();
            navController.navigate(destId, null, opts);
        } catch (Exception e) {
            Log.e(TAG, "navigateByRole error (" + role + ")", e);
        }
    }

    private void seedDemoData() {
        // Ejecutar seeder en background thread después de que la BD esté lista
        new Thread(() -> {
            try {
                // Pequeña pausa para asegurar que la BD esté completamente inicializada
                Thread.sleep(1000);
                com.hfad.encomiendas.core.DemoSeeder.seedOnce(getApplicationContext());
                Log.d(TAG, "Seeder ejecutado correctamente");
            } catch (Exception e) {
                Log.e(TAG, "Error ejecutando seeder", e);
            }
        }).start();
    }

    private void requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController controller = (navHostFragment != null) ? navHostFragment.getNavController() : null;
        return (controller != null && controller.navigateUp()) || super.onSupportNavigateUp();
    }
}