package com.farm.miner;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

public class DashboardFragment extends Fragment {

    // Variables Gamification
    private double currentBtcBalance = 0.0000000001;
    private final double baseMiningSpeed = 0.0000000005; 
    private int currentEnergyPercent = 100;
    private int currentXP = 320;
    private final double MINIMUM_WITHDRAWAL = 0.005;

    private TextView tvBtcCounter, tvEnergyPercent, tvXP;
    private View btnTapToMine, btnWithdraw;
    private ViewGroup rootView;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = (ViewGroup) inflater.inflate(R.layout.fragment_dashboard, container, false);
        
        tvBtcCounter = rootView.findViewById(R.id.tvBtcCounter);
        tvEnergyPercent = rootView.findViewById(R.id.tvEnergyPercent);
        tvXP = rootView.findViewById(R.id.tvXP);
        btnTapToMine = rootView.findViewById(R.id.btnTapToMine);
        btnWithdraw = rootView.findViewById(R.id.btnWithdraw);

        setupListeners();
        startAutoMiningVisuals();
        startEnergyRegen();

        return rootView;
    }

    private void setupListeners() {
        // Bouton TAP TO MINE
        btnTapToMine.setOnClickListener(v -> {
            if (currentEnergyPercent > 0) {
                currentEnergyPercent -= 1; // Coût du clic
                currentBtcBalance += (baseMiningSpeed * 5); // Bonus de clic
                currentXP += 2; // Ajout de XP
                
                updateDisplays();
                showFloatingXPAnimation(v);
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } else {
                Toast.makeText(getContext(), "Énergie épuisée, patientez...", Toast.LENGTH_SHORT).show();
            }
        });

        // Bouton RETRAIT
        btnWithdraw.setOnClickListener(v -> {
            if (currentBtcBalance < MINIMUM_WITHDRAWAL) {
                showMinimumNotReachedModal();
            }
        });
    }

    private void startAutoMiningVisuals() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                currentBtcBalance += baseMiningSpeed;
                updateDisplays();
                mainHandler.postDelayed(this, 1000); // Boucle chaque seconde
            }
        }, 1000);
    }

    private void startEnergyRegen() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentEnergyPercent < 100) {
                    currentEnergyPercent++;
                    updateDisplays();
                }
                mainHandler.postDelayed(this, 36000); // +1% toutes les 36s (100% en 1h)
            }
        }, 36000);
    }

    private void updateDisplays() {
        tvBtcCounter.setText(String.format(java.util.Locale.US, "%.10f BTC", currentBtcBalance));
        tvEnergyPercent.setText(currentEnergyPercent + "%");
        tvXP.setText(currentXP + " XP");
    }

    private void showFloatingXPAnimation(View anchorView) {
        TextView floatingXP = new TextView(getContext());
        floatingXP.setText("+2 XP");
        floatingXP.setTextColor(Color.parseColor("#00F5D4")); // Néon Cyan
        floatingXP.setTextSize(18f);
        
        // Positionnement au-dessus du bouton
        floatingXP.setX(anchorView.getX() + (anchorView.getWidth() / 2f) - 30);
        floatingXP.setY(anchorView.getY() - 50);
        
        rootView.addView(floatingXP);
        
        // Animation de montée et disparition
        floatingXP.animate()
                .translationYBy(-100f)
                .alpha(0f)
                .setDuration(800)
                .withEndAction(() -> rootView.removeView(floatingXP))
                .start();
    }

    private void showMinimumNotReachedModal() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Retrait impossible")
                .setMessage("Le solde minimum de retrait (" + MINIMUM_WITHDRAWAL + " BTC) n'est pas encore atteint.")
                .setPositiveButton("Compris", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
