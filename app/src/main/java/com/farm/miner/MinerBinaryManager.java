package com.farm.miner;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Gestionnaire multi-ABI pour le mineur natif Verumine.
 *
 * Principe :
 * 1) détecter l'ABI réellement supportée par Android ;
 * 2) essayer uniquement les binaires correspondant à cette ABI ;
 * 3) extraire le binaire hors des assets ;
 * 4) vérifier sa taille et son SHA-256 lorsqu'il est connu ;
 * 5) faire un "smoke test" (-h) avant de lancer le minage ;
 * 6) passer au fallback suivant si le binaire ne démarre pas.
 *
 * Les assets sont générés par GitHub Actions dans :
 * assets/miners/<abi>/<variant>
 */
public final class MinerBinaryManager {

    private static final String TAG = "VerumineMinerABI";

    private static final int SMOKE_TEST_TIMEOUT_MS = 7000;

    private final Context context;
    private final AssetManager assets;

    public MinerBinaryManager(Context context) {
        this.context = context.getApplicationContext();
        this.assets = this.context.getAssets();
    }

    public static final class PreparedMiner {

        private final File file;
        private final String abi;
        private final String variant;
        private final String smokeOutput;

        PreparedMiner(
                File file,
                String abi,
                String variant,
                String smokeOutput
        ) {
            this.file = file;
            this.abi = abi;
            this.variant = variant;
            this.smokeOutput = smokeOutput;
        }

        public File getFile() {
            return file;
        }

        public String getAbi() {
            return abi;
        }

        public String getVariant() {
            return variant;
        }

        public String getSmokeOutput() {
            return smokeOutput;
        }
    }

    private static final class Candidate {

        final String assetPath;
        final String variant;
        final String expectedSha256;

        Candidate(
                String assetPath,
                String variant,
                String expectedSha256
        ) {
            this.assetPath = assetPath;
            this.variant = variant;
            this.expectedSha256 = expectedSha256;
        }
    }

    /**
     * Renvoie l'ABI primaire réellement utilisée par Android.
     */
    public String getPrimaryAbi() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            String[] abis = Build.SUPPORTED_ABIS;

            if (abis != null) {

                for (String abi : abis) {

                    if (isKnownAbi(abi)) {
                        return abi;
                    }
                }
            }
        }

        /*
         * Compatibilité anciens Android.
         */
        String legacy = Build.CPU_ABI;

        if (isKnownAbi(legacy)) {
            return legacy;
        }

        return "unknown";
    }

    /**
     * Liste des ABI Android disponibles sur le téléphone.
     */
    public List<String> getSupportedAbis() {

        List<String> result = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            if (Build.SUPPORTED_ABIS != null) {
                result.addAll(
                        Arrays.asList(Build.SUPPORTED_ABIS)
                );
            }

        } else if (Build.CPU_ABI != null) {

            result.add(Build.CPU_ABI);
        }

        List<String> normalized = new ArrayList<>();

        for (String abi : result) {

            if (abi != null
                    && !abi.trim().isEmpty()
                    && !normalized.contains(abi)) {

                normalized.add(abi);
            }
        }

        return normalized;
    }

    public String getHumanReadableAbi() {

        String abi = getPrimaryAbi();

        switch (abi) {

            case "arm64-v8a":
                return "ARM64 (arm64-v8a)";

            case "armeabi-v7a":
                return "ARM 32 bits (armeabi-v7a)";

            case "x86_64":
                return "x86 64 bits (x86_64)";

            case "x86":
                return "x86 32 bits (x86)";

            default:
                return "ABI inconnue (" + abi + ")";
        }
    }

    /**
     * Prépare et teste le premier mineur réellement exécutable.
     */
    public PreparedMiner prepareBestMiner()
            throws Exception {

        String abi = getPrimaryAbi();

        if ("unknown".equals(abi)) {

            throw new IOException(
                    "ABI Android inconnue. ABI supportées annoncées : "
                            + getSupportedAbis()
            );
        }

        List<Candidate> candidates =
                getCandidatesForAbi(abi);

        if (candidates.isEmpty()) {

            throw new IOException(
                    "Aucun mineur Android prévu pour l'ABI "
                            + abi
                            + ". ABI détectées : "
                            + getSupportedAbis()
            );
        }

        Exception lastError = null;

        for (Candidate candidate : candidates) {

            try {

                Log.i(
                        TAG,
                        "Tentative mineur : ABI="
                                + abi
                                + " variant="
                                + candidate.variant
                                + " asset="
                                + candidate.assetPath
                );

                File extracted =
                        extractCandidate(
                                candidate,
                                abi
                        );

                SmokeTestResult smoke =
                        smokeTest(extracted);

                if (!smoke.success) {

                    throw new IOException(
                            "Smoke test échoué pour "
                                    + candidate.variant
                                    + ". Sortie : "
                                    + smoke.output
                    );
                }

                Log.i(
                        TAG,
                        "Mineur validé : "
                                + extracted.getAbsolutePath()
                                + " ("
                                + candidate.variant
                                + ")"
                );

                return new PreparedMiner(
                        extracted,
                        abi,
                        candidate.variant,
                        smoke.output
                );

            } catch (Exception e) {

                lastError = e;

                Log.w(
                        TAG,
                        "Échec du candidat "
                                + candidate.variant
                                + " : "
                                + e.getMessage()
                );
            }
        }

        throw new IOException(
                "Aucun binaire compatible n'a réussi le test pour "
                        + abi
                        + ". Dernière erreur : "
                        + (
                        lastError != null
                                ? lastError.getMessage()
                                : "inconnue"
                )
        );
    }

    private List<Candidate> getCandidatesForAbi(
            String abi
    ) {

        switch (abi) {

            case "arm64-v8a":

                /*
                 * Ordre de priorité :
                 *
                 * 1. A53 optimisé
                 * 2. A55 optimisé
                 * 3. ancienne version ARM
                 */
                return Arrays.asList(

                        new Candidate(
                                "miners/arm64-v8a/ccminer-a53",
                                "a53",
                                "56f71fa313d5f942283ea1d8f02f679088c612f08607928f33612d4f387fb004"
                        ),

                        new Candidate(
                                "miners/arm64-v8a/ccminer-a55",
                                "a55",
                                "d3b182994e499dd4957ebeead25565c382b7e6b6efa4a017533d9b22f0d83f7c"
                        ),

                        new Candidate(
                                "miners/arm64-v8a/ccminer-legacy",
                                "legacy-arm",
                                "b9f2307e990779b8ea11808f1ccfed2f9fb103b75d67ad55ea9416e7ffe569cf"
                        )
                );

            case "armeabi-v7a":

                /*
                 * Emplacement prévu pour le vrai binaire
                 * Android ARMv7.
                 *
                 * Aucun binaire public vérifié n'est
                 * inventé ici.
                 */
                return Collections.singletonList(

                        new Candidate(
                                "miners/armeabi-v7a/ccminer-armv7",
                                "armv7",
                                null
                        )
                );

            case "x86_64":

                /*
                 * Emplacement réservé au vrai binaire
                 * Android x86_64.
                 *
                 * IMPORTANT :
                 * ne jamais utiliser ici un binaire Ubuntu/glibc.
                 */
                return Collections.singletonList(

                        new Candidate(
                                "miners/x86_64/ccminer",
                                "x86_64",
                                null
                        )
                );

            case "x86":

                /*
                 * Emplacement réservé au vrai binaire
                 * Android x86.
                 */
                return Collections.singletonList(

                        new Candidate(
                                "miners/x86/ccminer",
                                "x86",
                                null
                        )
                );

            default:

                return Collections.emptyList();
        }
    }

    private File extractCandidate(
            Candidate candidate,
            String abi
    ) throws Exception {

        File baseDir =
                new File(
                        context.getFilesDir(),
                        "miners"
                                + File.separator
                                + abi
                );

        if (!baseDir.exists()
                && !baseDir.mkdirs()) {

            throw new IOException(
                    "Impossible de créer le dossier : "
                            + baseDir.getAbsolutePath()
            );
        }

        String safeVariant =
                candidate.variant.replaceAll(
                        "[^a-zA-Z0-9._-]",
                        "_"
                );

        File target =
                new File(
                        baseDir,
                        "ccminer-" + safeVariant
                );

        /*
         * Réextraction systématique.
         * Cela évite de conserver un ancien binaire
         * après une mise à jour.
         */
        if (target.exists()
                && !target.delete()) {

            throw new IOException(
                    "Impossible de supprimer l'ancien binaire : "
                            + target.getAbsolutePath()
            );
        }

        try (
                InputStream input =
                        assets.open(candidate.assetPath);

                FileOutputStream output =
                        new FileOutputStream(target)
        ) {

            byte[] buffer = new byte[16 * 1024];

            int count;

            while ((count = input.read(buffer)) != -1) {

                output.write(
                        buffer,
                        0,
                        count
                );
            }

            output.flush();
        }

        if (!target.exists()
                || target.length() < 100 * 1024) {

            throw new IOException(
                    "Binaire extrait invalide ou incomplet : "
                            + target.length()
                            + " octets"
            );
        }

        /*
         * Vérification cryptographique pour les
         * binaires dont le SHA256 est connu.
         */
        if (candidate.expectedSha256 != null) {

            String actual =
                    sha256(target);

            if (!candidate.expectedSha256.equalsIgnoreCase(
                    actual
            )) {

                if (!target.delete()) {

                    Log.w(
                            TAG,
                            "Impossible de supprimer "
                                    + "le binaire au mauvais hash."
                    );
                }

                throw new SecurityException(
                        "SHA-256 invalide pour "
                                + candidate.variant
                                + ". Attendu="
                                + candidate.expectedSha256
                                + ", obtenu="
                                + actual
                );
            }
        }

        if (!target.setExecutable(true, false)) {

            Log.w(
                    TAG,
                    "setExecutable(false) a échoué. "
                            + "Tentative de lancement quand même."
            );
        }

        return target;
    }

    private static final class SmokeTestResult {

        final boolean success;
        final String output;

        SmokeTestResult(
                boolean success,
                String output
        ) {
            this.success = success;
            this.output = output;
        }
    }

    /**
     * Teste le binaire avant tout lancement réel de minage.
     *
     * -h doit afficher l'aide puis terminer.
     *
     * Un mauvais ELF / mauvaise architecture / linker
     * cassé est ainsi détecté avant connexion au pool.
     */
    private SmokeTestResult smokeTest(
            File binary
    ) throws Exception {

        Process process = null;

        try {

            ProcessBuilder builder =
                    new ProcessBuilder(
                            binary.getAbsolutePath(),
                            "-h"
                    );

            builder.directory(
                    context.getFilesDir()
            );

            builder.redirectErrorStream(true);

            process = builder.start();

            StringBuilder output =
                    new StringBuilder();

            long deadline =
                    SystemClock.elapsedRealtime()
                            + SMOKE_TEST_TIMEOUT_MS;

            boolean finished = false;
            int exitCode = -1;

            while (
                    SystemClock.elapsedRealtime()
                            < deadline
            ) {

                try {

                    exitCode =
                            process.exitValue();

                    finished = true;
                    break;

                } catch (
                        IllegalThreadStateException
                                notFinished
                ) {

                    Thread.sleep(100);
                }
            }

            if (!finished) {

                process.destroy();

                return new SmokeTestResult(
                        false,
                        "Timeout du smoke test"
                );
            }

            try (
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            process.getInputStream(),
                                            StandardCharsets.UTF_8
                                    )
                            )
            ) {

                String line;

                int chars = 0;

                while (
                        (line = reader.readLine()) != null
                                && chars < 12000
                ) {

                    output
                            .append(line)
                            .append('\n');

                    chars +=
                            line.length() + 1;
                }
            }

            String text =
                    output.toString().trim();

            String lower =
                    text.toLowerCase();

            boolean architectureError =
                    lower.contains(
                            "exec format error"
                    )
                            || lower.contains(
                            "wrong architecture"
                    )
                            || lower.contains(
                            "cannot execute"
                    )
                            || lower.contains(
                            "not executable"
                    )
                            || lower.contains(
                            "bad cpu type"
                    )
                            || lower.contains(
                            "no such file or directory"
                    )
                            || lower.contains(
                            "cannot locate symbol"
                    )
                            || lower.contains(
                            "linker:"
                    );

            boolean looksLikeCcminer =
                    lower.contains("ccminer")
                            || lower.contains("verushash")
                            || lower.contains("usage:");

            boolean success =
                    !architectureError
                            && looksLikeCcminer
                            && (
                            exitCode == 0
                                    || exitCode == 1
                    );

            return new SmokeTestResult(
                    success,
                    "exitCode="
                            + exitCode
                            + "\n"
                            + text
            );

        } finally {

            if (process != null) {

                try {
                    process.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String sha256(
            File file
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        try (
                FileInputStream input =
                        new FileInputStream(file)
        ) {

            byte[] buffer =
                    new byte[32 * 1024];

            int count;

            while ((count = input.read(buffer)) != -1) {

                digest.update(
                        buffer,
                        0,
                        count
                );
            }
        }

        byte[] hash =
                digest.digest();

        StringBuilder result =
                new StringBuilder(
                        hash.length * 2
                );

        for (byte b : hash) {

            result.append(
                    String.format(
                            "%02x",
                            b & 0xff
                    )
            );
        }

        return result.toString();
    }

    private static boolean isKnownAbi(
            String abi
    ) {

        return "arm64-v8a".equals(abi)
                || "armeabi-v7a".equals(abi)
                || "x86_64".equals(abi)
                || "x86".equals(abi);
    }
}
