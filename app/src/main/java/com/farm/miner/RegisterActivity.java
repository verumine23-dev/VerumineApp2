package com.farm.miner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register); // (Suppose un layout similaire à login)

        // Initialisation fictive des champs (nom, email, tel, pass)
        Button btnRegister = findViewById(R.id.btnRegister); // ID supposé

        btnRegister.setOnClickListener(v -> {
            // Dans une version sans backend, on simule l'inscription
            SharedPreferences prefs = getSharedPreferences("VeruminePrefs", MODE_PRIVATE);
            prefs.edit().putBoolean("isLoggedIn", true).apply();
            
            Toast.makeText(this, "Compte créé avec succès ! Initialisation du nœud...", Toast.LENGTH_LONG).show();
            
            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}