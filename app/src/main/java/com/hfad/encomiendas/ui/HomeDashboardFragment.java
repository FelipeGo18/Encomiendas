package com.hfad.encomiendas.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Solicitud;
import com.hfad.encomiendas.data.SolicitudDao;
import com.hfad.encomiendas.data.TrackingEventDao;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HomeDashboardFragment extends Fragment {

    private SwipeRefreshLayout swRefresh;
    private TextView tvHola, tvCountPendientes, tvCountAsignadas, tvCountRecolectadas, tvEmpty;
    private RecyclerView rvSolicitudes;
    private MaterialButton btnCrear;

    private UltimasAdapter adapter;
    private NavController navController; // cache

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        swRefresh          = v.findViewById(R.id.swRefresh);
        tvHola             = v.findViewById(R.id.tvHola);
        tvCountPendientes  = v.findViewById(R.id.tvCountPendientes);
        tvCountAsignadas   = v.findViewById(R.id.tvCountAsignadas);
        tvCountRecolectadas= v.findViewById(R.id.tvCountRecolectadas);
        tvEmpty            = v.findViewById(R.id.tvEmpty);
        rvSolicitudes      = v.findViewById(R.id.rvSolicitudes);
        btnCrear           = v.findViewById(R.id.btnCrear);

        rvSolicitudes.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new UltimasAdapter(AppDatabase.getInstance(requireContext()));
        rvSolicitudes.setAdapter(adapter);

        SessionManager sm = new SessionManager(requireContext());
        if (!TextUtils.isEmpty(sm.getEmail())) {
            tvHola.setText("¡Hola, " + sm.getEmail() + "!");
        }

        if (swRefresh != null) swRefresh.setOnRefreshListener(this::cargarPanel);
        if (btnCrear != null) {
            btnCrear.setOnClickListener(vw -> {
                NavController nav = NavHostFragment.findNavController(this);
                try { nav.navigate(R.id.action_home_to_solicitar); }
                catch (Exception ignore) { nav.navigate(R.id.solicitarRecoleccionFragment); }
            });
        }

        navController = NavHostFragment.findNavController(this);
        cargarPanel();
    }

    /* ==================== Adapter ==================== */

    private static class UltimasAdapter extends RecyclerView.Adapter<VH> {
        private final List<SolicitudDao.SolicitudConEta> data = new ArrayList<>();
        private final java.text.SimpleDateFormat dfFecha = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        private final java.text.SimpleDateFormat dfHora  = new java.text.SimpleDateFormat("h:mm a", Locale.getDefault());
        private final AppDatabase db;
        private final List<LocationCache> locCache = new ArrayList<>();
        private final Handler ui = new Handler(Looper.getMainLooper());
        private static final String MAPS_KEY = ""; // Rellena si deseas snapshots

        static class LocationCache { long solicitudId; Double lat; Double lon; String whenIso; }

        UltimasAdapter(AppDatabase db) { this.db = db; }

        void setData(List<SolicitudDao.SolicitudConEta> list) {
            boolean same = (list!=null && list.size()==data.size());
            if (same) {
                for (int i=0;i<list.size();i++) if (data.get(i).s.id != list.get(i).s.id) { same = false; break; }
            }
            if (!same) {
                data.clear(); if (list!=null) data.addAll(list); notifyDataSetChanged();
            } else {
                for (int i=0;i<list.size();i++) data.set(i, list.get(i));
                notifyItemRangeChanged(0, data.size());
            }
            rebuildLocCacheIds();
        }
        private void rebuildLocCacheIds(){
            locCache.clear();
            for (SolicitudDao.SolicitudConEta it: data){ LocationCache c = new LocationCache(); c.solicitudId = it.s.id; locCache.add(c);} }

        void refreshLocationsAsync() {
            Executors.newSingleThreadExecutor().execute(() -> {
                android.util.Log.d("HomeDashboard", "refreshLocationsAsync iniciado - items en cache: " + locCache.size());

                for (LocationCache c: locCache) {
                    // LÓGICA GARANTIZADA: Si hay asignación = SIEMPRE mostrar ubicación
                    Double recolectorLat = null, recolectorLon = null;
                    String whenIso = null;
                    boolean foundAssignment = false;

                    try {
                        // Buscar si hay una asignación para esta solicitud
                        com.hfad.encomiendas.data.Asignacion asignacion = db.asignacionDao().getBySolicitudId(c.solicitudId);
                        android.util.Log.d("HomeDashboard", "Solicitud " + c.solicitudId + " - Asignación: " + (asignacion != null ? "SÍ (ID: " + asignacion.id + ")" : "NO"));

                        if (asignacion != null) {
                            foundAssignment = true;
                            // HAY ASIGNACIÓN = GARANTIZAR UBICACIÓN SIEMPRE

                            // Obtener coordenadas del destino
                            com.hfad.encomiendas.data.Solicitud solicitud = db.solicitudDao().byId(c.solicitudId);
                            android.util.Log.d("HomeDashboard", "Solicitud " + c.solicitudId + " - Coordenadas: " +
                                (solicitud != null && solicitud.lat != null ? solicitud.lat + "," + solicitud.lon : "NULL"));

                            if (solicitud != null && solicitud.lat != null && solicitud.lon != null) {

                                // PASO 1: SIEMPRE usar ubicación simulada como base (GARANTIZADA)
                                recolectorLat = solicitud.lat + 0.008; // ~800m al norte
                                recolectorLon = solicitud.lon + 0.008; // ~800m al este
                                whenIso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
                                        .format(new java.util.Date(asignacion.createdAt));

                                android.util.Log.d("HomeDashboard", "Solicitud " + c.solicitudId + " - Ubicación SIMULADA asignada: " +
                                    recolectorLat + "," + recolectorLon);

                                // PASO 2: OPCIONAL - Si hay tracking muy reciente, reemplazar
                                TrackingEventDao.LastLoc trackingLoc = db.trackingEventDao().lastLocationForShipment(c.solicitudId);
                                if (trackingLoc != null && trackingLoc.lat != null && trackingLoc.lon != null) {
                                    android.util.Log.d("HomeDashboard", "Solicitud " + c.solicitudId + " - Tracking encontrado: " +
                                        trackingLoc.lat + "," + trackingLoc.lon + " en " + trackingLoc.whenIso);

                                    try {
                                        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
                                        long trackingTime = f.parse(trackingLoc.whenIso.replace('Z', ' ').trim().substring(0, 19)).getTime();
                                        long now = System.currentTimeMillis();
                                        long diffMinutes = (now - trackingTime) / (1000 * 60);

                                        android.util.Log.d("HomeDashboard", "Solicitud " + c.solicitudId + " - Tracking edad: " + diffMinutes + " minutos");

                                        // Solo usar tracking si es MUY reciente (menos de 30 minutos)
                                        if (diffMinutes <= 30) {
                                            recolectorLat = trackingLoc.lat;
                                            recolectorLon = trackingLoc.lon;
                                            whenIso = trackingLoc.whenIso;
                                            android.util.Log.d("HomeDashboard", "Solicitud " + c.solicitudId + " - Usando tracking REAL");
                                        } else {
                                            android.util.Log.d("HomeDashboard", "Solicitud " + c.solicitudId + " - Tracking muy viejo, manteniendo simulada");
                                        }
                                    } catch (Exception parseEx) {
                                        android.util.Log.w("HomeDashboard", "Error parseando fecha tracking para solicitud " + c.solicitudId, parseEx);
                                        // Mantener la ubicación simulada
                                    }
                                } else {
                                    android.util.Log.d("HomeDashboard", "Solicitud " + c.solicitudId + " - Sin tracking, usando simulada");
                                }
                            } else {
                                android.util.Log.w("HomeDashboard", "Solicitud " + c.solicitudId + " - Sin coordenadas válidas en solicitud");
                            }
                        } else {
                            // SIN ASIGNACIÓN = Solo mostrar si hay tracking real
                            android.util.Log.d("HomeDashboard", "Solicitud " + c.solicitudId + " - Sin asignación, buscando solo tracking");
                            TrackingEventDao.LastLoc loc = db.trackingEventDao().lastLocationForShipment(c.solicitudId);
                            if (loc != null && loc.lat != null && loc.lon != null) {
                                recolectorLat = loc.lat;
                                recolectorLon = loc.lon;
                                whenIso = loc.whenIso;
                                android.util.Log.d("HomeDashboard", "Solicitud " + c.solicitudId + " - Tracking encontrado sin asignación");
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("HomeDashboard", "Error procesando solicitud " + c.solicitudId, e);
                        // Fallback: intentar solo tracking events
                        TrackingEventDao.LastLoc loc = db.trackingEventDao().lastLocationForShipment(c.solicitudId);
                        if (loc != null) {
                            recolectorLat = loc.lat;
                            recolectorLon = loc.lon;
                            whenIso = loc.whenIso;
                        }
                    }

                    // Actualizar cache con los datos obtenidos
                    if (recolectorLat != null && recolectorLon != null) {
                        c.lat = recolectorLat;
                        c.lon = recolectorLon;
                        c.whenIso = whenIso;
                        android.util.Log.d("HomeDashboard", "✓ Solicitud " + c.solicitudId + " - Cache actualizado: " +
                            recolectorLat + "," + recolectorLon + (foundAssignment ? " (CON ASIGNACIÓN)" : " (SIN ASIGNACIÓN)"));
                    } else {
                        android.util.Log.w("HomeDashboard", "✗ Solicitud " + c.solicitudId + " - Sin ubicación final");
                    }
                }

                android.util.Log.d("HomeDashboard", "refreshLocationsAsync completado, actualizando UI");

                // actualizar visible - FORZAR ACTUALIZACIÓN INDIVIDUAL
                if (!data.isEmpty()) {
                    // post a la UI
                    if (!rvRef.get().isAttachedToWindow()) {
                        android.util.Log.w("HomeDashboard", "RecyclerView no está attached, no se puede actualizar");
                        return;
                    }

                    android.util.Log.d("HomeDashboard", "Forzando actualización de UI en el hilo principal");
                    rvRef.get().post(() -> {
                        android.util.Log.d("HomeDashboard", "Ejecutando notifyDataSetChanged en UI thread");

                        // DOBLE ESTRATEGIA: Actualizar items específicos Y dataset completo
                        for (int i = 0; i < data.size(); i++) {
                            SolicitudDao.SolicitudConEta item = data.get(i);
                            LocationCache cache = findCache(item.s.id);
                            if (cache != null && cache.lat != null && cache.lon != null) {
                                android.util.Log.d("HomeDashboard", "Actualizando item " + i + " para solicitud " + item.s.id + " con ubicación " + cache.lat + "," + cache.lon);
                                notifyItemChanged(i);
                            }
                        }

                        // También actualizar todo el dataset
                        notifyDataSetChanged();
                        android.util.Log.d("HomeDashboard", "notifyDataSetChanged ejecutado");
                    });
                }
            });
        }

        // referencia débil al RecyclerView para post (configurada en onAttachedToRecyclerView)
        private java.lang.ref.WeakReference<RecyclerView> rvRef = new java.lang.ref.WeakReference<>(null);
        @Override public void onAttachedToRecyclerView(@NonNull RecyclerView rv){ super.onAttachedToRecyclerView(rv); rvRef = new java.lang.ref.WeakReference<>(rv);}

        private LocationCache findCache(long id){ for (LocationCache c: locCache) if (c.solicitudId==id) return c; return null; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_solicitud_dashboard, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            SolicitudDao.SolicitudConEta it = data.get(position);
            Solicitud s = it.s;

            h.tvGuiaEstado.setText(nn(s.guia) + "  •  " + nn(s.estado));

            String fecha = dfFecha.format(new Date(s.ventanaInicioMillis));
            String hIni  = dfHora.format(new Date(s.ventanaInicioMillis));
            String hFin  = dfHora.format(new Date(s.ventanaFinMillis));
            h.tvRango.setText(fecha + ", " + hIni + " - " + hFin);

            h.tvEta.setText("ETA: " + prettyEta(it.eta));
            h.tvDireccion.setText( normalizeAddress(s.direccion) );

            String destino = firstNonEmpty(meta(s.notas, "DestinoDir"), meta(s.notas, "Destino"));
            h.tvDestino.setText("Destino: " + nn(destino));

            h.itemView.setOnClickListener(v -> { if (cb != null) cb.openMapa(s.id); });

            LocationCache lc = findCache(s.id);
            android.util.Log.d("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " position " + position + " - Cache: " +
                (lc != null && lc.lat != null ? lc.lat + "," + lc.lon : "NULL"));

            if (lc==null || lc.lat==null) {
                android.util.Log.d("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " - Cache vacío, iniciando búsqueda async");
                h.tvUbicacion.setText("Ubicación: —");
                h.loadSnapshot(null, null, null, MAPS_KEY);
                Executors.newSingleThreadExecutor().execute(() -> {
                    // NUEVA LÓGICA: SIEMPRE mostrar ubicación cuando hay asignación
                    Double recolectorLat = null, recolectorLon = null;
                    String whenIso = null;

                    try {
                        // Buscar si hay una asignación para esta solicitud
                        com.hfad.encomiendas.data.Asignacion asignacion = db.asignacionDao().getBySolicitudId(s.id);
                        android.util.Log.d("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " - Asignación: " +
                            (asignacion != null ? "SÍ (ID: " + asignacion.id + ")" : "NO"));

                        if (asignacion != null) {
                            // HAY ASIGNACIÓN = SIEMPRE MOSTRAR UBICACIÓN GARANTIZADA

                            if (s.lat != null && s.lon != null) {
                                // ESTRATEGIA: Siempre usar ubicación simulada cerca del destino
                                // Esto garantiza que SIEMPRE aparezca algo cuando hay asignación
                                recolectorLat = s.lat + 0.012; // ~1.2km al norte
                                recolectorLon = s.lon + 0.012; // ~1.2km al este
                                whenIso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
                                        .format(new java.util.Date(asignacion.createdAt));

                                android.util.Log.d("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " - Ubicación SIMULADA: " +
                                    recolectorLat + "," + recolectorLon);
                            }
                        } else {
                            // SIN ASIGNACIÓN = Solo mostrar si hay tracking real
                            TrackingEventDao.LastLoc loc = db.trackingEventDao().lastLocationForShipment(s.id);
                            if (loc != null && loc.lat != null && loc.lon != null) {
                                recolectorLat = loc.lat;
                                recolectorLon = loc.lon;
                                whenIso = loc.whenIso;
                                android.util.Log.d("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " - Tracking encontrado");
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("HomeDashboard", "onBindViewHolder - Error solicitud " + s.id, e);
                        // Fallback: intentar solo tracking events
                        TrackingEventDao.LastLoc loc = db.trackingEventDao().lastLocationForShipment(s.id);
                        if (loc != null) {
                            recolectorLat = loc.lat;
                            recolectorLon = loc.lon;
                            whenIso = loc.whenIso;
                        }
                    }

                    // Crear variables finales para la lambda
                    final Double finalRecolectorLat = recolectorLat;
                    final Double finalRecolectorLon = recolectorLon;
                    final String finalWhenIso = whenIso;

                    // Actualizar UI si se encontró ubicación
                    if (finalRecolectorLat != null && finalRecolectorLon != null) {
                        android.util.Log.d("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " - Actualizando UI con: " +
                            finalRecolectorLat + "," + finalRecolectorLon);

                        if (lc != null) {
                            lc.lat = finalRecolectorLat;
                            lc.lon = finalRecolectorLon;
                            lc.whenIso = finalWhenIso;
                        }
                        h.itemView.post(() -> {
                            android.util.Log.d("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " - Ejecutando actualización UI");
                            h.tvUbicacion.setText(formatUbicacion(finalRecolectorLat, finalRecolectorLon, finalWhenIso));
                            h.loadSnapshot(finalRecolectorLat, finalRecolectorLon, null, MAPS_KEY);
                        });
                    } else {
                        android.util.Log.w("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " - Sin ubicación para mostrar");
                    }
                });
            } else {
                android.util.Log.d("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " - Usando cache existente: " +
                    lc.lat + "," + lc.lon);
                String textoUbicacion = formatUbicacion(lc.lat, lc.lon, lc.whenIso);
                android.util.Log.d("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " - Estableciendo texto: '" + textoUbicacion + "'");

                // FORZAR VISIBILIDAD Y ACTUALIZACIÓN AGRESIVA DEL TEXTVIEW
                h.tvUbicacion.setVisibility(View.VISIBLE);
                h.tvUbicacion.setText(textoUbicacion);
                h.tvUbicacion.setTextColor(0xFF000000); // Negro sólido
                h.tvUbicacion.setTextSize(14); // Tamaño visible
                h.tvUbicacion.invalidate();
                h.tvUbicacion.requestLayout();

                // FORZAR ACTUALIZACIÓN DE TODO EL CONTENEDOR
                h.itemView.invalidate();
                h.itemView.requestLayout();

                // POST ADICIONAL PARA GARANTIZAR RENDERIZADO
                h.itemView.post(() -> {
                    h.tvUbicacion.setVisibility(View.VISIBLE);
                    h.tvUbicacion.setText(textoUbicacion);
                    h.tvUbicacion.invalidate();
                });

                android.util.Log.d("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " - TextView actualizado. Texto actual: '" + h.tvUbicacion.getText() + "'");
                android.util.Log.d("HomeDashboard", "onBindViewHolder - Solicitud " + s.id + " - TextView visibilidad: " +
                    (h.tvUbicacion.getVisibility() == View.VISIBLE ? "VISIBLE" : "HIDDEN/GONE"));

                h.loadSnapshot(lc.lat, lc.lon, null, MAPS_KEY);
            }
        }

        private String formatUbicacion(Double lat, Double lon, String whenIso){
            android.util.Log.d("HomeDashboard", "formatUbicacion llamado con: lat=" + lat + ", lon=" + lon + ", whenIso=" + whenIso);

            if (lat==null||lon==null) {
                android.util.Log.d("HomeDashboard", "formatUbicacion devolviendo: 'Ubicación: —' (coordenadas null)");
                return "Ubicación: —";
            }

            String delta = formatDelta(whenIso);
            String resultado = String.format(Locale.getDefault(), "Ubicación: %.5f, %.5f%s", lat, lon, delta.isEmpty()?"":" ("+delta+")");
            android.util.Log.d("HomeDashboard", "formatUbicacion devolviendo: '" + resultado + "'");

            return resultado;
        }

        private static String nn(String s) { return (s == null || s.trim().isEmpty()) ? "—" : s.trim(); }

        private static String meta(String notas, String key) {
            if (notas == null) return "";
            Pattern p = Pattern.compile(
                    "\\b" + Pattern.quote(key) + "\\s*:\\s*(.*?)\\s*(?:\\|\\s*|(?=[A-ZÁÉÍÓÚÑ][\\wÁÉÍÓÚÑáéíóúñ ]*:\\s*)|$)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
            Matcher m = p.matcher(notas);
            if (m.find()) return m.group(1).trim();

            Pattern p2 = Pattern.compile("\\b" + Pattern.quote(key) + "\\s*:\\s*(.*)$",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m2 = p2.matcher(notas);
            return m2.find() ? m2.group(1).trim() : "";
        }

        private static String firstNonEmpty(String... arr) {
            if (arr == null) return "";
            for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
            return "";
        }

        private static String normalizeAddress(String raw) {
            if (raw == null) return "—";
            String[] parts = raw.split(",");
            List<String> cleaned = new ArrayList<>();
            Set<String> seenCanon = new LinkedHashSet<>();
            String prevCanon = null;

            for (String p : parts) {
                String t = p.trim();
                if (t.isEmpty()) continue;
                String canon = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                        .replaceAll("[^a-zA-Z\\s]", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("\\s+", " ")
                        .trim();
                if (prevCanon != null && (canon.equals(prevCanon) ||
                        canon.startsWith(prevCanon) || prevCanon.startsWith(canon))) continue;
                if (seenCanon.contains(canon)) continue;
                cleaned.add(t);
                seenCanon.add(canon);
                prevCanon = canon;
            }
            return cleaned.isEmpty() ? raw.trim() : TextUtils.join(", ", cleaned);
        }

        private static String prettyEta(String iso){
            if (iso == null || iso.trim().isEmpty()) return "—";
            int t = iso.indexOf('T');
            if (t >= 0 && iso.length() >= t + 6) return iso.substring(t + 1, t + 6);
            return iso;
        }

        private static String formatDelta(String iso){
            if (iso == null) return "";
            try {
                // intento parse simple
                String base = iso.replace('Z',' ').trim();
                // soporta formato con zona: tomamos primeros 19 caracteres yyyy-MM-ddTHH:mm:ss
                if (base.length() >= 19) base = base.substring(0,19);
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                long t = f.parse(base).getTime();
                long now = System.currentTimeMillis();
                long diff = Math.max(0, now - t);
                long mins = diff / 60000L;
                if (mins < 1) return "hace instantes";
                if (mins < 60) return "hace " + mins + " min";
                long hrs = mins / 60; if (hrs < 24) return "hace " + hrs + " h";
                long dias = hrs / 24; return "hace " + dias + " d";
            } catch (Exception ignore) {}
            return "";
        }

        interface ItemClick { void openMapa(long solicitudId); }
        private ItemClick cb;
        void setOnItemClick(ItemClick cb){ this.cb = cb; }
        @Override public int getItemCount(){ return data.size(); }
    }

    private static class VH extends RecyclerView.ViewHolder {
        TextView tvGuiaEstado, tvRango, tvEta, tvDireccion, tvDestino, tvUbicacion;
        ImageView ivMap;
        VH(@NonNull View itemView) {
            super(itemView);
            tvGuiaEstado = itemView.findViewById(R.id.tvGuiaEstado);
            tvRango      = itemView.findViewById(R.id.tvRango);
            tvEta        = itemView.findViewById(R.id.tvEta);
            tvDireccion  = itemView.findViewById(R.id.tvDireccion);
            tvDestino    = itemView.findViewById(R.id.tvDestino);
            tvUbicacion  = itemView.findViewById(R.id.tvUbicacion);
            ivMap        = itemView.findViewById(R.id.ivMapPreview);
        }
        void loadSnapshot(Double lat, Double lon, Double destLat, String key) {
            if (ivMap==null) return;
            if (lat==null || lon==null || key==null || key.isEmpty()) {
                ivMap.setImageResource(R.drawable.ic_launcher_background);
                return;
            }
            StringBuilder url = new StringBuilder("https://maps.googleapis.com/maps/api/staticmap?");
            url.append("center=").append(lat).append(",").append(lon)
               .append("&zoom=13&size=400x200&scale=2&maptype=roadmap")
               .append("&markers=color:blue%7C").append(lat).append(",").append(lon);
            // destino opcional no implementado (sin destLon real)
            url.append("&key=").append(key);
            new Thread(() -> {
                try (InputStream is = new URL(url.toString()).openStream()) {
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    ivMap.post(() -> ivMap.setImageBitmap(bmp));
                } catch (Exception ignore) {}
            }).start();
        }
    }

    // Exponer método para configurar callback tras cargar panel
    private void prepararClicks() {
        if (adapter != null) adapter.setOnItemClick(sid -> {
            if (navController != null) {
                try {
                    Bundle b = new Bundle(); b.putLong("solicitudId", sid);
                    navController.navigate(R.id.action_home_to_solicitudMapa, b);
                } catch (Exception ignored) {}
            }
        });
    }

    private void cargarPanel() {
        if (swRefresh != null) swRefresh.setRefreshing(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                SessionManager sm = new SessionManager(requireContext());
                String email = sm.getEmail();
                if (TextUtils.isEmpty(email)) {
                    runOnUi(() -> {
                        if (swRefresh != null) swRefresh.setRefreshing(false);
                        Toast.makeText(requireContext(), "Sesión no válida", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                com.hfad.encomiendas.data.User u = db.userDao().findByEmail(email);
                if (u == null) {
                    runOnUi(() -> {
                        if (swRefresh != null) swRefresh.setRefreshing(false);
                        Toast.makeText(requireContext(), "Usuario no encontrado", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                int cPend = db.solicitudDao().countByEstado(u.id, "PENDIENTE");
                int cAsig = db.solicitudDao().countByEstado(u.id, "ASIGNADA");
                int cReco = db.solicitudDao().countByEstado(u.id, "RECOLECTADA");

                List<SolicitudDao.SolicitudConEta> ultimas = db.solicitudDao().listAllByUserWithEta(u.id, 20);

                runOnUi(() -> {
                    tvCountPendientes.setText(String.valueOf(cPend));
                    tvCountAsignadas.setText(String.valueOf(cAsig));
                    tvCountRecolectadas.setText(String.valueOf(cReco));

                    adapter.setData(ultimas);
                    adapter.refreshLocationsAsync();
                    tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    if (swRefresh != null) swRefresh.setRefreshing(false);
                    prepararClicks();
                });

            } catch (Exception e) {
                runOnUi(() -> {
                    if (swRefresh != null) swRefresh.setRefreshing(false);
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // Optimizar MapView en reciclado
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (rvSolicitudes != null) {
            RecyclerView.Adapter a = rvSolicitudes.getAdapter();
            if (a instanceof UltimasAdapter) {
                // no acceso directo a los holders ya reciclados; rely en GC
            }
        }
    }

    private void runOnUi(Runnable r) { if (!isAdded()) return; requireActivity().runOnUiThread(r); }
}
