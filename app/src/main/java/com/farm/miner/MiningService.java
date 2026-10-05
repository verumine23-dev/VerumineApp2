package com.farm.miner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

public class MiningService extends Service {

    private static final String TAG = "VerumineMining";
    private static final String CHANNEL_ID = "VerumineServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String BINARY_NAME = "ccminer-arm64";

    private static final String POOL_URL = "stratum+tcp://ap.luckpool.net:3956";
    private static final String WALLET_ADDRESS = "RWZf58DwQ9R3QkdXVGtv2PGBYhc5qEfyxx";

    private Process minerProcess;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean stopping;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Verumine::MiningLock"
            );
            wakeLock.acquire();
        }

        Log.i(TAG, "MiningService créé");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification("Préparation du mineur...");
        startForeground(NOTIFICATION_ID, notification);

        stopping = false;
        startMining();

        return START_STICKY;
    }

    private void startMining() {
        synchronized (this) {
            if (minerProcess != null && minerProcess.isAlive()) {
                Log.i(TAG, "ccminer est déjà en cours d'exécution");
                return;
            }
        }

        new Thread(() -> {
            try {
                if (!isArm64Supported()) {
                    failMining("Téléphone incompatible : ccminer utilisé est ARM64");
                    return;
                }

                File binaryFile = prepareMinerBinary();
                if (!binaryFile.exists() || binaryFile.length() == 0) {
                    failMining("ccminer introuvable après extraction");
                    return;
                }

                if (!binaryFile.setExecutable(true, false)) {
                    Log.w(TAG, "Impossible de modifier directement le bit executable");
                }

                String deviceId = Settings.Secure.getString(
                        getContentResolver(), Settings.Secure.ANDROID_ID
                );
                String suffix = deviceId == null ? "Android" :
                        deviceId.substring(0, Math.min(deviceId.length(), 6));
                String workerName = "Node_" + suffix;
                int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

                String username = WALLET_ADDRESS + "." + workerName;

                Log.i(TAG, "========================================");
                Log.i(TAG, "DÉMARRAGE CCMINER");
                Log.i(TAG, "Binaire: " + binaryFile.getAbsolutePath());
                Log.i(TAG, "Pool: " + POOL_URL);
                Log.i(TAG, "Worker: " + workerName);
                Log.i(TAG, "Threads: " + threads);
                Log.i(TAG, "========================================");

                updateNotification("Connexion à LuckPool... Worker: " + workerName);

                ProcessBuilder pb = new ProcessBuilder(
                        binaryFile.getAbsolutePath(),
                        "-a", "verus",
                        "-o", POOL_URL,
                        "-u", username,
                        "-p", "x",
                        "-t", String.valueOf(threads)
                );
                pb.directory(getFilesDir());
                pb.redirectErrorStream(true);

                synchronized (this) {
                    if (stopping) return;
                    minerProcess = pb.start();
                }

                Log.i(TAG, "ccminer démarré, PID/process actif");
                updateNotification("Minage Verus actif • " + workerName);

                readMinerOutput(minerProcess.getInputStream());

                int exitCode = minerProcess.waitFor();
                Log.w(TAG, "ccminer arrêté. exitCode=" + exitCode);

                synchronized (this) {
                    minerProcess = null;
                }

                if (!stopping) {
                    updateNotification("ccminer arrêté (code " + exitCode + ")");
                }

            } catch (Exception e) {
                Log.e(TAG, "Erreur pendant le démarrage du minage", e);
                failMining("Erreur ccminer : " + e.getClass().getSimpleName());
            }
        }, "VerumineMinerStarter").start();
    }

    private File prepareMinerBinary() throws IOException {
        File destination = new File(getFilesDir(), BINARY_NAME);

        if (destination.exists() && destination.length() > 0) {
            Log.i(TAG, "ccminer déjà présent dans files: " + destination.getAbsolutePath());
            return destination;
        }

        Log.i(TAG, "Extraction de ccminer depuis assets...");

        try (InputStream input = getAssets().open(BINARY_NAME);
             FileOutputStream output = new FileOutputStream(destination)) {

            byte[] buffer = new byte[8192];
            int count;
            long total = 0;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                total += count;
            }
            output.flush();
            Log.i(TAG, "ccminer extrait: " + total + " octets");
        }

        if (!destination.setExecutable(true, false)) {
            Log.w(TAG, "setExecutable() a retourné false");
        }

        return destination;
    }

    private void readMinerOutput(InputStream stream) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.i(TAG, "ccminer: " + line);
                    String lower = line.toLowerCase();
                    if (lower.contains("accepted") || lower.contains("stratum") ||
                            lower.contains("connected") || lower.contains("share")) {
                        updateNotification("LuckPool: " + shorten(line));
                    }
                }
            } catch (IOException e) {
                if (!stopping) {
                    Log.e(TAG, "Erreur lecture sortie ccminer", e);
                }
            }
        }, "VerumineMinerOutput").start();
    }

    private boolean isArm64Supported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) return true;
        }
        Log.e(TAG, "ABIs supportées: " + java.util.Arrays.toString(Build.SUPPORTED_ABIS));
        return false;
    }

    private void failMining(String message) {
        Log.e(TAG, message);
        updateNotification(message);
    }

    private String shorten(String value) {
        if (value == null) return "";
        value = value.trim();
        return value.length() > 90 ? value.substring(0, 90) + "..." : value;
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Verumine — Minage Verus")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    @Override
    public void onDestroy() {
        stopping = true;
        synchronized (this) {
            if (minerProcess != null) {
                minerProcess.destroy();
                minerProcess = null;
            }
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        Log.i(TAG, "MiningService détruit");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Verumine Mining",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("État du minage Verus de Verumine");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
