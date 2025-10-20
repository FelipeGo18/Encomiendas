# Propuesta de Navegación - Aplicación de Encomiendas

## 📱 Información del Proyecto
**Nombre:** Sistema de Gestión de Encomiendas  
**Tipo de Navegación:** Navigation Component (Jetpack Navigation)  
**Fecha:** Octubre 2025

---

## 🎯 Tipo de Navegación Utilizada

### Navigation Component de Android Jetpack

Este proyecto utiliza **Android Navigation Component**, que es la solución moderna y recomendada por Google para manejar la navegación en aplicaciones Android. 

#### ¿Por qué Navigation Component?
- ✅ Gestión automática del back stack
- ✅ Transiciones y animaciones integradas
- ✅ Paso de argumentos entre destinos de forma segura
- ✅ Deep linking support
- ✅ Visualización gráfica del flujo de navegación
- ✅ Menor código repetitivo (boilerplate)

---

## 🗺️ Estructura de Navegación

### Componentes Principales

#### 1. **NavHostFragment** 
- **Ubicación:** `activity_main.xml`
- **ID:** `nav_host_fragment`
- **Función:** Contenedor principal que aloja todos los fragmentos y gestiona las transiciones

```xml
<androidx.fragment.app.FragmentContainerView
    android:id="@+id/nav_host_fragment"
    android:name="androidx.navigation.fragment.NavHostFragment"
    app:navGraph="@navigation/nav_graph" />
```

#### 2. **Navigation Graph**
- **Archivo:** `res/navigation/nav_graph.xml`
- **Fragmentos:** 16 destinos diferentes
- **Destino inicial:** `loginFragment`

#### 3. **NavController**
- **Ubicación:** `MainActivity.java`
- **Función:** Controlador que ejecuta las navegaciones entre fragmentos

```java
NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
    .findFragmentById(R.id.nav_host_fragment);
navController = navHost.getNavController();
```

---

## 🔄 Flujo de Navegación por Roles

La aplicación implementa un sistema de navegación **basado en roles de usuario**. Después del login, el usuario es dirigido a diferentes pantallas según su rol:

### Mapa de Navegación por Rol

```
┌─────────────────────────────────────────────────────────────┐
│                       LOGIN FRAGMENT                         │
│                    (Pantalla inicial)                        │
└───────────────────────┬─────────────────────────────────────┘
                        │
         ┌──────────────┼──────────────┐
         │              │              │
         ▼              ▼              ▼
    ┌────────┐    ┌──────────┐   ┌──────────┐
    │REMITENTE│    │ASIGNADOR │   │RECOLECTOR│
    └────┬───┘    └─────┬────┘   └────┬─────┘
         │              │              │
         ▼              ▼              ▼
   ┌──────────┐   ┌──────────┐  ┌──────────────┐
   │   HOME   │   │ASIGNADOR │  │     MIS      │
   │DASHBOARD │   │ FRAGMENT │  │ASIGNACIONES  │
   └──────────┘   └──────────┘  └──────────────┘
```

### Roles Implementados

| Rol | Pantalla Destino | Funcionalidad Principal |
|-----|------------------|-------------------------|
| **REMITENTE** (default) | `homeDashboardFragment` | Solicitar recolecciones, ver estado de solicitudes |
| **ASIGNADOR** | `asignadorFragment` | Gestionar zonas, asignar recolectores |
| **RECOLECTOR** | `misAsignacionesFragment` | Ver y completar asignaciones de recolección |
| **OPERADOR_HUB** | `hubDashboardFragment` | Gestión del hub de distribución |
| **REPARTIDOR** | `repartidorDashboardFragment` | Entregas y calificaciones |

---

## 📊 Diagrama de Navegación Detallado

### 1. Flujo de Autenticación
```
loginFragment
    ├─→ registroRemitenteFragment (acción: action_login_to_registro)
    │       └─→ loginFragment (acción: action_registro_to_login)
    │
    └─→ [Destinos según rol con popUpTo inclusive]
```

### 2. Flujo de REMITENTE
```
homeDashboardFragment
    ├─→ solicitarRecoleccionFragment (action_home_to_solicitar)
    └─→ solicitudMapaFragment (action_home_to_solicitudMapa)
```

### 3. Flujo de ASIGNADOR
```
asignadorFragment
    ├─→ zonaDetalleFragment (action_asignador_to_zonaDetalle)
    │       └─→ [Detalle de zona con argumentos: fecha, zona, zoneId]
    │
    └─→ gestionZonasFragment (action_asignador_to_gestionZonas)
            └─→ zoneMapEditorFragment (action_gestionZonas_to_zoneMapEditor)
                    └─→ [Editor de polígono con argumento: zoneId]
```

### 4. Flujo de RECOLECTOR
```
misAsignacionesFragment
    └─→ detalleRecoleccionFragment (action_misAsignaciones_to_detalle)
            ├─→ recoleccionMapaFragment (action_detalle_to_recoleccionMapa)
            └─→ [Argumentos: asignacionId]
```

### 5. Flujo de REPARTIDOR
```
repartidorDashboardFragment
    └─→ misCalificacionesFragment (action_repartidor_to_calificaciones)
```

---

## 🎬 Animaciones de Transición

El proyecto incluye animaciones personalizadas para las transiciones:

### Archivos de Animación
- **`slide_in_right.xml`** - Entrada desde la derecha
- **`slide_in_left.xml`** - Entrada desde la izquierda
- **`slide_out_right.xml`** - Salida hacia la derecha
- **`slide_out_left.xml`** - Salida hacia la izquierda

### Características
- **Duración:** 250ms
- **Interpolador:** `fast_out_slow_in` (Material Design)
- **Efectos:** Translación + Alpha (desvanecimiento)

```xml
<translate
    android:fromXDelta="100%"
    android:toXDelta="0%"
    android:duration="250" />
<alpha android:fromAlpha="0" android:toAlpha="1" android:duration="250"/>
```

---

## 🔧 Implementación Técnica

### Dependencias Utilizadas
```kotlin
// Navigation Component
implementation("androidx.navigation:navigation-fragment:2.7.7")
implementation("androidx.navigation:navigation-ui:2.7.7")
implementation("androidx.navigation:navigation-runtime:2.7.7")
```

### Paso de Argumentos

El sistema utiliza argumentos tipados definidos en el nav_graph:

#### Ejemplo 1: Detalle de Recolección
```xml
<fragment android:id="@+id/detalleRecoleccionFragment">
    <argument 
        android:name="asignacionId" 
        app:argType="integer" />
</fragment>
```

#### Ejemplo 2: Detalle de Zona
```xml
<fragment android:id="@+id/zonaDetalleFragment">
    <argument android:name="fecha" app:argType="string" />
    <argument android:name="zona" app:argType="string" />
    <argument android:name="zoneId" app:argType="integer" android:defaultValue="-1" />
</fragment>
```

### Navegación Programática

#### En MainActivity (navegación por rol):
```java
private void navigateByRole(String role) {
    int destId;
    switch (role.toUpperCase()) {
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
    
    NavOptions opts = new NavOptions.Builder()
        .setPopUpTo(R.id.loginFragment, true)
        .build();
    navController.navigate(destId, null, opts);
}
```

#### En Fragmentos:
```java
// Navegación simple
NavHostFragment.findNavController(this)
    .navigate(R.id.action_home_to_solicitar);

// Navegación con argumentos (usando Bundle)
Bundle args = new Bundle();
args.putInt("asignacionId", asignacionId);
NavHostFragment.findNavController(this)
    .navigate(R.id.action_misAsignaciones_to_detalle, args);
```

---

## 🛡️ Gestión del Back Stack

### popUpTo y popUpToInclusive

El proyecto utiliza estrategias de back stack management:

#### Login → Dashboard (No volver al login con back)
```xml
<action
    android:id="@+id/action_login_to_home"
    app:destination="@id/homeDashboardFragment"
    app:popUpTo="@id/loginFragment"
    app:popUpToInclusive="true" />
```

Esto significa:
- ✅ Al presionar back desde el dashboard, la app se cierra
- ✅ No se regresa al login
- ✅ Se limpia el stack hasta el login (inclusive)

### Logout
```java
private void doLogout() {
    new SessionManager(this).logout();
    NavOptions opts = new NavOptions.Builder()
        .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
        .build();
    navController.navigate(R.id.loginFragment, null, opts);
}
```

---

## 📱 Características Especiales

### 1. **Deep Linking**
El NavHostFragment está configurado con:
```xml
app:defaultNavHost="true"
```
Esto permite que el fragmento maneje automáticamente el botón back del sistema.

### 2. **Menú de Opciones**
- **Archivo:** `res/menu/main_menu.xml`
- **Acción:** Logout desde cualquier pantalla
- **Implementación:** En MainActivity

### 3. **Toolbar Integrado**
```xml
<com.google.android.material.appbar.MaterialToolbar
    android:id="@+id/topAppBar"
    app:title="@string/app_name"
    app:titleCentered="true" />
```

---

## 🎨 Patrón de Diseño

### Single Activity Architecture

Este proyecto sigue el patrón **Single Activity** recomendado por Google:
- ✅ Una sola Activity (MainActivity)
- ✅ Múltiples Fragments para diferentes pantallas
- ✅ NavController gestiona todas las transiciones
- ✅ Estado compartido mediante ViewModel (si se implementa)

### Ventajas
1. **Simplicidad:** Un solo ciclo de vida de Activity
2. **Transiciones suaves:** Animaciones entre fragments
3. **Menor overhead:** No crear/destruir Activities constantemente
4. **Mejor experiencia:** Transiciones más fluidas

---

## 📈 Escalabilidad

El sistema de navegación está diseñado para:
- ✅ Agregar nuevos roles fácilmente
- ✅ Crear nuevos flujos de navegación
- ✅ Mantener el código organizado
- ✅ Facilitar el testing

### Agregar un nuevo destino:
1. Crear el Fragment en `ui/`
2. Agregar al `nav_graph.xml`
3. Definir acciones desde otros fragmentos
4. Opcional: Agregar al switch de roles

---

## 🔍 Resumen de Destinos

| # | Fragment | Función | Argumentos |
|---|----------|---------|------------|
| 1 | loginFragment | Autenticación | - |
| 2 | registroRemitenteFragment | Registro de usuarios | - |
| 3 | homeDashboardFragment | Dashboard remitente | - |
| 4 | solicitarRecoleccionFragment | Solicitar recolección | - |
| 5 | solicitudMapaFragment | Ver solicitud en mapa | - |
| 6 | asignadorFragment | Panel asignador | - |
| 7 | gestionZonasFragment | Gestión de zonas | - |
| 8 | zoneMapEditorFragment | Editor de zonas | zoneId |
| 9 | zonaDetalleFragment | Detalle zona | fecha, zona, zoneId |
| 10 | zonaMapaFullFragment | Mapa fullscreen | fecha, zona, zoneId |
| 11 | misAsignacionesFragment | Lista asignaciones | - |
| 12 | detalleRecoleccionFragment | Detalle recolección | asignacionId |
| 13 | recoleccionMapaFragment | Mapa recolección | - |
| 14 | hubDashboardFragment | Dashboard hub | - |
| 15 | repartidorDashboardFragment | Dashboard repartidor | - |
| 16 | misCalificacionesFragment | Ver calificaciones | - |
| 17 | entregaFragment | Gestión entrega | manifiestoItemId |
| 18 | poligonoRutaPreviewFragment | Preview ruta | zoneId, fecha |

**Total: 18 destinos de navegación**

---

## 💡 Conclusiones

La aplicación de Encomiendas utiliza un sistema de navegación **moderno, escalable y mantenible** basado en Navigation Component de Android Jetpack. 

### Fortalezas:
✅ Arquitectura clara y organizada  
✅ Separación de roles bien definida  
✅ Animaciones fluidas  
✅ Back stack management correcto  
✅ Paso de argumentos tipado y seguro  
✅ Fácil de extender y mantener  

### Tecnologías:
- **Navigation Component 2.7.7**
- **Single Activity Architecture**
- **Fragment-based navigation**
- **Material Design Components**

---

**Autor:** [Tu Nombre]  
**Curso:** Desarrollo de Aplicaciones Móviles  
**Laboratorio:** List Views and Adapters - Navegación Android  
**Fecha:** Octubre 2025

