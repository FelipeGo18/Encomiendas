package com.hfad.encomiendas;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hfad.encomiendas.api.ApiTester;
import com.hfad.encomiendas.core.NotificationHelper;
import com.hfad.encomiendas.core.SessionManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private NavController navController;
    private MaterialToolbar toolbar;
    private SessionManager sessionManager;
    private TextView notificationBadge;
    private int notificationCount = 0;

    // Destinos de nivel superior (sin botón back)
    private AppBarConfiguration appBarConfiguration;

    private ActivityResultLauncher<String> notifPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar SessionManager
        sessionManager = new SessionManager(this);

        // 🔥 PRUEBA DE CONEXIÓN CON LA API
        // ✅ DESCOMENTAR para probar la conexión con GlassFish
        ApiTester.testConnection(this);

        // Configurar toolbar
        toolbar = findViewById(R.id.topAppBar);
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

        // Configurar AppBarConfiguration con destinos de nivel superior
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.loginFragment,
                R.id.homeDashboardFragment,
                R.id.asignadorFragment,
                R.id.misAsignacionesFragment,
                R.id.hubDashboardFragment,
                R.id.repartidorDashboardFragment,
                R.id.adminFragment,
                R.id.estadisticasFragment
        ).build();

        // Conectar toolbar con Navigation
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);

        // Listener para cambios de destino
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            updateToolbarForDestination(destination.getId(), destination.getLabel());
        });

        // ⚠ SOLO al primer create: configura el grafo y rutea por rol.
        if (savedInstanceState == null) {
            NavInflater inflater = navController.getNavInflater();
            NavGraph graph = inflater.inflate(R.navigation.nav_graph);
            graph.setStartDestination(R.id.loginFragment);
            navController.setGraph(graph);

            if (sessionManager.isLoggedIn()) {
                navigateByRole(sessionManager.getRole());
            }
        }

        // Datos demo si quieres, no afecta el nav state
        seedDemoData();


        // Simular notificaciones (esto lo conectarías con tu lógica real)
        simulateNotifications();
    }

    /**
     * Actualizar toolbar según el destino actual
     */
    private void updateToolbarForDestination(int destinationId, CharSequence label) {
        // Ocultar toolbar en login y registro
        if (destinationId == R.id.loginFragment || destinationId == R.id.registroRemitenteFragment) {
            toolbar.setVisibility(View.GONE);
        } else {
            toolbar.setVisibility(View.VISIBLE);

            // Actualizar título si está disponible
            if (label != null) {
                toolbar.setTitle(label);
            }

            // Invalidar menú para recargar según contexto
            invalidateOptionsMenu();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Limpiar menú anterior
        menu.clear();

        // Obtener destino actual
        int currentDestination = navController.getCurrentDestination() != null
            ? navController.getCurrentDestination().getId()
            : R.id.loginFragment;

        // No mostrar menú en login/registro
        if (currentDestination == R.id.loginFragment ||
            currentDestination == R.id.registroRemitenteFragment) {
            return true;
        }

        // Cargar menú según rol del usuario
        String role = sessionManager.getRole();
        int menuResource = getMenuForRole(role);
        getMenuInflater().inflate(menuResource, menu);

        return true;
    }

    /**
     * Obtener el menú apropiado según el rol
     */
    private int getMenuForRole(String role) {
        if (role == null) return R.menu.main_menu;

        switch (role.toUpperCase()) {
            case "ADMIN":
                return R.menu.menu_admin;
            case "REMITENTE":
                return R.menu.menu_remitente;
            case "RECOLECTOR":
                return R.menu.menu_recolector;
            case "ASIGNADOR":
                return R.menu.menu_asignador;
            case "OPERADOR":
            case "OPERADOR_HUB":
            case "REPARTIDOR":
            default:
                return R.menu.main_menu;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        // Acciones comunes
        if (itemId == R.id.action_logout) {
            showLogoutConfirmation();
            return true;
        } else if (itemId == R.id.action_profile) {
            showProfile();
            return true;
        } else if (itemId == R.id.action_help) {
            showHelp();
            return true;
        }

        // Acciones específicas de ADMIN
        if (itemId == R.id.action_statistics) {
            navController.navigate(R.id.estadisticasFragment);
            return true;
        } else if (itemId == R.id.action_manage_users) {
            Toast.makeText(this, "Gestión de usuarios - Por implementar", Toast.LENGTH_SHORT).show();
            return true;
        }

        // Acciones específicas de REMITENTE
        if (itemId == R.id.action_my_requests) {
            navController.navigate(R.id.homeDashboardFragment);
            return true;
        } else if (itemId == R.id.action_new_pickup) {
            navController.navigate(R.id.solicitarRecoleccionFragment);
            return true;
        } else if (itemId == R.id.action_history) {
            Toast.makeText(this, "Historial - Por implementar", Toast.LENGTH_SHORT).show();
            return true;
        }

        // Acciones específicas de RECOLECTOR
        if (itemId == R.id.action_my_assignments) {
            navController.navigate(R.id.misAsignacionesFragment);
            return true;
        } else if (itemId == R.id.action_completed) {
            Toast.makeText(this, "Completadas - Por implementar", Toast.LENGTH_SHORT).show();
            return true;
        }

        // Acciones específicas de ASIGNADOR
        if (itemId == R.id.action_manage_zones) {
            navController.navigate(R.id.gestionZonasFragment);
            return true;
        } else if (itemId == R.id.action_view_assignments) {
            navController.navigate(R.id.asignadorFragment);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Mostrar diálogo de confirmación de logout
     */
    private void showLogoutConfirmation() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Estás seguro que deseas cerrar sesión?")
            .setPositiveButton("Cerrar sesión", (dialog, which) -> doLogout())
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /**
     * Mostrar perfil de usuario
     */
    private void showProfile() {
        String userEmail = sessionManager.getEmail();
        String userRole = sessionManager.getRole();

        // Extraer nombre del email (antes del @)
        String userName = userEmail.contains("@")
            ? userEmail.substring(0, userEmail.indexOf("@"))
            : userEmail;

        new MaterialAlertDialogBuilder(this)
            .setTitle("Mi Perfil")
            .setMessage("Usuario: " + userName + "\n" +
                       "Email: " + userEmail + "\n" +
                       "Rol: " + userRole)
            .setPositiveButton("Cerrar", null)
            .show();
    }

    /**
     * Mostrar ayuda
     */
    private void showHelp() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Ayuda")
            .setMessage("Sistema de Gestión de Encomiendas\n\n" +
                       "Para soporte contacta a:\n" +
                       "soporte@encomiendas.com\n\n" +
                       "Versión: 1.0")
            .setPositiveButton("Entendido", null)
            .show();
    }

    /**
     * Mostrar configuración
     */
    private void showSettings() {
        Toast.makeText(this, "Configuración - Por implementar", Toast.LENGTH_SHORT).show();
    }

    /**
     * Acción al hacer click en notificaciones
     */
    private void onNotificationClick() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Notificaciones (" + notificationCount + ")")
            .setMessage("• Nueva asignación disponible\n• Recolección completada\n• Actualización del sistema")
            .setPositiveButton("Cerrar", (dialog, which) -> {
                // Marcar como leídas
                updateNotificationBadge(0);
            })
            .show();
    }

    /**
     * Actualizar badge de notificaciones
     */
    private void updateNotificationBadge(int count) {
        notificationCount = count;
        if (notificationBadge != null) {
            if (count > 0) {
                notificationBadge.setText(String.valueOf(count));
                notificationBadge.setVisibility(View.VISIBLE);
            } else {
                notificationBadge.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Simular notificaciones (conectar con lógica real)
     */
    private void simulateNotifications() {
        // Ejemplo: simular 3 notificaciones después de 2 segundos
        toolbar.postDelayed(() -> {
            if (sessionManager.isLoggedIn()) {
                updateNotificationBadge(3);
            }
        }, 2000);
    }

    private void doLogout() {
        sessionManager.logout();
        try {
            NavOptions opts = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                    .build();
            navController.navigate(R.id.loginFragment, null, opts);
            Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Reset nav tras logout", e);
            navController.setGraph(R.navigation.nav_graph);
        }
    }

    private void navigateByRole(String roleRaw) {
        String role = (roleRaw == null ? "" : roleRaw.trim().toUpperCase());
        int destId;
        switch (role) {
            case "ADMIN":
                destId = R.id.adminFragment; break;
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
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}