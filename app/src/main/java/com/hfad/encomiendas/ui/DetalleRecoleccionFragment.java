package com.hfad.encomiendas.ui;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Asignacion;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.ui.widgets.SignatureView; // <— usa el widget externo

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;

public class DetalleRecoleccionFragment extends Fragment {

    private int asignacionId = -1;

    private TextView tvHeader, tvEstado, tvPedido;
    private ImageView ivFoto;
    private SignatureView signatureView;
    private MaterialButton btnTomarFoto, btnLimpiarFirma, btnGuardarFirma, btnActivarGuia;

    private AppDatabase db;
    private Asignacion current;

    public DetalleRecoleccionFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detalle_recoleccion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        asignacionId   = (getArguments() != null) ? getArguments().getInt("asignacionId", -1) : -1;

        tvHeader       = v.findViewById(R.id.tvHeader);
        tvEstado       = v.findViewById(R.id.tvEstado);
        tvPedido       = v.findViewById(R.id.tvPedido);      // <— NUEVO
        ivFoto         = v.findViewById(R.id.ivFoto);
        signatureView  = v.findViewById(R.id.signatureView);
        btnTomarFoto   = v.findViewById(R.id.btnTomarFoto);
        btnLimpiarFirma= v.findViewById(R.id.btnLimpiarFirma);
        btnGuardarFirma= v.findViewById(R.id.btnGuardarFirma);
        btnActivarGuia = v.findViewById(R.id.btnActivarGuia);

        db = AppDatabase.getInstance(requireContext());

        btnTomarFoto.setOnClickListener(v1 -> stubSavePhoto());
        btnLimpiarFirma.setOnClickListener(v12 -> signatureView.clear());
        btnGuardarFirma.setOnClickListener(v13 -> guardarFirma());
        btnActivarGuia.setOnClickListener(v14 -> activarGuia());

        cargar();
    }

    private void cargar() {
        if (asignacionId <= 0) { toast("Id inválido"); return; }

        Executors.newSingleThreadExecutor().execute(() -> {
            current = db.asignacionDao().getById(asignacionId);
            final AsignacionDao.AsignacionDetalle det = db.asignacionDao().getDetalleById(asignacionId);

            runOnUi(() -> {
                if (current == null) { toast("No encontrada"); return; }

                tvHeader.setText("Asignación #" + current.id);
                tvEstado.setText("Estado: " + (nn(current.estado)));

                // Mostrar DETALLE del pedido
                if (det != null) {
                    String producto = nn(det.tipoProducto) + " " + nn(det.tamanoPaquete)
                            + " — " + nn(det.ciudadOrigen) + " → " + nn(det.ciudadDestino)
                            + "\n" + nn(det.direccion) + " • " + nn(det.horaDesde) + "–" + nn(det.horaHasta);
                    tvPedido.setText(producto);
                } else {
                    tvPedido.setText("—");
                }

                // Foto previa (si hubiera)
                if (!TextUtils.isEmpty(current.evidenciaFotoUri)) {
                    try { ivFoto.setImageURI(Uri.parse(current.evidenciaFotoUri)); } catch (Exception ignored) {}
                }

                // Si ya había firma guardada, deshabilita edición y oculta botón Guardar
                if (!TextUtils.isEmpty(current.firmaBase64)) {
                    lockSignature();
                }

                actualizarBotonGuia();
            });
        });
    }

    private void actualizarBotonGuia() {
        boolean okFoto  = current != null && !TextUtils.isEmpty(current.evidenciaFotoUri);
        boolean okFirma = current != null && !TextUtils.isEmpty(current.firmaBase64);
        btnActivarGuia.setEnabled(okFoto && okFirma && (current != null && !current.guiaActiva));
    }

    private void stubSavePhoto() {
        final String fakeUri = "content://evidencia/foto_" + asignacionId;
        Executors.newSingleThreadExecutor().execute(() -> {
            db.asignacionDao().guardarFoto(asignacionId, fakeUri);
            current = db.asignacionDao().getById(asignacionId);
            runOnUi(() -> {
                toast("Foto guardada");
                try { ivFoto.setImageURI(Uri.parse(fakeUri)); } catch (Exception ignored) {}
                actualizarBotonGuia();
            });
        });
    }

    private void guardarFirma() {
        Bitmap bmp = signatureView.exportBitmap();
        if (bmp == null) { toast("Dibuja la firma primero"); return; }
        String b64 = bitmapToBase64(bmp);

        Executors.newSingleThreadExecutor().execute(() -> {
            db.asignacionDao().guardarFirma(asignacionId, b64);
            current = db.asignacionDao().getById(asignacionId);
            runOnUi(() -> {
                toast("Firma guardada");
                lockSignature();       // <— congelar firma y ocultar botón
                actualizarBotonGuia();
            });
        });
    }

    private void lockSignature() {
        signatureView.setEnabled(false);
        btnGuardarFirma.setVisibility(View.GONE);
        // Puedes dejar “Limpiar” activo si quieres permitir rehacer antes de activar guía; si no:
        // btnLimpiarFirma.setVisibility(View.GONE);
    }

    private String bitmapToBase64(Bitmap bmp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        byte[] bytes = out.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private void activarGuia() {
        if (current == null) return;
        if (TextUtils.isEmpty(current.evidenciaFotoUri)) { toast("Falta foto"); return; }
        if (TextUtils.isEmpty(current.firmaBase64))     { toast("Falta firma"); return; }

        Executors.newSingleThreadExecutor().execute(() -> {
            db.asignacionDao().activarGuia(asignacionId);
            current = db.asignacionDao().getById(asignacionId);
            runOnUi(() -> {
                toast("Guía activada");
                tvEstado.setText("Estado: " + nn(current.estado));
                actualizarBotonGuia();
            });
        });
    }

    private void runOnUi(Runnable r) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(r);
    }

    private void toast(String t) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show();
    }

    private static String nn(String s) { return (s == null || s.trim().isEmpty()) ? "—" : s.trim(); }

    // Utilidad para firma
    private static String bitmapToBase64Static(Bitmap bmp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }
}
