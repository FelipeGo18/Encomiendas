package com.hfad.encomiendas.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Solicitud;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executors;

public class SolicitarRecoleccionFragment extends Fragment {

    // Dropdowns
    private MaterialAutoCompleteTextView etCiudadRecogida;
    private MaterialAutoCompleteTextView etTipoZona;
    private MaterialAutoCompleteTextView etTipoProducto;
    private MaterialAutoCompleteTextView etCiudadOrigen;
    private MaterialAutoCompleteTextView etCiudadDestino;
    private MaterialAutoCompleteTextView etFormaPago;

    // Inputs
    private TextInputEditText etBarrioVereda, etDireccion, etFecha, etHoraDesde, etHoraHasta,
            etValorDeclarado;

    private MaterialButton btnSolicitar;

    private final Calendar cal = Calendar.getInstance();

    public SolicitarRecoleccionFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_solicitar_recoleccion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // Bind
        etCiudadRecogida = v.findViewById(R.id.etCiudadRecogida);
        etTipoZona       = v.findViewById(R.id.etTipoZona);
        etTipoProducto   = v.findViewById(R.id.etTipoProducto);
        etCiudadOrigen   = v.findViewById(R.id.etCiudadOrigen);
        etCiudadDestino  = v.findViewById(R.id.etCiudadDestino);
        etFormaPago      = v.findViewById(R.id.etFormaPago);

        etBarrioVereda   = v.findViewById(R.id.etBarrioVereda);
        etDireccion      = v.findViewById(R.id.etDireccion);
        etFecha          = v.findViewById(R.id.etFecha);
        etHoraDesde      = v.findViewById(R.id.etHoraDesde);
        etHoraHasta      = v.findViewById(R.id.etHoraHasta);
        etValorDeclarado = v.findViewById(R.id.etValorDeclarado);

        btnSolicitar     = v.findViewById(R.id.btnSolicitar);

        // Dropdowns con arrays
        wireDropdown(etCiudadRecogida, R.array.ciudades_co);
        wireDropdown(etTipoZona,       R.array.tipos_zona);
        wireDropdown(etTipoProducto,   R.array.tipos_producto);
        wireDropdown(etCiudadOrigen,   R.array.ciudades_co);
        wireDropdown(etCiudadDestino,  R.array.ciudades_co);
        wireDropdown(etFormaPago,      R.array.formas_pago);

        // Pickers de fecha/hora
        setupDateField(etFecha);
        setupTimeField(etHoraDesde);
        setupTimeField(etHoraHasta);

        if (btnSolicitar != null) btnSolicitar.setOnClickListener(vw -> guardarSolicitud());
    }

    // ---------- Pickers ----------
    private void setupDateField(@Nullable TextInputEditText et) {
        if (et == null) return;
        View.OnClickListener showPicker = vv -> {
            final Calendar c = Calendar.getInstance();
            DatePickerDialog dlg = new DatePickerDialog(
                    requireContext(),
                    (DatePicker dp, int y, int m, int d) -> {
                        Calendar chosen = Calendar.getInstance();
                        chosen.set(y, m, d, 0, 0, 0);
                        et.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosen.getTime()));
                    },
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)
            );
            dlg.show();
        };
        et.setOnClickListener(showPicker);
        et.setOnFocusChangeListener((vv, hasFocus) -> { if (hasFocus) showPicker.onClick(vv); });
    }

    private void setupTimeField(@Nullable TextInputEditText et) {
        if (et == null) return;
        View.OnClickListener showPicker = vv -> {
            Calendar c = Calendar.getInstance();
            TimePickerDialog dlg = new TimePickerDialog(
                    requireContext(),
                    (tp, hour, minute) -> {
                        String hh = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
                        et.setText(hh);
                    },
                    c.get(Calendar.HOUR_OF_DAY),
                    c.get(Calendar.MINUTE),
                    true  // 24h
            );
            dlg.show();
        };
        et.setOnClickListener(showPicker);
        et.setOnFocusChangeListener((vv, hasFocus) -> { if (hasFocus) showPicker.onClick(vv); });
    }

    private void wireDropdown(@Nullable MaterialAutoCompleteTextView view, int arrayRes) {
        if (view == null) return;
        String[] items = getResources().getStringArray(arrayRes);
        view.setSimpleItems(items);
        view.setOnClickListener(v -> view.showDropDown());
        view.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) view.showDropDown(); });
    }

    // ---------- Guardar ----------
    private void guardarSolicitud() {
        String municipio   = textOf(etCiudadRecogida);
        String tipoProd    = textOf(etTipoProducto);
        String ciudadOri   = textOf(etCiudadOrigen);
        String ciudadDes   = textOf(etCiudadDestino);
        String formaPago   = textOf(etFormaPago);

        String barrio      = textOf(etBarrioVereda);
        String direccion   = textOf(etDireccion);
        String fecha       = textOf(etFecha);
        String desde       = textOf(etHoraDesde);
        String hasta       = textOf(etHoraHasta);
        Double valorDecl   = parseDoubleSafe(textOf(etValorDeclarado));

        if (TextUtils.isEmpty(municipio) || TextUtils.isEmpty(direccion)) {
            toast("Completa municipio y dirección"); return;
        }
        if (TextUtils.isEmpty(fecha) || TextUtils.isEmpty(desde) || TextUtils.isEmpty(hasta)) {
            toast("Completa la fecha y la ventana de tiempo"); return;
        }

        Solicitud s = new Solicitud();
        s.municipio      = municipio;
        s.barrioVereda   = barrio;
        s.direccion      = direccion;
        s.fecha          = fecha;
        s.horaDesde      = desde;
        s.horaHasta      = hasta;
        s.tipoProducto   = tipoProd;
        s.ciudadOrigen   = ciudadOri;
        s.ciudadDestino  = ciudadDes;
        s.formaPago      = formaPago;
        s.valorDeclarado = valorDecl;
        s.createdAt      = System.currentTimeMillis();

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            db.solicitudDao().insert(s);
            runOnUi(() -> {
                toast("Solicitud guardada");
                limpiar();
            });
        });
    }

    private void limpiar() {
        clearText(etBarrioVereda, etDireccion, etFecha, etHoraDesde, etHoraHasta, etValorDeclarado);
        if (etCiudadRecogida != null) etCiudadRecogida.setText("", false);
        if (etTipoZona != null)       etTipoZona.setText("", false);
        if (etTipoProducto != null)   etTipoProducto.setText("", false);
        if (etCiudadOrigen != null)   etCiudadOrigen.setText("", false);
        if (etCiudadDestino != null)  etCiudadDestino.setText("", false);
        if (etFormaPago != null)      etFormaPago.setText("", false);
    }

    // ---------- utils ----------
    private String textOf(@Nullable TextView tv) {
        return (tv == null || tv.getText() == null) ? "" : tv.getText().toString().trim();
    }
    private void clearText(TextInputEditText... edits) {
        if (edits == null) return;
        for (TextInputEditText e : edits) if (e != null) e.setText("");
    }
    private Double parseDoubleSafe(String s) {
        try { return TextUtils.isEmpty(s) ? 0d : Double.parseDouble(s); }
        catch (Exception e) { return 0d; }
    }
    private void runOnUi(Runnable r) { if (!isAdded()) return; requireActivity().runOnUiThread(r); }
    private void toast(String t) { if (!isAdded()) return; Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show(); }
}