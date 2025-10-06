package com.hfad.encomiendas.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AddressComponent;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.hfad.encomiendas.BuildConfig;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.core.SessionManager;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Slot;
import com.hfad.encomiendas.data.SlotDao;
import com.hfad.encomiendas.data.Solicitud;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class SolicitarRecoleccionFragment extends Fragment {

    // ---- UI: ORIGEN ----
    private TextView tvDirOrigen;
    private MaterialAutoCompleteTextView etCiudadRecogida, etTipoZona, etBarrioVereda;

    private SwitchMaterial swDesglosar;
    private LinearLayout llDesgloseDireccion;
    private TextInputEditText etTipoVia, etVia, etNumero, etAptoBloque;

    // ---- UI: DESTINO ----
    private TextView tvDirDestino;
    private MaterialAutoCompleteTextView etCiudadDestino;

    // ---- UI: Ventana de tiempo (12h) ----
    private TextInputEditText etFecha, etHoraDesde, etHoraHasta;

    // ---- UI: Envío / Pago ----
    private MaterialAutoCompleteTextView etTamanoPaquete;
    private MaterialAutoCompleteTextView etFormaPago;
    private TextInputEditText etValorDeclarado, etIndicaciones;

    private MaterialButton btnSolicitar;

    // ---- Estado: direcciones / lugar ----
    private String dirOrigen = "", dirDestino = "";
    private Double latOrigen = null, lonOrigen = null;
    private Double latDestino = null, lonDestino = null;
    private String munOrigen = null, barrioOrigen = null, zonaOrigen = null;
    private String munDestino = null;

    // ---- Estado: fecha/hora ----
    private long selectedDateMillis = 0L;
    private int startHour = -1, startMinute = -1, endHour = -1, endMinute = -1;

    // ---- UI: Slots ----
    private ChipGroup cgSlots; // nuevo

    public SolicitarRecoleccionFragment() {}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_solicitar_recoleccion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // ---- Bind ORIGEN ----
        tvDirOrigen      = v.findViewById(R.id.tvDireccionOrigen);
        etCiudadRecogida = v.findViewById(R.id.etCiudadRecogida);
        etTipoZona       = v.findViewById(R.id.etTipoZona);
        etBarrioVereda   = v.findViewById(R.id.etBarrioVereda);

        swDesglosar         = v.findViewById(R.id.swDesglosar);
        llDesgloseDireccion = v.findViewById(R.id.llDesgloseDireccion);
        etTipoVia           = v.findViewById(R.id.etTipoVia);
        etVia               = v.findViewById(R.id.etVia);
        etNumero            = v.findViewById(R.id.etNumero);
        etAptoBloque        = v.findViewById(R.id.etAptoBloque);

        // ---- Bind DESTINO ----
        tvDirDestino    = v.findViewById(R.id.tvDireccionDestino);
        etCiudadDestino = v.findViewById(R.id.etCiudadDestino);

        // ---- Bind TIEMPO ----
        etFecha     = v.findViewById(R.id.etFecha);
        etHoraDesde = v.findViewById(R.id.etHoraDesde);
        etHoraHasta = v.findViewById(R.id.etHoraHasta);

        // ---- Bind ENVÍO / PAGO ----
        etTamanoPaquete = v.findViewById(R.id.etTamanoPaquete);
        etFormaPago     = v.findViewById(R.id.etFormaPago);
        etValorDeclarado= v.findViewById(R.id.etValorDeclarado);
        etIndicaciones  = v.findViewById(R.id.etIndicaciones);

        btnSolicitar = v.findViewById(R.id.btnSolicitar);
        cgSlots = v.findViewById(R.id.cgSlots);

        if (etTamanoPaquete != null) {
            etTamanoPaquete.setSimpleItems(R.array.tipos_producto);
            etTamanoPaquete.setOnClickListener(x -> etTamanoPaquete.showDropDown());
            etTamanoPaquete.setOnFocusChangeListener((vv, f) -> { if (f) etTamanoPaquete.showDropDown(); });
        }
        if (etFormaPago != null) {
            etFormaPago.setSimpleItems(R.array.formas_pago);
            etFormaPago.setOnClickListener(x -> etFormaPago.showDropDown());
            etFormaPago.setOnFocusChangeListener((vv, f) -> { if (f) etFormaPago.showDropDown(); });
        }
        if (etTipoZona != null) {
            etTipoZona.setSimpleItems(R.array.tipos_zona);
            etTipoZona.setOnClickListener(x -> etTipoZona.showDropDown());
            etTipoZona.setOnFocusChangeListener((vv, f) -> { if (f) etTipoZona.showDropDown(); });
        }

        if (swDesglosar != null && llDesglosarDireccionVisible() == false) {
            swDesglosar.setOnCheckedChangeListener((b, isChecked) ->
                    llDesgloseDireccion.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        }

        setupDateField(etFecha);
        setupTimeField12(etHoraDesde, true);
        setupTimeField12(etHoraHasta, false);

        if (btnSolicitar != null) btnSolicitar.setOnClickListener(vw -> guardarSolicitud());

        ensurePlacesInit();
        attachAutocompleteOrigen();
        attachAutocompleteDestino();
    }

    // ----------------- Google Places -----------------
    private void ensurePlacesInit() {
        if (!Places.isInitialized()) {
            Places.initialize(requireContext().getApplicationContext(), BuildConfig.MAPS_API_KEY);
        }
    }

    private void attachAutocompleteOrigen() {
        AutocompleteSupportFragment ac =
                (AutocompleteSupportFragment) getChildFragmentManager().findFragmentById(R.id.flPlacesOrigen);
        if (ac == null) {
            ac = AutocompleteSupportFragment.newInstance();
            getChildFragmentManager().beginTransaction().replace(R.id.flPlacesOrigen, ac).commitNow();
        }
        ac.setHint("Dirección de recogida…");
        ac.setCountries(Arrays.asList("CO"));
        ac.setPlaceFields(Arrays.asList(
                Place.Field.ADDRESS, Place.Field.ADDRESS_COMPONENTS, Place.Field.LAT_LNG, Place.Field.NAME
        ));
        ac.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override public void onPlaceSelected(@NonNull Place place) {
                dirOrigen = firstNonEmpty(place.getAddress(), place.getName());
                tvDirOrigen.setText(dirOrigen);

                if (place.getLatLng() != null) { latOrigen = place.getLatLng().latitude; lonOrigen = place.getLatLng().longitude; }
                else { latOrigen = lonOrigen = null; }

                munOrigen   = pickComponent(place, "locality");
                if (isEmpty(munOrigen)) munOrigen = pickComponent(place, "administrative_area_level_2");
                if (isEmpty(munOrigen)) munOrigen = pickComponent(place, "administrative_area_level_1");

                barrioOrigen = firstNonEmpty(
                        pickComponent(place, "neighborhood"),
                        pickComponent(place, "sublocality"),
                        pickComponent(place, "sublocality_level_1")
                );
                zonaOrigen = inferZonaTipo(place);

                if (!isEmpty(munOrigen) && etCiudadRecogida != null) etCiudadRecogida.setText(munOrigen, false);
                if (!isEmpty(barrioOrigen) && etBarrioVereda != null) etBarrioVereda.setText(barrioOrigen);
                if (!isEmpty(zonaOrigen) && etTipoZona != null) etTipoZona.setText(zonaOrigen, false);

                if (llDesglosarDireccionVisible()) {
                    if (etTipoVia != null) etTipoVia.setText("");
                    if (etVia != null) etVia.setText("");
                    if (etNumero != null) etNumero.setText("");
                    if (etAptoBloque != null) etAptoBloque.setText("");
                }
            }
            @Override public void onError(@NonNull com.google.android.gms.common.api.Status status) {
                toast("Error origen: " + status.getStatusMessage());
            }
        });
    }

    private void attachAutocompleteDestino() {
        AutocompleteSupportFragment ac =
                (AutocompleteSupportFragment) getChildFragmentManager().findFragmentById(R.id.flPlacesDestino);
        if (ac == null) {
            ac = AutocompleteSupportFragment.newInstance();
            getChildFragmentManager().beginTransaction().replace(R.id.flPlacesDestino, ac).commitNow();
        }
        ac.setHint("Dirección de destino…");
        ac.setCountries(Arrays.asList("CO"));
        ac.setPlaceFields(Arrays.asList(
                Place.Field.ADDRESS, Place.Field.ADDRESS_COMPONENTS, Place.Field.LAT_LNG, Place.Field.NAME
        ));
        ac.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override public void onPlaceSelected(@NonNull Place place) {
                dirDestino = firstNonEmpty(place.getAddress(), place.getName());
                tvDirDestino.setText(dirDestino);

                if (place.getLatLng() != null) { latDestino = place.getLatLng().latitude; lonDestino = place.getLatLng().longitude; }
                else { latDestino = lonDestino = null; }

                munDestino = firstNonEmpty(
                        pickComponent(place, "locality"),
                        pickComponent(place, "administrative_area_level_2"),
                        pickComponent(place, "administrative_area_level_1")
                );
                if (!isEmpty(munDestino) && etCiudadDestino != null) etCiudadDestino.setText(munDestino, false);
            }
            @Override public void onError(@NonNull com.google.android.gms.common.api.Status status) {
                toast("Error destino: " + status.getStatusMessage());
            }
        });
    }

    private static @Nullable String pickComponent(Place p, String typeKey) {
        if (p.getAddressComponents() == null) return null;
        List<AddressComponent> comps = p.getAddressComponents().asList();
        for (AddressComponent c : comps) {
            if (c.getTypes().contains(typeKey)) return c.getName();
        }
        return null;
    }

    private static String inferZonaTipo(Place p) {
        String[] ruralKeys = {"vereda", "corregimiento", "rural"};
        if (p.getAddressComponents() != null) {
            for (AddressComponent c : p.getAddressComponents().asList()) {
                String name = c.getName() == null ? "" : c.getName().toLowerCase(Locale.ROOT);
                for (String rk : ruralKeys) if (name.contains(rk)) return "Rural (vereda/corregimiento)";
            }
        }
        if (!isEmpty(pickComponent(p, "neighborhood")) || !isEmpty(pickComponent(p, "sublocality")))
            return "Urbana (barrio/localidad)";
        return "Urbana (barrio/localidad)";
    }

    // ----------------- Guardado -----------------
    private void guardarSolicitud() {
        if (TextUtils.isEmpty(dirOrigen))  { toast("Selecciona la dirección de recogida"); return; }
        if (TextUtils.isEmpty(dirDestino)) { toast("Selecciona la dirección de destino"); return; }
        if (selectedDateMillis == 0L || startHour < 0 || endHour < 0) {
            toast("Selecciona fecha y ventana de tiempo"); return;
        }
        long ini = combine(selectedDateMillis, startHour, (startMinute < 0 ? 0 : startMinute));
        long fin = combine(selectedDateMillis, endHour,   (endMinute   < 0 ? 0 : endMinute));
        if (fin <= ini) { toast("La hora fin debe ser mayor que la de inicio"); return; }

        String tamano     = textOf(etTamanoPaquete);
        String pago       = textOf(etFormaPago);
        String valorDecl  = textOf(etValorDeclarado);
        String indic      = textOf(etIndicaciones);

        String fechaTxt = textOf(etFecha);
        String hIniTxt  = textOf(etHoraDesde);
        String hFinTxt  = textOf(etHoraHasta);

        // === NOTAS con claves estandarizadas ===
        StringBuilder meta = new StringBuilder();
        appendKV(meta, "Origen",     safe(munOrigen));
        appendKV(meta, "OrigenDir",  safe(dirOrigen));

        // **Usa "Zona"** (lo buscan tus SQL)
        appendKV(meta, "Zona",       safe(firstNonEmpty(barrioOrigen, munOrigen)));
        appendKV(meta, "TipoZona",   safe(zonaOrigen));

        appendKV(meta, "Destino",    safe(munDestino));
        appendKV(meta, "DestinoDir", safe(dirDestino));

        appendKV(meta, "Tamano",     safe(tamano));
        appendKV(meta, "Pago",       safe(pago));
        if (!isEmpty(valorDecl)) appendKV(meta, "Valor", "$" + valorDecl.trim());
        appendKV(meta, "Fecha",      safe(fechaTxt));
        if (!isEmpty(hIniTxt) && !isEmpty(hFinTxt)) appendKV(meta, "Ventana", hIniTxt + "-" + hFinTxt);

        final String notasFinal = isEmpty(indic) ? meta.toString()
                : (indic.trim() + (meta.length()>0 ? " | " : "") + meta.toString());

        SessionManager sm = new SessionManager(requireContext());
        final String email = sm.getEmail();
        if (isEmpty(email)) { toast("Sesión inválida"); return; }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                com.hfad.encomiendas.data.User u = db.userDao().findByEmail(email);
                long remitenteId = (u == null) ? 0L : u.id;

                // === Reserva / creación de slot ===
                String fechaClave = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(selectedDateMillis));
                int inicioMin = startHour * 60 + (startMinute < 0 ? 0 : startMinute);
                int finMin    = endHour   * 60 + (endMinute   < 0 ? 0 : endMinute);
                SlotDao slotDao = db.slotDao();
                Long zonaId = null; // TODO: enlazar con zoneId real si se selecciona zona
                Slot slot = slotDao.findSlot(fechaClave, inicioMin, finMin, zonaId);
                long nowTs = System.currentTimeMillis();
                if (slot == null) {
                    Slot sNew = new Slot();
                    sNew.fecha = fechaClave;
                    sNew.inicioMin = inicioMin;
                    sNew.finMin = finMin;
                    sNew.capacidadMax = 20; // capacidad default; TODO parametrizar
                    sNew.ocupadas = 0;
                    sNew.zonaId = zonaId;
                    sNew.createdAt = nowTs;
                    sNew.updatedAt = null;
                    long newId = slotDao.insert(sNew);
                    slot = slotDao.findSlot(fechaClave, inicioMin, finMin, zonaId);
                    if (slot == null) throw new IllegalStateException("No se pudo crear slot");
                }
                // Intentar ocupar cupo
                int updated = slotDao.tryIncrement(slot.id, nowTs);
                if (updated == 0) {
                    runOnUi(() -> toast("Franja llena, elige otra"));
                    return;
                }

                Solicitud s = new Solicitud();
                s.remitenteId = remitenteId;
                s.recolectorId = null;
                s.slotId = slot.id; // vincular slot reservado

                // principal = origen
                s.direccion = dirOrigen;
                s.fechaEpochMillis = System.currentTimeMillis();
                s.ventanaInicioMillis = ini;
                s.ventanaFinMillis    = fin;

                s.tipoPaquete = isEmpty(tamano) ? "MEDIANO" : tamano;
                s.pesoKg = null; s.volumenM3 = null;

                s.lat = latOrigen; s.lon = lonOrigen;
                s.guia = generarGuia();
                s.estado = "PENDIENTE";
                s.notas  = meta.length()==0 && isEmpty(indic) ? null : notasFinal;

                db.solicitudDao().insert(s);

                runOnUi(() -> {
                    toast("Solicitud creada. Guía: " + s.guia);
                    NavHostFragment.findNavController(this).navigateUp();
                    limpiar();
                });
            } catch (Exception e) {
                runOnUi(() -> toast("Error: " + e.getMessage()));
            }
        });
    }

    // ----------------- Helpers UI / util -----------------
    private void setupDateField(@Nullable TextInputEditText et) {
        if (et == null) return;
        View.OnClickListener showPicker = vv -> {
            Calendar c = Calendar.getInstance();
            DatePickerDialog dlg = new DatePickerDialog(
                    requireContext(),
                    (DatePicker dp, int y, int m, int d) -> {
                        Calendar chosen = Calendar.getInstance();
                        chosen.set(y, m, d, 0, 0, 0);
                        selectedDateMillis = chosen.getTimeInMillis();
                        et.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(new Date(selectedDateMillis)));
                        refreshSlotChips();
                    },
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
            );
            dlg.show();
        };
        et.setOnClickListener(showPicker);
        et.setOnFocusChangeListener((vv, hasFocus) -> { if (hasFocus) showPicker.onClick(vv); });
    }

    private void setupTimeField12(@Nullable TextInputEditText et, boolean isStart) {
        if (et == null) return;
        View.OnClickListener showPicker = vv -> {
            Calendar c = Calendar.getInstance();
            int initHour = c.get(Calendar.HOUR_OF_DAY);
            int initMin  = c.get(Calendar.MINUTE);

            TimePickerDialog dlg = new TimePickerDialog(
                    requireContext(),
                    (tp, hour24, minute) -> {
                        if (isStart) { startHour = hour24; startMinute = minute; }
                        else         { endHour = hour24;   endMinute   = minute; }
                        et.setText(formatTime12(hour24, minute));
                    },
                    initHour, initMin, false
            );
            dlg.show();
        };
        et.setOnClickListener(showPicker);
        et.setOnFocusChangeListener((vv, hasFocus) -> { if (hasFocus) showPicker.onClick(vv); });
    }

    private static String formatTime12(int hour24, int minute) {
        int h = hour24 % 12; if (h == 0) h = 12;
        String ampm = (hour24 < 12) ? "AM" : "PM";
        return String.format(Locale.getDefault(), "%d:%02d %s", h, minute, ampm);
    }

    private static long combine(long dateMillis, int hour24, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dateMillis);
        cal.set(Calendar.HOUR_OF_DAY, hour24);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static void appendKV(StringBuilder sb, String key, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append(" | ");
        sb.append(key).append(": ").append(value.trim());
    }

    private boolean llDesglosarDireccionVisible() {
        return llDesgloseDireccion != null && llDesgloseDireccion.getVisibility() == View.VISIBLE;
    }

    private static String generarGuia() {
        String yyyy = new SimpleDateFormat("yyyy", Locale.getDefault()).format(new Date());
        int rand = (int) (Math.random() * 900000) + 100000;
        return "EC-" + yyyy + "-" + String.format(Locale.getDefault(), "%06d", rand);
    }

    private void limpiar() {
        tvDirOrigen.setText("—"); tvDirDestino.setText("—");
        dirOrigen = dirDestino = ""; latOrigen = lonOrigen = latDestino = lonDestino = null;
        munOrigen = barrioOrigen = zonaOrigen = munDestino = null;

        if (etCiudadRecogida != null) etCiudadRecogida.setText("", false);
        if (etBarrioVereda != null)   etBarrioVereda.setText("");
        if (etTipoZona != null)       etTipoZona.setText("", false);

        if (etCiudadDestino != null)  etCiudadDestino.setText("", false);
        if (etTamanoPaquete != null)  etTamanoPaquete.setText("", false);

        if (etFormaPago != null)      etFormaPago.setText("", false);
        if (etValorDeclarado != null) etValorDeclarado.setText("");
        if (etIndicaciones != null)   etIndicaciones.setText("");

        if (etFecha != null)          etFecha.setText("");
        if (etHoraDesde != null)      etHoraDesde.setText("");
        if (etHoraHasta != null)      etHoraHasta.setText("");

        selectedDateMillis = 0L; startHour = startMinute = endHour = endMinute = -1;
    }

    private void refreshSlotChips(){
        if (cgSlots == null) return;
        cgSlots.removeAllViews();
        if (selectedDateMillis == 0L) return;
        String fechaClave = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(selectedDateMillis));
        int[][] sugeridas = { {8,10}, {10,12}, {12,14}, {14,16}, {16,18} };
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                SlotDao dao = db.slotDao();
                java.util.List<Slot> existentes = dao.listByFecha(fechaClave);
                runOnUi(() -> {
                    for (int[] rango : sugeridas) {
                        int iniH = rango[0]; int finH = rango[1];
                        int iniMin = iniH*60; int finMin = finH*60;
                        Slot match = null;
                        if (existentes != null) {
                            for (Slot s : existentes) {
                                if (s.inicioMin == iniMin && s.finMin == finMin) { match = s; break; }
                            }
                        }
                        int cap = (match==null?20:match.capacidadMax);
                        int occ = (match==null?0:match.ocupadas);
                        Chip chip = new Chip(requireContext());
                        chip.setCheckable(true);
                        chip.setText(String.format(Locale.getDefault(), "%02d:00-%02d:00 (%d/%d)", iniH, finH, occ, cap));
                        chip.setTag(iniH+"-"+finH);
                        if (match != null && occ >= cap) {
                            chip.setEnabled(false);
                            chip.setAlpha(0.5f);
                        }
                        chip.setOnClickListener(v -> {
                            if (!chip.isEnabled()) return;
                            startHour = iniH; startMinute = 0;
                            endHour = finH; endMinute = 0;
                            if (etHoraDesde != null) etHoraDesde.setText(formatTime12(startHour, startMinute));
                            if (etHoraHasta != null) etHoraHasta.setText(formatTime12(endHour, endMinute));
                        });
                        cgSlots.addView(chip);
                    }
                });
            } catch (Exception ignore) {}
        });
    }

    private static String firstNonEmpty(String... xs) {
        if (xs == null) return null;
        for (String s : xs) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }
    private static String safe(String s){ return s==null? "": s; }
    private static boolean isEmpty(String s){ return s==null || s.trim().isEmpty(); }
    private static String textOf(@Nullable TextInputEditText et){ return (et==null||et.getText()==null)?"":et.getText().toString().trim(); }
    private static String textOf(@Nullable com.google.android.material.textfield.MaterialAutoCompleteTextView atv){
        return (atv==null || atv.getText()==null) ? "" : atv.getText().toString().trim();
    }
    private void runOnUi(Runnable r){ if(isAdded()) requireActivity().runOnUiThread(r); }
    private void toast(String t){ if(isAdded()) Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show(); }
}
