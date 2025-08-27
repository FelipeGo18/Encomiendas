package com.hfad.encomiendas.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.hfad.encomiendas.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class SolicitarRecoleccionFragment extends Fragment {

    // Dropdowns
    private AutoCompleteTextView etTipoProducto, etCiudadOrigen, etCiudadDestino, etFormaPago, etCiudadRecogida, etTipoZona;
    // Inputs normales
    private TextInputEditText etBarrioVereda, etDireccion, etTipoVia, etVia, etNumero, etAptoBloque, etIndicaciones;
    private TextInputEditText etFecha, etHoraDesde, etHoraHasta, etValorDeclarado;

    // Desglose toggle y contenedor
    private SwitchMaterial swDesglosar;
    private ViewGroup llDesgloseDireccion;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_solicitar_recoleccion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- FindViews: Dropdowns ---
        etTipoProducto   = view.findViewById(R.id.etTipoProducto);
        etCiudadOrigen   = view.findViewById(R.id.etCiudadOrigen);
        etCiudadDestino  = view.findViewById(R.id.etCiudadDestino);
        etFormaPago      = view.findViewById(R.id.etFormaPago);
        etCiudadRecogida = view.findViewById(R.id.etCiudadRecogida);
        etTipoZona       = view.findViewById(R.id.etTipoZona);

        // --- FindViews: Text fields ---
        etBarrioVereda   = view.findViewById(R.id.etBarrioVereda);
        etDireccion      = view.findViewById(R.id.etDireccion);
        etTipoVia        = view.findViewById(R.id.etTipoVia);
        etVia            = view.findViewById(R.id.etVia);
        etNumero         = view.findViewById(R.id.etNumero);
        etAptoBloque     = view.findViewById(R.id.etAptoBloque);
        etIndicaciones   = view.findViewById(R.id.etIndicaciones);
        etValorDeclarado = view.findViewById(R.id.etValorDeclarado);

        etFecha      = view.findViewById(R.id.etFecha);
        etHoraDesde  = view.findViewById(R.id.etHoraDesde);
        etHoraHasta  = view.findViewById(R.id.etHoraHasta);

        // --- Toggle/Contenedor desglose ---
        swDesglosar        = view.findViewById(R.id.swDesglosar);
        llDesgloseDireccion = view.findViewById(R.id.llDesgloseDireccion);

        // Adapters (dropdowns)
        setAdapter(etTipoProducto, R.array.tipos_producto);
        setAdapter(etCiudadOrigen, R.array.ciudades_co);
        setAdapter(etCiudadDestino, R.array.ciudades_co);
        setAdapter(etFormaPago, R.array.formas_pago);
        setAdapter(etCiudadRecogida, R.array.ciudades_co);
        setAdapter(etTipoZona, R.array.tipos_zona);

        setupDrop(etTipoProducto);
        setupDrop(etCiudadOrigen);
        setupDrop(etCiudadDestino);
        setupDrop(etFormaPago);
        setupDrop(etCiudadRecogida);
        setupDrop(etTipoZona);

        // Pickers de fecha y hora
        setupPickers();

        // --- Lógica de mostrar/ocultar desglose ---
        // Arranque: oculto
        setDesgloseVisible(false);

        // Switch manual
        swDesglosar.setOnCheckedChangeListener((btn, checked) -> setDesgloseVisible(checked));

        // Si el usuario escribe dirección (y no activó el switch), mantenlo oculto
        etDireccion.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!swDesglosar.isChecked()) setDesgloseVisible(false);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Acción principal
        MaterialButton btnSolicitar = view.findViewById(R.id.btnSolicitar);
        btnSolicitar.setOnClickListener(v -> {
            clearErrors();

            if (isEmpty(etCiudadRecogida)) { etCiudadRecogida.setError("Requerido"); toast("Selecciona el municipio de recogida"); return; }
            if (isEmpty(etDireccion))      { etDireccion.setError("Requerido");      toast("Ingresa la dirección");              return; }
            if (isEmpty(etFecha))          { etFecha.setError("Requerido");          toast("Selecciona la fecha de recogida");   return; }
            if (isEmpty(etHoraDesde))      { etHoraDesde.setError("Requerido");      toast("Selecciona la hora de inicio");      return; }
            if (isEmpty(etHoraHasta))      { etHoraHasta.setError("Requerido");      toast("Selecciona la hora de fin");         return; }

            // Si es zona rural, barrio/vereda requerido
            String zona = textOf(etTipoZona.getText());
            if (zona.toLowerCase(Locale.getDefault()).contains("rural") && isEmpty(etBarrioVereda)) {
                etBarrioVereda.setError("Requerido en zona rural");
                toast("Indica la vereda/corregimiento para zona rural");
                return;
            }

            // Fecha no pasada
            Date selDate = parseDate( textOf(etFecha.getText()) );
            if (selDate == null) {
                etFecha.setError("Formato inválido (YYYY-MM-DD)");
                toast("Fecha inválida. Usa formato YYYY-MM-DD.");
                return;
            }
            if (isPast(selDate)) {
                etFecha.setError("La fecha no puede ser pasada");
                toast("La fecha de recogida no puede ser en el pasado");
                return;
            }

            // Horas válidas y 'hasta' > 'desde'
            Integer desdeMin = parseHHmmToMinutes( textOf(etHoraDesde.getText()) );
            Integer hastaMin = parseHHmmToMinutes( textOf(etHoraHasta.getText()) );
            if (desdeMin == null) { etHoraDesde.setError("Formato HH:MM"); toast("Hora desde inválida"); return; }
            if (hastaMin == null) { etHoraHasta.setError("Formato HH:MM"); toast("Hora hasta inválida"); return; }
            if (hastaMin <= desdeMin) {
                etHoraHasta.setError("Debe ser mayor que la hora de inicio");
                toast("‘Hora hasta’ debe ser mayor que ‘Hora desde’");
                return;
            }

            toast("Solicitud enviada (mock): " + buildResumen());
        });
    }

    // ---------- Mostrar/Ocultar desglose ----------
    private void setDesgloseVisible(boolean visible) {
        llDesgloseDireccion.setVisibility(visible ? View.VISIBLE : View.GONE);
        setEnabledDesglose(visible);
        if (!visible) clearDesglose();
    }

    private void setEnabledDesglose(boolean enabled) {
        etTipoVia.setEnabled(enabled);
        etVia.setEnabled(enabled);
        etNumero.setEnabled(enabled);
        etAptoBloque.setEnabled(enabled);
    }

    private void clearDesglose() {
        etTipoVia.setText(null);
        etVia.setText(null);
        etNumero.setText(null);
        etAptoBloque.setText(null);
    }

    // ---------- Pickers ----------
    private void setupPickers() {
        View.OnClickListener dateClick = vv -> showDatePicker(etFecha);
        etFecha.setOnClickListener(dateClick);
        etFecha.setOnFocusChangeListener((v, has) -> { if (has) showDatePicker(etFecha); });

        View.OnClickListener timeFromClick = vv -> showTimePicker(etHoraDesde);
        etHoraDesde.setOnClickListener(timeFromClick);
        etHoraDesde.setOnFocusChangeListener((v, has) -> { if (has) showTimePicker(etHoraDesde); });

        View.OnClickListener timeToClick = vv -> showTimePicker(etHoraHasta);
        etHoraHasta.setOnClickListener(timeToClick);
        etHoraHasta.setOnFocusChangeListener((v, has) -> { if (has) showTimePicker(etHoraHasta); });
    }

    private void showDatePicker(TextInputEditText target) {
        final Calendar c = Calendar.getInstance();
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH);
        int d = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dlg = new DatePickerDialog(requireContext(),
                (view, year, month, dayOfMonth) -> {
                    String mm = String.format(Locale.getDefault(), "%02d", month + 1);
                    String dd = String.format(Locale.getDefault(), "%02d", dayOfMonth);
                    target.setText(year + "-" + mm + "-" + dd);
                }, y, m, d);
        dlg.show();
    }

    private void showTimePicker(TextInputEditText target) {
        final Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);

        TimePickerDialog dlg = new TimePickerDialog(requireContext(),
                (view, hourOfDay, minute) -> {
                    String hh = String.format(Locale.getDefault(), "%02d", hourOfDay);
                    String mm = String.format(Locale.getDefault(), "%02d", minute);
                    target.setText(hh + ":" + mm);
                }, h, min, true);
        dlg.show();
    }

    // ---------- Helpers UI ----------
    private void setAdapter(AutoCompleteTextView view, int arrayRes) {
        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(requireContext(), arrayRes, android.R.layout.simple_list_item_1);
        view.setAdapter(adapter);
        view.setThreshold(0);
    }

    private void setupDrop(AutoCompleteTextView v) {
        v.setOnClickListener(x -> v.showDropDown());
        v.setOnFocusChangeListener((vv, hasFocus) -> { if (hasFocus) v.showDropDown(); });
    }

    private void clearErrors() {
        etCiudadRecogida.setError(null);
        etDireccion.setError(null);
        etFecha.setError(null);
        etHoraDesde.setError(null);
        etHoraHasta.setError(null);
        etBarrioVereda.setError(null);
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private boolean isEmpty(AutoCompleteTextView v) {
        CharSequence t = v.getText();
        return t == null || TextUtils.isEmpty(t.toString().trim());
    }

    private boolean isEmpty(TextInputEditText v) {
        CharSequence t = v.getText();
        return t == null || TextUtils.isEmpty(t.toString().trim());
    }

    private String textOf(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    // ---------- Validaciones de fecha/hora ----------
    private Date parseDate(String s) {
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            df.setLenient(false);
            return df.parse(s);
        } catch (ParseException e) {
            return null;
        }
    }

    private boolean isPast(Date d) {
        Calendar today = Calendar.getInstance();
        zeroTime(today);
        Calendar sel = Calendar.getInstance();
        sel.setTime(d);
        zeroTime(sel);
        return sel.before(today);
    }

    private void zeroTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private Integer parseHHmmToMinutes(String s) {
        try {
            SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            tf.setLenient(false);
            Date d = tf.parse(s);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        } catch (ParseException e) {
            return null;
        }
    }

    // ---------- Resumen ----------
    private String buildResumen() {
        String tipo   = textOf(etTipoProducto.getText());
        String origen = textOf(etCiudadOrigen.getText());
        String dest   = textOf(etCiudadDestino.getText());
        String fecha  = textOf(etFecha.getText());
        String desde  = textOf(etHoraDesde.getText());
        String hasta  = textOf(etHoraHasta.getText());
        return tipo + " | " + origen + "→" + dest + " | " + fecha + " " + desde + "-" + hasta;
    }
}
