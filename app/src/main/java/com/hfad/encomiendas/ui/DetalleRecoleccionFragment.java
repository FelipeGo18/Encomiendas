package com.hfad.encomiendas.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.hfad.encomiendas.BuildConfig;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Asignacion;
import com.hfad.encomiendas.data.AsignacionDao;
import com.hfad.encomiendas.ui.widgets.SignatureView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class DetalleRecoleccionFragment extends Fragment {

    private static final String ARG_ID = "asignacionId";

    private int asignacionId;

    // UI
    private TextView tvTitulo, tvSub, tvEstado;
    private ImageView ivFoto;
    private MaterialButton btnTomarFoto, btnEditarFoto, btnConfirmarFoto, btnSoloEditar;
    private View llEditarConfirmar, llSoloEditar;

    private ImageView ivFirmaPreview;
    private SignatureView signView;
    private MaterialButton btnGuardarFirma, btnLimpiar;

    // Estado DB
    private boolean guiaActiva = false;
    private Integer ordenRuta = null;
    private String estado = "";

    // Foto
    private Uri pendingPhotoUri = null;    // tomada pero no confirmada
    private String savedPhotoUri = null;   // confirmada (DB)

    // Firma
    private String firmaB64 = null;

    // Cámara/permiso
    private ActivityResultLauncher<String> reqPermission;
    private ActivityResultLauncher<Uri> takePicture;

    public DetalleRecoleccionFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detalle_recoleccion, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        asignacionId = getArguments() != null ? getArguments().getInt(ARG_ID, -1) : -1;

        reqPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) lanzarCamara();
            else toast("Permiso de cámara denegado");
        });

        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (ok && pendingPhotoUri != null) {
                ivFoto.setImageURI(pendingPhotoUri);
                updatePhotoButtons();
            } else {
                pendingPhotoUri = null;
                toast("No se tomó la foto");
                updatePhotoButtons();
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        tvTitulo = v.findViewById(R.id.tvTitulo);
        tvSub    = v.findViewById(R.id.tvSub);
        tvEstado = v.findViewById(R.id.tvEstado);

        ivFoto = v.findViewById(R.id.ivFoto);
        btnTomarFoto      = v.findViewById(R.id.btnTomarFoto);
        llEditarConfirmar = v.findViewById(R.id.llEditarConfirmar);
        btnEditarFoto     = v.findViewById(R.id.btnEditarFoto);
        btnConfirmarFoto  = v.findViewById(R.id.btnConfirmarFoto);
        llSoloEditar      = v.findViewById(R.id.llSoloEditar);
        btnSoloEditar     = v.findViewById(R.id.btnSoloEditar);

        ivFirmaPreview  = v.findViewById(R.id.ivFirmaPreview);
        signView        = v.findViewById(R.id.signView);
        btnGuardarFirma = v.findViewById(R.id.btnGuardarFirma);
        btnLimpiar      = v.findViewById(R.id.btnLimpiar);

        ivFoto.setOnClickListener(view -> showPreview());

        btnTomarFoto.setOnClickListener(view -> onTomarFoto());
        btnEditarFoto.setOnClickListener(view -> onTomarFoto());
        btnSoloEditar.setOnClickListener(view -> onTomarFoto());
        btnEditarFoto.setOnLongClickListener(v1 -> { borrarFoto(); return true; });
        btnSoloEditar.setOnLongClickListener(v12 -> { borrarFoto(); return true; });

        btnGuardarFirma.setOnClickListener(view -> onGuardarFirma());
        btnLimpiar.setOnClickListener(view -> signView.clear());

        cargarDetalle();
    }

    /* ===== Carga de datos ===== */
    private void cargarDetalle() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                AsignacionDao.AsignacionDetalle d = db.asignacionDao().getDetalleById(asignacionId);
                Asignacion a = db.asignacionDao().getById(asignacionId);

                ordenRuta  = (d != null) ? d.ordenRuta : null;
                estado     = (a != null && !TextUtils.isEmpty(a.estado)) ? a.estado : "—";
                guiaActiva = (a != null && a.guiaActiva);

                savedPhotoUri = (a != null) ? a.evidenciaFotoUri : null;
                pendingPhotoUri = (savedPhotoUri == null) ? null : Uri.parse(savedPhotoUri);

                firmaB64 = (a != null) ? a.firmaBase64 : null;

                runOnUi(() -> {
                    tvTitulo.setText("#" + asignacionId + " • " + estado);
                    tvSub.setText("Orden: " + (ordenRuta == null ? "—" : ordenRuta));
                    tvEstado.setText(guiaActiva ? "GUÍA ACTIVADA (RECOLECTADA)" : "");

                    if (!TextUtils.isEmpty(savedPhotoUri)) {
                        try { ivFoto.setImageURI(Uri.parse(savedPhotoUri)); } catch (Exception ignore) {}
                    }

                    updatePhotoButtons();
                    updateSignatureSection();  // <<-- mostrar firma si existe
                    updateUiEnabled();
                });

            } catch (Exception e) {
                runOnUi(() -> toast("Error cargando: " + e.getMessage()));
            }
        });
    }

    /* ===== Foto ===== */
    private void onTomarFoto() {
        if (guiaActiva) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            reqPermission.launch(Manifest.permission.CAMERA);
        } else {
            lanzarCamara();
        }
    }

    private void lanzarCamara() {
        try {
            File dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir == null) dir = requireContext().getFilesDir();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File photo = new File(dir, "evid_" + asignacionId + "_" + ts + ".jpg");
            if (!photo.exists()) photo.createNewFile();

            pendingPhotoUri = FileProvider.getUriForFile(
                    requireContext(),
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    photo
            );
            takePicture.launch(pendingPhotoUri);
        } catch (IOException e) {
            toast("No se pudo crear la foto: " + e.getMessage());
        }
    }

    private void onConfirmarFoto() {
        if (pendingPhotoUri == null) { toast("Toma una foto primero"); return; }
        savedPhotoUri = pendingPhotoUri.toString();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase.getInstance(requireContext())
                        .asignacionDao().guardarFoto(asignacionId, savedPhotoUri);

                runOnUi(() -> {
                    toast("Foto confirmada");
                    updatePhotoButtons();
                    verificarYActivarGuia();
                });
            } catch (Exception e) {
                runOnUi(() -> toast("Error guardando foto: " + e.getMessage()));
            }
        });
    }

    private void borrarFoto() {
        if (guiaActiva) return;
        pendingPhotoUri = null;
        savedPhotoUri = null;
        ivFoto.setImageDrawable(null);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase.getInstance(requireContext())
                        .asignacionDao().guardarFoto(asignacionId, null);
            } catch (Exception ignore) {}
        });
        updatePhotoButtons();
    }

    private void updatePhotoButtons() {
        boolean hayPendiente = (pendingPhotoUri != null) &&
                (savedPhotoUri == null || !pendingPhotoUri.toString().equals(savedPhotoUri));
        boolean hayConfirmada = !TextUtils.isEmpty(savedPhotoUri);

        btnTomarFoto.setVisibility((!hayPendiente && !hayConfirmada) ? View.VISIBLE : View.GONE);
        llEditarConfirmar.setVisibility(hayPendiente ? View.VISIBLE : View.GONE);
        llSoloEditar.setVisibility((!hayPendiente && hayConfirmada) ? View.VISIBLE : View.GONE);
    }

    private void showPreview() {
        Uri toShow = (pendingPhotoUri != null) ? pendingPhotoUri :
                (!TextUtils.isEmpty(savedPhotoUri) ? Uri.parse(savedPhotoUri) : null);
        if (toShow == null) return;

        ImageView iv = new ImageView(requireContext());
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageURI(toShow);

        new AlertDialog.Builder(requireContext())
                .setView(iv)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    /* ===== Firma & Guía ===== */
    private void onGuardarFirma() {
        if (guiaActiva) return;
        if (signView.isEmpty()) { toast("Dibuja la firma primero"); return; }

        final String b64 = signView.getBitmapBase64();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                db.asignacionDao().guardarFirma(asignacionId, b64);
                runOnUi(() -> {
                    firmaB64 = b64;
                    toast("Firma guardada");
                    updateSignatureSection();
                });
                verificarYActivarGuia();
            } catch (Exception e) {
                runOnUi(() -> toast("Error guardando firma: " + e.getMessage()));
            }
        });
    }

    private void verificarYActivarGuia() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                Asignacion a = db.asignacionDao().getById(asignacionId);

                boolean hayFoto  = (a != null && !TextUtils.isEmpty(a.evidenciaFotoUri));
                boolean hayFirma = (a != null && !TextUtils.isEmpty(a.firmaBase64));

                if (hayFoto && hayFirma && (a != null) && !a.guiaActiva) {
                    db.asignacionDao().activarGuia(asignacionId);
                    db.solicitudDao().marcarRecolectadaPorAsignacion(asignacionId);

                    runOnUi(() -> {
                        guiaActiva = true;
                        tvEstado.setText("GUÍA ACTIVADA (RECOLECTADA)");
                        updateUiEnabled();
                        updateSignatureSection();
                    });
                }
            } catch (Exception e) {
                runOnUi(() -> toast("Error activando guía: " + e.getMessage()));
            }
        });
    }

    /* ===== UI helpers ===== */
    private void updateSignatureSection() {
        boolean hasFirma = !TextUtils.isEmpty(firmaB64);

        if (hasFirma) {
            Bitmap bmp = decodeB64(firmaB64);
            ivFirmaPreview.setImageBitmap(bmp);
            ivFirmaPreview.setVisibility(View.VISIBLE);

            signView.setVisibility(View.GONE);
            btnGuardarFirma.setVisibility(View.GONE);
            btnLimpiar.setVisibility(View.GONE);
        } else {
            ivFirmaPreview.setVisibility(View.GONE);

            signView.setVisibility(View.VISIBLE);
            btnGuardarFirma.setVisibility(View.VISIBLE);
            btnLimpiar.setVisibility(View.VISIBLE);
        }

        // si la guía está activa o ya hay firma => no editable
        boolean enableDrawing = !guiaActiva && !hasFirma;
        signView.setEnabled(enableDrawing);
        btnGuardarFirma.setEnabled(enableDrawing);
        btnLimpiar.setEnabled(enableDrawing);
        signView.setAlpha(enableDrawing ? 1f : 0.5f);
        btnGuardarFirma.setAlpha(enableDrawing ? 1f : 0.5f);
        btnLimpiar.setAlpha(enableDrawing ? 1f : 0.5f);
    }

    private Bitmap decodeB64(String b64) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateUiEnabled() {
        boolean enabled = !guiaActiva;

        ivFoto.setClickable(true); // seguimos permitiendo vista previa
        btnTomarFoto.setEnabled(enabled);
        btnEditarFoto.setEnabled(enabled);
        btnConfirmarFoto.setEnabled(enabled);
        btnSoloEditar.setEnabled(enabled);

        float alpha = enabled ? 1f : 0.5f;
        btnTomarFoto.setAlpha(alpha);
        btnEditarFoto.setAlpha(alpha);
        btnConfirmarFoto.setAlpha(alpha);
        btnSoloEditar.setAlpha(alpha);
    }

    private void runOnUi(Runnable r) { if (!isAdded()) return; requireActivity().runOnUiThread(r); }
    private void toast(String s) { if (!isAdded()) return; Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show(); }
}