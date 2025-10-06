package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Solicitud;
import java.util.concurrent.Executors;

/**
 * Fragmento para mostrar una solicitud específica en el mapa
 */
public class SolicitudMapaFragment extends Fragment implements OnMapReadyCallback {

    private static final String ARG_SOLICITUD_ID = "solicitud_id";

    private GoogleMap mMap;
    private TextView tvInfo;
    private long solicitudId;
    private Solicitud solicitud;

    public static SolicitudMapaFragment newInstance(long solicitudId) {
        SolicitudMapaFragment fragment = new SolicitudMapaFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_SOLICITUD_ID, solicitudId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            // Intentar primero con la clave original
            solicitudId = getArguments().getLong(ARG_SOLICITUD_ID, 0);

            // Si no se encontró, intentar con la clave de navegación
            if (solicitudId == 0) {
                solicitudId = getArguments().getLong("solicitudId", 0);
            }

            android.util.Log.d("SolicitudMapaFragment", "onCreate - ID recibido: " + solicitudId);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_solicitud_mapa, container, false);

        tvInfo = view.findViewById(R.id.tvInfo);

        // Obtener el fragmento del mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        cargarSolicitud();

        return view;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Configurar el mapa
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Si ya tenemos la solicitud, mostrarla en el mapa
        if (solicitud != null) {
            mostrarSolicitudEnMapa();
        }
    }

    private void cargarSolicitud() {
        android.util.Log.d("SolicitudMapaFragment", "cargarSolicitud iniciado - solicitudId: " + solicitudId);

        if (solicitudId <= 0) {
            android.util.Log.e("SolicitudMapaFragment", "ID de solicitud inválido: " + solicitudId);
            if (tvInfo != null) {
                tvInfo.setText("ID de solicitud inválido");
            }
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                solicitud = db.solicitudDao().byId(solicitudId);

                android.util.Log.d("SolicitudMapaFragment", "Solicitud cargada: " +
                    (solicitud != null ? "ID=" + solicitud.id + ", Estado=" + solicitud.estado : "NULL"));

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (solicitud != null) {
                            android.util.Log.d("SolicitudMapaFragment", "Actualizando UI con solicitud: " + solicitud.id);
                            actualizarInfo();
                            if (mMap != null) {
                                mostrarSolicitudEnMapa();
                            }
                        } else {
                            android.util.Log.w("SolicitudMapaFragment", "Solicitud no encontrada en BD para ID: " + solicitudId);
                            if (tvInfo != null) {
                                tvInfo.setText("Solicitud no encontrada (ID: " + solicitudId + ")");
                            }
                        }
                    });
                }

            } catch (Exception e) {
                android.util.Log.e("SolicitudMapaFragment", "Error cargando solicitud ID " + solicitudId, e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (tvInfo != null) {
                            tvInfo.setText("Error al cargar la solicitud: " + e.getMessage() + " (ID: " + solicitudId + ")");
                        }
                    });
                }
            }
        });
    }

    private void actualizarInfo() {
        if (solicitud != null) {
            String info = String.format("Guía: %s\nDirección: %s\nEstado: %s",
                    solicitud.guia != null ? solicitud.guia : "—",
                    solicitud.direccion != null ? solicitud.direccion : "—",
                    solicitud.estado != null ? solicitud.estado : "—");
            tvInfo.setText(info);
        }
    }

    private void mostrarSolicitudEnMapa() {
        if (solicitud != null && solicitud.lat != null && solicitud.lon != null) {
            LatLng ubicacionDestino = new LatLng(solicitud.lat, solicitud.lon);

            // Agregar marcador del destino
            mMap.addMarker(new MarkerOptions()
                    .position(ubicacionDestino)
                    .title(solicitud.guia != null ? solicitud.guia : "Destino")
                    .snippet(solicitud.direccion));

            // Buscar y mostrar ubicación del recolector
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(requireContext());

                    // Buscar si hay una asignación para esta solicitud
                    com.hfad.encomiendas.data.Asignacion asignacion = db.asignacionDao().getBySolicitudId(solicitud.id);

                    Double recolectorLat = null, recolectorLon = null;
                    String infoRecolector = "";

                    if (asignacion != null) {
                        // HAY ASIGNACIÓN - mostrar ubicación garantizada
                        // Usar ubicación simulada cerca del destino
                        recolectorLat = solicitud.lat + 0.008; // ~800m al norte
                        recolectorLon = solicitud.lon + 0.008; // ~800m al este
                        infoRecolector = "Recolector asignado";

                        // Intentar obtener ubicación real del tracking si existe
                        com.hfad.encomiendas.data.TrackingEventDao.LastLoc trackingLoc =
                            db.trackingEventDao().lastLocationForShipment(solicitud.id);

                        if (trackingLoc != null && trackingLoc.lat != null && trackingLoc.lon != null) {
                            try {
                                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(
                                    "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
                                long trackingTime = f.parse(trackingLoc.whenIso.replace('Z', ' ').trim().substring(0, 19)).getTime();
                                long now = System.currentTimeMillis();
                                long diffMinutes = (now - trackingTime) / (1000 * 60);

                                // Solo usar tracking si es muy reciente (menos de 30 minutos)
                                if (diffMinutes <= 30) {
                                    recolectorLat = trackingLoc.lat;
                                    recolectorLon = trackingLoc.lon;
                                    infoRecolector = "Recolector (ubicación real)";
                                }
                            } catch (Exception parseEx) {
                                android.util.Log.w("SolicitudMapaFragment", "Error parseando fecha tracking", parseEx);
                            }
                        }
                    } else {
                        // SIN ASIGNACIÓN - solo mostrar si hay tracking real
                        com.hfad.encomiendas.data.TrackingEventDao.LastLoc loc =
                            db.trackingEventDao().lastLocationForShipment(solicitud.id);
                        if (loc != null && loc.lat != null && loc.lon != null) {
                            recolectorLat = loc.lat;
                            recolectorLon = loc.lon;
                            infoRecolector = "Ubicación de tracking";
                        }
                    }

                    // Actualizar UI en el hilo principal
                    final Double finalRecolectorLat = recolectorLat;
                    final Double finalRecolectorLon = recolectorLon;
                    final String finalInfoRecolector = infoRecolector;

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (finalRecolectorLat != null && finalRecolectorLon != null) {
                                LatLng ubicacionRecolector = new LatLng(finalRecolectorLat, finalRecolectorLon);

                                // Agregar marcador del recolector
                                mMap.addMarker(new MarkerOptions()
                                        .position(ubicacionRecolector)
                                        .title(finalInfoRecolector)
                                        .snippet(String.format("Lat: %.5f, Lon: %.5f", finalRecolectorLat, finalRecolectorLon)));

                                // Ajustar la cámara para mostrar ambos marcadores
                                com.google.android.gms.maps.model.LatLngBounds.Builder builder =
                                    new com.google.android.gms.maps.model.LatLngBounds.Builder();
                                builder.include(ubicacionDestino);
                                builder.include(ubicacionRecolector);

                                com.google.android.gms.maps.model.LatLngBounds bounds = builder.build();
                                int padding = 100; // padding en pixels
                                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                            } else {
                                // Solo mostrar el destino si no hay ubicación de recolector
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ubicacionDestino, 15));
                            }
                        });
                    }

                } catch (Exception e) {
                    android.util.Log.e("SolicitudMapaFragment", "Error cargando ubicación del recolector", e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            // Fallback: solo mostrar el destino
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ubicacionDestino, 15));
                        });
                    }
                }
            });
        }
    }
}
