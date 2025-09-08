package com.hfad.encomiendas;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.NavInflater;
import androidx.navigation.NavGraph;

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
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHost == null) {
            throw new IllegalStateException("Falta @id/nav_host_fragment en activity_main");
        }
        navController = navHost.getNavController();
        NavInflater inflater = navController.getNavInflater();
        NavGraph graph = inflater.inflate(R.navigation.nav_graph);

        // SIEMPRE arrancamos en Login
        graph.setStartDestination(R.id.loginFragment);
        navController.setGraph(graph);

        // Si ya hay sesión abierta → ruteo inmediato por rol
        SessionManager sm = new SessionManager(this);
        if (sm.isLoggedIn()) {
            navigateByRole(sm.getRole());
            routedAtStart = true;
        }

        seedDemoData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Fallback por si vuelve y aún no estaba ruteado
        if (!routedAtStart) {
            SessionManager sm = new SessionManager(this);
            if (sm.isLoggedIn()) {
                navigateByRole(sm.getRole());
                routedAtStart = true;
            }
        }
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
        routedAtStart = false;
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
                // pon aquí tu fragment de repartidor cuando lo tengas
                destId = R.id.repartidorDashboardFragment; break;
            case "ASIGNADOR":
                destId = R.id.asignadorFragment; break;
            case "RECOLECTOR":
                destId = R.id.misAsignacionesFragment; break;
            default: // REMITENTE
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

    // en MainActivity.java
    private void seedDemoData() {
        com.hfad.encomiendas.core.DemoSeeder.seedOnce(getApplicationContext());
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController controller = (navHostFragment != null) ? navHostFragment.getNavController() : null;
        return (controller != null && controller.navigateUp()) || super.onSupportNavigateUp();
    }
}
