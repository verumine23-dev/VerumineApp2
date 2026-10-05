package com.farm.miner;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private View logoV;
    private TextView textVerumine;
    private ProgressBar progressBar;
    private TextView textProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        logoV = findViewById(R.id.logoV);
        textVerumine = findViewById(R.id.textVerumine);
        progressBar = findViewById(R.id.progressBar);
        textProgress = findViewById(R.id.textProgress);

        startAnimationSequence();
    }

    private void startAnimationSequence() {
        Handler handler = new Handler(Looper.getMainLooper());

        // Étape 1 : Apparition du Logo
        logoV.setAlpha(0f);
        logoV.animate().alpha(1f).setDuration(1500).start();

        // Étape 2 & 3 : Réduction et Nom
        handler.postDelayed(() -> {
            logoV.animate().translationY(-100f).scaleX(0.8f).scaleY(0.8f).setDuration(1000).start();
            textVerumine.setVisibility(View.VISIBLE);
            textVerumine.setAlpha(0f);
            textVerumine.animate().alpha(1f).translationY(-50f).setDuration(1000).start();
        }, 1500);

        // Étape 4 - 7 : Barre de chargement
        handler.postDelayed(() -> {
            progressBar.setVisibility(View.VISIBLE);
            textProgress.setVisibility(View.VISIBLE);
            animateProgress();
        }, 2500);

        // Étape 8 : Transition vers l'application
        handler.postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 7000);
    }

    private void animateProgress() {
        ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100);
        animator.setDuration(4500);
        animator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();
            textProgress.setText(progress + "%");
        });
        animator.start();
    }
}