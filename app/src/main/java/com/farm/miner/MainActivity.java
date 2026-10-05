package com.farm.miner;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );

        setContentView(
                R.layout.activity_main
        );

        requestBatteryExemption();

        Intent serviceIntent =
                new Intent(
                        this,
                        MiningService.class
                );

        if (
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.O
        ) {

            startForegroundService(
                    serviceIntent
            );

        } else {

            startService(
                    serviceIntent
            );
        }

        if (
                savedInstanceState == null
        ) {

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.fragment_container,
                            new DashboardFragment()
                    )
                    .commit();
        }
    }

    private void requestBatteryExemption() {

        if (
                Build.VERSION.SDK_INT
                        < Build.VERSION_CODES.M
        ) {

            return;
        }

        PowerManager powerManager =
                (PowerManager)
                        getSystemService(
                                Context.POWER_SERVICE
                        );

        if (powerManager == null) {
            return;
        }

        if (
                powerManager.isIgnoringBatteryOptimizations(
                        getPackageName()
                )
        ) {

            return;
        }

        try {

            Intent intent =
                    new Intent(
                            Settings
                                    .ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    );

            intent.setData(
                    Uri.parse(
                            "package:"
                                    + getPackageName()
                    )
            );

            startActivity(intent);

        } catch (Exception ignored) {

            /*
             * Certains fabricants bloquent cette demande.
             * L'application continue à fonctionner sans
             * cette exemption.
             */
        }
    }
}
