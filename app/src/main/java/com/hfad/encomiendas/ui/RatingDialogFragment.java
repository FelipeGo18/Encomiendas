package com.hfad.encomiendas.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.hfad.encomiendas.R;
import com.hfad.encomiendas.data.AppDatabase;
import com.hfad.encomiendas.data.Rating;

import java.util.concurrent.Executors;

/**
 * DialogFragment simple para capturar calificación (1..5) y comentario opcional.
 */
public class RatingDialogFragment extends DialogFragment {

    private static final String ARG_SHIPMENT_ID = "shipmentId";
    private static final String ARG_REMITENTE_ID = "remitenteId";
    private static final String ARG_RECOLECTOR_ID = "recolectorId";

    public static RatingDialogFragment newInstance(long shipmentId, long remitenteId, @Nullable Integer recolectorId) {
        RatingDialogFragment f = new RatingDialogFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_SHIPMENT_ID, shipmentId);
        b.putLong(ARG_REMITENTE_ID, remitenteId);
        if (recolectorId != null) b.putInt(ARG_RECOLECTOR_ID, recolectorId);
        f.setArguments(b);
        return f;
    }

    private RatingBar ratingBar;
    private EditText etComment;
    private MaterialButton btnEnviar;
    private MaterialButton btnCancelar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_rating, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        ratingBar = v.findViewById(R.id.ratingBar);
        etComment = v.findViewById(R.id.etComment);
        btnEnviar = v.findViewById(R.id.btnEnviar);
        btnCancelar = v.findViewById(R.id.btnCancelar);

        btnCancelar.setOnClickListener(_v -> dismissAllowingStateLoss());
        btnEnviar.setOnClickListener(_v -> enviar());
    }

    private void enviar() {
        final float starsF = ratingBar.getRating();
        if (starsF < 0.5f) { toast("Selecciona de 1 a 5 estrellas"); return; }
        final int stars = Math.max(1, Math.round(starsF));
        final String comment = etComment.getText() == null ? "" : etComment.getText().toString().trim();
        final long shipmentId = getArguments() != null ? getArguments().getLong(ARG_SHIPMENT_ID, -1) : -1;
        final long remitenteId = getArguments() != null ? getArguments().getLong(ARG_REMITENTE_ID, -1) : -1;
        final Integer recolectorId = (getArguments() != null && getArguments().containsKey(ARG_RECOLECTOR_ID)) ? getArguments().getInt(ARG_RECOLECTOR_ID) : null;
        if (shipmentId <= 0 || remitenteId <= 0) { toast("Datos inválidos"); return; }

        btnEnviar.setEnabled(false);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                // Verificar si ya existe calificación
                if (db.ratingDao().byShipment(shipmentId) != null) {
                    runUi(() -> { toast("Ya existe calificación"); dismissAllowingStateLoss(); });
                    return;
                }

                // USAR EL SERVICIO DE RATING MANAGEMENT EN LUGAR DE CREAR MANUALMENTE
                com.hfad.encomiendas.core.RatingManagementService ratingService =
                    new com.hfad.encomiendas.core.RatingManagementService(db);

                // Usar el método createRating del servicio
                long ratingId = ratingService.createRating(
                    shipmentId,
                    remitenteId,
                    recolectorId,
                    stars,
                    TextUtils.isEmpty(comment) ? null : comment
                );

                runUi(() -> {
                    toast("Gracias por tu calificación (ID: " + ratingId + ")");
                    dismissAllowingStateLoss();
                });
            } catch (Exception e) {
                runUi(() -> { toast("Error: " + e.getMessage()); btnEnviar.setEnabled(true); });
            }
        });
    }

    private void runUi(Runnable r) { if (!isAdded()) return; requireActivity().runOnUiThread(r); }
    private void toast(String t) { if (!isAdded()) return; Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show(); }
}
