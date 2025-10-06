package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.hfad.encomiendas.BuildConfig;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Asignacion;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.TrackingEventDao;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Mapa a pantalla completa para una asignación de recolección.
 * Muestra: marcador del recolector (última ubicación registrada) + marcador del destino (dirección a recoger) + ruta.
 */
public class RecoleccionMapaFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "RecoleccionMapaFrag";
    private static final boolean DEBUG = true;
    private static final double BOGOTA_LAT = 4.7110;
    private static final double BOGOTA_LON = -74.0721;
    private static final long REFRESH_MS = 30_000L;

    private int asignacionId = -1;
    private long solicitudId = -1;
    private Double destinoLat, destinoLon; // destino

    private MapView mapView;
    private GoogleMap gMap;
    private Marker markerRecolector, markerDestino;
    private Polyline routePolyline;
    private Double lastRecolectorLat, lastRecolectorLon;

    private View btnCenter;
    private ImageButton btnBack;
    private TextView tvStatus;

    private final Runnable refresco = new Runnable() {
        @Override public void run() {
            cargarLastLocation();
            if (mapView != null) mapView.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recoleccion_mapa, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        asignacionId = getArguments() != null ? getArguments().getInt("asignacionId", -1) : -1;

        if (DEBUG) Log.d(TAG, "onViewCreated - asignacionId: " + asignacionId);

        mapView = v.findViewById(R.id.mapFullReco);
        btnCenter = v.findViewById(R.id.fabCenterReco);
        btnBack = v.findViewById(R.id.btnBackReco);
        tvStatus = v.findViewById(R.id.tvStatusReco);

        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        }
        if (btnBack != null) btnBack.setOnClickListener(v1 -> requireActivity().onBackPressed());
        if (btnCenter != null) btnCenter.setOnClickListener(v12 -> centerCamera(true));

        cargarDatosIniciales();
    }

    private void cargarDatosIniciales() {
        if (asignacionId <= 0) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                Asignacion a = db.asignacionDao().getById(asignacionId);
                if (a != null) {
                    solicitudId = a.solicitudId;
                }

                // Obtener coordenadas del destino
                if (solicitudId > 0) {
                    Solicitud s = db.solicitudDao().byId(solicitudId);
                    if (s != null) {
                        destinoLat = s.lat;
                        destinoLon = s.lon;
                        if (DEBUG) Log.d(TAG, "Coordenadas destino desde solicitud: " + destinoLat + "," + destinoLon);
                    }
                }

                // Fallback: coordenadas de prueba si no hay reales
                if (destinoLat == null || destinoLon == null) {
                    destinoLat = 4.6097 + (Math.random() - 0.5) * 0.1;
                    destinoLon = -74.0817 + (Math.random() - 0.5) * 0.1;
                    if (DEBUG) Log.d(TAG, "Usando coordenadas de prueba para destino: " + destinoLat + "," + destinoLon);
                }

                if (getActivity()!=null) getActivity().runOnUiThread(() -> {
                    if (gMap != null) {
                        pintarMarcadores(false);
                        cargarLastLocation(); // Cargar ubicación del recolector
                    }
                });
            } catch (Exception e) {
                if (DEBUG) Log.e(TAG, "Error cargando datos iniciales", e);
            }
        });
    }

    private void cargarLastLocation() {
        if (DEBUG) Log.d(TAG, "cargarLastLocation - solicitudId: " + solicitudId);

        // PRIMERA PRIORIDAD: Intentar obtener ubicación actual del dispositivo
        intentarUbicacionDispositivo();

        // SEGUNDA PRIORIDAD: Buscar en tracking events si existen
        if (solicitudId > 0) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    TrackingEventDao.LastLoc loc = AppDatabase.getInstance(requireContext())
                            .trackingEventDao().lastLocationForShipment(solicitudId);

                    if (loc != null && loc.lat != null && loc.lon != null) {
                        if (DEBUG) Log.d(TAG, "Encontrada ubicación en tracking: " + loc.lat + "," + loc.lon);
                        // Solo usar tracking si no tenemos ubicación del dispositivo
                        if (lastRecolectorLat == null || lastRecolectorLon == null) {
                            lastRecolectorLat = loc.lat;
                            lastRecolectorLon = loc.lon;
                            if (getActivity()!=null) getActivity().runOnUiThread(() -> {
                                pintarMarcadores(false);
                                dibujarRutaReal(); // Cambiar a ruta real
                                actualizarStatus();
                            });
                        }
                    } else {
                        if (DEBUG) Log.w(TAG, "No hay datos de tracking para solicitud " + solicitudId);
                    }
                } catch (Exception e) {
                    if (DEBUG) Log.e(TAG, "Error consultando tracking", e);
                }
            });
        }
    }

    // NUEVO: Método para obtener ubicación actual del dispositivo
    private void intentarUbicacionDispositivo() {
        if (DEBUG) Log.d(TAG, "Intentando obtener ubicación actual del dispositivo");

        // Verificar permisos
        if (getContext() != null &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {

            try {
                com.google.android.gms.location.FusedLocationProviderClient fusedLocationClient =
                    com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireContext());

                fusedLocationClient.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).addOnSuccessListener(location -> {
                    if (location != null) {
                        lastRecolectorLat = location.getLatitude();
                        lastRecolectorLon = location.getLongitude();
                        if (DEBUG) Log.d(TAG, "✓ Ubicación actual del dispositivo obtenida: " + lastRecolectorLat + "," + lastRecolectorLon);

                        pintarMarcadores(false);
                        dibujarRutaReal();
                        actualizarStatus();
                    } else {
                        if (DEBUG) Log.w(TAG, "No se pudo obtener ubicación actual");
                        usarPosicionDePrueba();
                    }
                }).addOnFailureListener(e -> {
                    if (DEBUG) Log.w(TAG, "Error obteniendo ubicación: " + e.getMessage());
                    usarPosicionDePrueba();
                });

            } catch (SecurityException e) {
                if (DEBUG) Log.e(TAG, "Sin permisos de ubicación", e);
                usarPosicionDePrueba();
            }
        } else {
            if (DEBUG) Log.w(TAG, "Sin permisos de ubicación concedidos");
            usarPosicionDePrueba();
        }
    }

    private void usarPosicionDePrueba() {
        if (destinoLat != null && destinoLon != null) {
            // Posición de prueba: cerca pero separada del destino
            lastRecolectorLat = destinoLat + 0.015; // ~1.5km al norte
            lastRecolectorLon = destinoLon + 0.015; // ~1.5km al este
            if (DEBUG) Log.d(TAG, "Usando posición de prueba para recolector: " + lastRecolectorLat + "," + lastRecolectorLon);

            pintarMarcadores(false);
            dibujarRutaReal();
            actualizarStatus();
        }
    }

    private void actualizarStatus() {
        if (tvStatus == null) return;
        String txt;
        if (lastRecolectorLat != null && destinoLat != null) {
            txt = String.format(Locale.getDefault(),
                "Recolector: %.4f,%.4f → Destino: %.4f,%.4f",
                lastRecolectorLat, lastRecolectorLon, destinoLat, destinoLon);
        } else if (lastRecolectorLat != null) {
            txt = String.format(Locale.getDefault(), "Recolector: %.4f,%.4f", lastRecolectorLat, lastRecolectorLon);
        } else {
            txt = "Cargando ubicaciones...";
        }
        tvStatus.setText(txt);
    }

    private void pintarMarcadores(boolean force) {
        if (gMap == null) return;

        if (DEBUG) Log.d(TAG, "Pintando marcadores - recolector: " + lastRecolectorLat + "," + lastRecolectorLon + " destino: " + destinoLat + "," + destinoLon);

        // Crear iconos personalizados usando IconUtils (mismos que en el mapa del asignador)
        BitmapDescriptor iconRecolector = IconUtils.bitmapFromVector(requireContext(), R.drawable.ic_marker_recolector);
        BitmapDescriptor iconDestino = IconUtils.bitmapFromVector(requireContext(), R.drawable.ic_marker_pendiente);

        // Marcador del recolector (con icono personalizado)
        if (lastRecolectorLat != null && lastRecolectorLon != null) {
            LatLng posR = new LatLng(lastRecolectorLat, lastRecolectorLon);
            if (markerRecolector == null) {
                markerRecolector = gMap.addMarker(new MarkerOptions()
                    .position(posR)
                    .title("🚛 Recolector")
                    .snippet("Tu ubicación actual")
                    .icon(iconRecolector));
            } else {
                markerRecolector.setPosition(posR);
                markerRecolector.setIcon(iconRecolector); // Actualizar icono también
            }
        }

        // Marcador del destino (con icono personalizado)
        if (destinoLat != null && destinoLon != null) {
            LatLng posD = new LatLng(destinoLat, destinoLon);
            if (markerDestino == null) {
                markerDestino = gMap.addMarker(new MarkerOptions()
                    .position(posD)
                    .title("📦 Punto de Recojo")
                    .snippet("Destino de recolección")
                    .icon(iconDestino));
            } else {
                markerDestino.setIcon(iconDestino); // Actualizar icono también
            }
        }

        if (force) centerCamera(false);
    }

    // NUEVO: Método para dibujar ruta real usando DirectionsHelper
    private void dibujarRutaReal() {
        if (gMap == null || lastRecolectorLat == null || lastRecolectorLon == null || destinoLat == null || destinoLon == null) {
            if (DEBUG) Log.w(TAG, "No se puede dibujar ruta real - faltan coordenadas");
            return;
        }

        LatLng origen = new LatLng(lastRecolectorLat, lastRecolectorLon);
        LatLng destino = new LatLng(destinoLat, destinoLon);

        if (DEBUG) Log.d(TAG, "Obteniendo ruta real desde " + origen + " hasta " + destino);

        com.hfad.encomiendas.core.DirectionsHelper.getRoute(
            origen,
            destino,
            new com.hfad.encomiendas.core.DirectionsHelper.DirectionsCallback() {
                @Override
                public void onRouteReady(com.google.android.gms.maps.model.PolylineOptions polylineOptions, String duration, String distance) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (gMap != null) {
                                // Remover ruta anterior
                                if (routePolyline != null) {
                                    routePolyline.remove();
                                }

                                // Personalizar la polyline para el recolector
                                polylineOptions.width(10f).color(0xFF1976D2);
                                routePolyline = gMap.addPolyline(polylineOptions);

                                // Actualizar el status con información real
                                actualizarStatusConRuta(duration, distance);

                                if (DEBUG) Log.d(TAG, "✓ Ruta real dibujada: " + duration + " / " + distance);
                            }
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (DEBUG) Log.w(TAG, "Error obteniendo ruta real: " + error);
                    // Fallback a línea recta punteada
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> dibujarRutaSimple());
                    }
                }
            }
        );
    }

    // NUEVO: Método mejorado para dibujar ruta simple como fallback
    private void dibujarRutaSimple() {
        if (gMap == null || lastRecolectorLat == null || lastRecolectorLon == null || destinoLat == null || destinoLon == null) {
            if (DEBUG) Log.w(TAG, "No se puede dibujar ruta simple - faltan coordenadas");
            return;
        }

        // Remover ruta anterior
        if (routePolyline != null) {
            routePolyline.remove();
        }

        // Crear línea recta punteada como fallback
        List<LatLng> puntos = new ArrayList<>();
        puntos.add(new LatLng(lastRecolectorLat, lastRecolectorLon));
        puntos.add(new LatLng(destinoLat, destinoLon));

        PolylineOptions opts = new PolylineOptions()
            .width(8f)
            .color(0xFF1976D2) // Azul
            .geodesic(true)
            .pattern(java.util.Arrays.asList(
                new com.google.android.gms.maps.model.Dash(20),
                new com.google.android.gms.maps.model.Gap(10)
            ));
        opts.addAll(puntos);

        routePolyline = gMap.addPolyline(opts);
        if (DEBUG) Log.d(TAG, "✓ Ruta simple (fallback) dibujada con " + puntos.size() + " puntos");
    }

    // NUEVO: Actualizar status con información de ruta real
    private void actualizarStatusConRuta(String duration, String distance) {
        if (tvStatus == null) return;
        String txt;
        if (lastRecolectorLat != null && destinoLat != null) {
            txt = String.format(Locale.getDefault(),
                "📍 Recolector → Destino | ⏱️ %s | 📏 %s",
                duration, distance);
        } else {
            txt = "Cargando ruta...";
        }
        tvStatus.setText(txt);
    }

    private void centerCamera(boolean animate) {
        if (gMap == null) return;
        LatLng target;
        float zoom;

        if (lastRecolectorLat != null && destinoLat != null) {
            target = new LatLng( (lastRecolectorLat+destinoLat)/2.0, (lastRecolectorLon+destinoLon)/2.0 );
            zoom = 13f;
        } else if (lastRecolectorLat != null) {
            target = new LatLng(lastRecolectorLat, lastRecolectorLon);
            zoom = 14f;
        } else if (destinoLat != null) {
            target = new LatLng(destinoLat, destinoLon);
            zoom = 14f;
        } else {
            target = new LatLng(BOGOTA_LAT, BOGOTA_LON);
            zoom = 11f;
        }

        if (animate) {
            gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoom));
        } else {
            gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, zoom));
        }
    }

    /* ===== Ruta con API (opcional) ===== */
    private boolean routeRequested = false;
    private Double lastRouteFromLat, lastRouteFromLon;

    private void intentarRutaConAPI() {
        if (gMap == null || destinoLat == null || destinoLon == null || lastRecolectorLat == null || lastRecolectorLon == null) return;
        if (lastRouteFromLat != null && lastRouteFromLon != null) {
            double d = Math.hypot(lastRouteFromLat - lastRecolectorLat, lastRouteFromLon - lastRecolectorLon);
            if (d < 0.0003 && routePolyline != null) return;
        }
        if (routeRequested) return;
        routeRequested = true;
        final double oLat = lastRecolectorLat, oLon = lastRecolectorLon, dLat = destinoLat, dLon = destinoLon;
        Executors.newSingleThreadExecutor().execute(() -> fetchRoute(oLat, oLon, dLat, dLon));
    }

    private void fetchRoute(double oLat, double oLon, double dLat, double dLon) {
        List<LatLng> decoded = null;
        String key = BuildConfig.MAPS_API_KEY;
        boolean canApi = key != null && !key.trim().isEmpty() && !key.startsWith("REEMPLAZA");

        if (canApi) {
            String urlStr = String.format(Locale.US,
                    "https://maps.googleapis.com/maps/api/directions/json?origin=%f,%f&destination=%f,%f&mode=driving&key=%s",
                    oLat,oLon,dLat,dLon,key);
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.connect();

                if (conn.getResponseCode()==200) {
                    InputStream is = conn.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while((line=br.readLine())!=null) sb.append(line);
                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray routes = root.optJSONArray("routes");
                    if (routes != null && routes.length()>0) {
                        JSONObject r0 = routes.getJSONObject(0);
                        JSONObject poly = r0.getJSONObject("overview_polyline");
                        decoded = decodePolyline(poly.getString("points"));
                        if (DEBUG) Log.d(TAG, "✓ Ruta mejorada obtenida de Google API");
                    }
                }
            } catch (Exception e) {
                if (DEBUG) Log.w(TAG, "Error obteniendo ruta de API: " + e.getMessage());
            } finally {
                if (conn!=null) conn.disconnect();
            }
        }

        // Si no se pudo obtener de la API, usar línea recta
        if (decoded == null || decoded.isEmpty()) {
            decoded = new ArrayList<>();
            decoded.add(new LatLng(oLat,oLon));
            decoded.add(new LatLng(dLat,dLon));
            if (DEBUG) Log.d(TAG, "Usando ruta fallback (línea recta)");
        }

        final List<LatLng> finalDecoded = decoded;
        if (getActivity()!=null) getActivity().runOnUiThread(() -> drawRoute(finalDecoded, oLat, oLon));
    }

    private void drawRoute(List<LatLng> pts, double fromLat, double fromLon) {
        routeRequested = false;
        lastRouteFromLat = fromLat;
        lastRouteFromLon = fromLon;

        if (gMap == null || pts == null || pts.isEmpty()) return;
        if (routePolyline != null) routePolyline.remove();

        PolylineOptions opts = new PolylineOptions()
            .width(10f)
            .color(0xFF1976D2)
            .geodesic(true);
        opts.addAll(pts);
        routePolyline = gMap.addPolyline(opts);

        if (DEBUG) Log.d(TAG, "✓ Ruta final dibujada con " + pts.size() + " puntos");
    }

    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do { b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1)); lat += dlat;
            shift = 0; result = 0;
            do { b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1)); lng += dlng;
            double latD = lat / 1E5; double lonD = lng / 1E5; poly.add(new LatLng(latD, lonD));
        }
        return poly;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.gMap = googleMap;
        if (DEBUG) Log.d(TAG, "onMapReady - mapa listo");

        // Habilitar todos los gestos
        gMap.getUiSettings().setAllGesturesEnabled(true);
        gMap.getUiSettings().setZoomControlsEnabled(true);
        gMap.getUiSettings().setMapToolbarEnabled(true);

        centerCamera(false);
        pintarMarcadores(true);
        cargarLastLocation(); // Esto va a dibujar la ruta
    }

    @Override public void onResume() { super.onResume(); if (mapView!=null) mapView.onResume(); if (mapView!=null) mapView.postDelayed(refresco, REFRESH_MS); }
    @Override public void onPause() { super.onPause(); if (mapView!=null) { mapView.onPause(); mapView.removeCallbacks(refresco);} }
    @Override public void onDestroyView() { super.onDestroyView(); if (mapView!=null) mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView!=null) mapView.onLowMemory(); }
    @Override public void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override public void onStop() { super.onStop(); if (mapView != null) mapView.onStop(); }
    @Override public void onSaveInstanceState(@NonNull Bundle outState) { super.onSaveInstanceState(outState); if (mapView != null) mapView.onSaveInstanceState(outState); }
}
