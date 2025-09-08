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
import android.widget.EditText;
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
import com.hfad.encomiendas.data.ManifiestoDao;
import com.hfad.encomiendas.data.ManifiestoItem;
import com.hfad.encomiendas.ui.widgets.SignatureView;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;

public class EntregaFragment extends Fragment {

    private static final String ARG_ITEM_ID = "manifiestoItemId";

    public static Bundle argsOf(int itemId) {
        Bundle b = new Bundle();
        b.putInt(ARG_ITEM_ID, itemId);
        return b;
    }

    private int itemId;

    private TextView tvGuia, tvDestino, tvEstado;
    private EditText etOtp;
    private MaterialButton btnValidarOtp;

    private ImageView ivFoto;
    private MaterialButton btnTomarFoto, btnPreviewFoto;
    private SignatureView signView;
    private MaterialButton btnGuardarFirma, btnLimpiar;

    // Estado
    private String otpEsperado = null;
    private String podFotoUri = null;
    private String firmaB64 = null;
    private boolean entregada = false;

    private ActivityResultLauncher<String> reqPermission;
    private ActivityResultLauncher<Uri> takePicture;
    private Uri pendingPhotoUri;

    public EntregaFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_entrega, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        itemId = getArguments()!=null ? getArguments().getInt(ARG_ITEM_ID, -1) : -1;

        reqPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), g -> {
            if (g) lanzarCamara(); else toast("Permiso cámara denegado");
        });
        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (ok && pendingPhotoUri != null) {
                ivFoto.setImageURI(pendingPhotoUri);
                podFotoUri = pendingPhotoUri.toString();
                guardarPodFoto(podFotoUri);
            } else {
                toast("No se tomó la foto");
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        tvGuia = v.findViewById(R.id.tvGuia);
        tvDestino = v.findViewById(R.id.tvDestino);
        tvEstado = v.findViewById(R.id.tvEstado);

        etOtp = v.findViewById(R.id.etOtp);
        btnValidarOtp = v.findViewById(R.id.btnValidarOtp);

        ivFoto = v.findViewById(R.id.ivFoto);
        btnTomarFoto = v.findViewById(R.id.btnTomarFoto);
        btnPreviewFoto = v.findViewById(R.id.btnPreviewFoto);

        signView = v.findViewById(R.id.signView);
        btnGuardarFirma = v.findViewById(R.id.btnGuardarFirma);
        btnLimpiar = v.findViewById(R.id.btnLimpiar);

        btnValidarOtp.setOnClickListener(v1 -> validarOtp());
        btnTomarFoto.setOnClickListener(v12 -> solicitarFoto());
        btnPreviewFoto.setOnClickListener(v13 -> previewFoto());
        btnGuardarFirma.setOnClickListener(v14 -> guardarFirma());
        btnLimpiar.setOnClickListener(v15 -> signView.clear());

        cargar();
    }

    private void cargar() {
        Executors.newSingleThreadExecutor().execute(() -> {
            ManifiestoDao dao = AppDatabase.getInstance(requireContext()).manifiestoDao();
            ManifiestoItem it = dao.getItem(itemId);
            if (it == null) {
                runOnUi(() -> toast("Item no encontrado"));
                return;
            }
            otpEsperado = it.otp;
            podFotoUri  = it.podFotoUri;     // nombres correctos
            firmaB64    = it.podFirmaB64;    // nombres correctos
            entregada   = "ENTREGADA".equalsIgnoreCase(it.estado);

            runOnUi(() -> {
                tvGuia.setText("Guía: " + (it.guia==null? "—": it.guia));
                tvDestino.setText("Destino: " + (it.destinoDireccion==null? "—" : it.destinoDireccion));
                tvEstado.setText(entregada ? "ENTREGADA" : it.estado);

                if (!TextUtils.isEmpty(podFotoUri)) {
                    ivFoto.setImageURI(Uri.parse(podFotoUri));
                }
                if (!TextUtils.isEmpty(firmaB64)) { // vista previa de firma si ya existe
                    signView.setEnabled(false);
                    signView.setAlpha(0.5f);
                    Bitmap bmp = decodeB64(firmaB64);
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Firma registrada")
                            .setView(preview(bmp))
                            .setPositiveButton("Cerrar", null)
                            .show();
                }
                updateEnabled();
            });
        });
    }

    private void validarOtp() {
        String val = etOtp.getText()==null ? "" : etOtp.getText().toString().trim();
        if (val.length() != 6) { toast("OTP de 6 dígitos"); return; }
        if (!val.equals(otpEsperado)) { toast("OTP incorrecto"); return; }
        marcarEntregadaSiListo();
    }

    private void solicitarFoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            reqPermission.launch(Manifest.permission.CAMERA);
        } else lanzarCamara();
    }

    private void lanzarCamara() {
        try {
            File dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir == null) dir = requireContext().getFilesDir();
            File f = new File(dir, "pod_" + itemId + "_" + System.currentTimeMillis() + ".jpg");
            if (!f.exists()) f.createNewFile();
            pendingPhotoUri = FileProvider.getUriForFile(requireContext(),
                    BuildConfig.APPLICATION_ID + ".fileprovider", f);
            takePicture.launch(pendingPhotoUri);
        } catch (IOException e) {
            toast("No se pudo crear la foto: " + e.getMessage());
        }
    }

    private void previewFoto() {
        if (TextUtils.isEmpty(podFotoUri)) { toast("Sin foto"); return; }
        ImageView iv = new ImageView(requireContext());
        iv.setAdjustViewBounds(true);
        iv.setImageURI(Uri.parse(podFotoUri));
        new AlertDialog.Builder(requireContext()).setView(iv).setPositiveButton("Cerrar", null).show();
    }

    private void guardarPodFoto(String uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(requireContext()).manifiestoDao().guardarPodFoto(itemId, uri);
        });
    }

    private void guardarFirma() {
        if (signView.isEmpty()) { toast("Dibuja la firma"); return; }
        final String b64 = signView.getBitmapBase64();
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(requireContext()).manifiestoDao().guardarPodFirma(itemId, b64);
            runOnUi(() -> {
                firmaB64 = b64;
                toast("Firma guardada");
                marcarEntregadaSiListo();
            });
        });
    }

    /** Reglas: basta OTP válido *o* firma. Foto es opcional. */
    private void marcarEntregadaSiListo() {
        boolean ok = (!TextUtils.isEmpty(firmaB64)) ||
                (etOtp.getText()!=null && etOtp.getText().toString().trim().equals(otpEsperado));
        if (!ok) { toast("Confirma OTP o firma primero"); return; }

        Executors.newSingleThreadExecutor().execute(() -> {
            ManifiestoDao dao = AppDatabase.getInstance(requireContext()).manifiestoDao();
            dao.marcarEntregada(itemId, System.currentTimeMillis());
            runOnUi(() -> {
                entregada = true;
                updateEnabled();
                tvEstado.setText("ENTREGADA");
                toast("Entrega registrada");
            });
        });
    }

    private void updateEnabled() {
        boolean enable = !entregada;
        etOtp.setEnabled(enable);
        btnValidarOtp.setEnabled(enable);
        btnTomarFoto.setEnabled(enable);

        signView.setEnabled(enable);
        btnGuardarFirma.setEnabled(enable);
        btnLimpiar.setEnabled(enable);

        float alpha = enable?1f:0.5f;
        btnValidarOtp.setAlpha(alpha);
        btnTomarFoto.setAlpha(alpha);
        btnGuardarFirma.setAlpha(alpha);
        btnLimpiar.setAlpha(alpha);
    }

    private Bitmap decodeB64(String b64) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) { return null; }
    }

    private ImageView preview(Bitmap bmp) {
        ImageView iv = new ImageView(requireContext());
        iv.setAdjustViewBounds(true);
        iv.setImageBitmap(bmp);
        return iv;
    }

    private void runOnUi(Runnable r){ if (!isAdded()) return; requireActivity().runOnUiThread(r); }
    private void toast(String s){ if (!isAdded()) return; Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show(); }
}