package de.astra.pulse;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends FragmentActivity {
    private static final int REQUEST_LOCAL_VIDEO = 2048;
    private WebView webView;
    private String pendingVideoConfig;
    private boolean pendingVideoAnalysis;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setImportantForAutofill(WebView.IMPORTANT_FOR_AUTOFILL_YES);

        webView.addJavascriptInterface(new UpdateBridge(), "AstraUpdater");
        webView.addJavascriptInterface(new BiometricBridge(), "AstraBiometric");
        webView.addJavascriptInterface(new VideoBridge(), "AstraVideo");
        webView.setWebViewClient(new WebViewClient() {
            private boolean handleBiometricUrl(Uri uri) {
                if (uri != null && "astra-pulse".equals(uri.getScheme()) && "biometric".equals(uri.getHost())) {
                    showBiometricPrompt();
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleBiometricUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleBiometricUrl(Uri.parse(url));
            }
        });
        webView.loadUrl("file:///android_asset/IntervallTimer.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_LOCAL_VIDEO || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri videoUri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    videoUri,
                    data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
        }

        try {
            JSONObject config = new JSONObject(pendingVideoConfig == null ? "{}" : pendingVideoConfig);
            config.put("url", videoUri.toString());
            if (pendingVideoAnalysis) {
                analyzeVideo(videoUri, config);
            } else {
                launchVideo(config.toString());
            }
        } catch (Exception error) {
            Toast.makeText(this, "Das ausgewählte Video konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show();
        } finally {
            pendingVideoConfig = null;
            pendingVideoAnalysis = false;
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runJs(String script) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    private String quote(String value) {
        return JSONObject.quote(value == null ? "" : value);
    }

    private void launchVideo(String payload) {
        Intent intent = new Intent(this, OnlineVideoActivity.class);
        intent.putExtra(OnlineVideoActivity.EXTRA_CONFIG, payload);
        startActivity(intent);
    }

    public class VideoBridge {
        @JavascriptInterface
        public void open(String payload) {
            runOnUiThread(() -> {
                try {
                    JSONObject config = new JSONObject(payload);
                    if (!config.optString("url", "").startsWith("https://")) {
                        Toast.makeText(MainActivity.this, "Bitte eine sichere https://-Adresse verwenden.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    launchVideo(payload);
                } catch (Exception error) {
                    Toast.makeText(MainActivity.this, "Der Videomodus konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void openLocal(String payload) {
            runOnUiThread(() -> {
                try {
                    new JSONObject(payload);
                    pendingVideoConfig = payload;
                    pendingVideoAnalysis = false;
                    openVideoPicker();
                } catch (Exception error) {
                    Toast.makeText(MainActivity.this, "Die Videoauswahl konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void analyzeLocal(String payload) {
            runOnUiThread(() -> {
                try {
                    new JSONObject(payload);
                    pendingVideoConfig = payload;
                    pendingVideoAnalysis = true;
                    openVideoPicker();
                } catch (Exception error) {
                    Toast.makeText(MainActivity.this, "Die Videoanalyse konnte nicht gestartet werden.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void openVideoPicker() {
        Intent picker;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            picker = new Intent(MediaStore.ACTION_PICK_IMAGES);
        } else {
            picker = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        }
        picker.setType("video/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(picker, REQUEST_LOCAL_VIDEO);
    }

    private void analyzeVideo(Uri videoUri, JSONObject baseConfig) {
        Toast.makeText(this, "Video wird lokal analysiert ...", Toast.LENGTH_LONG).show();
        executor.execute(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                String cacheKey = "analysis_v2_" + sha256(videoUri.toString());
                String cached = getSharedPreferences("astra_video_analyses", MODE_PRIVATE).getString(cacheKey, null);
                JSONObject analysis;
                if (cached != null) {
                    analysis = new JSONObject(cached);
                } else {
                    retriever.setDataSource(MainActivity.this, videoUri);
                    long durationMs = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                    analysis = buildVideoAnalysis(retriever, durationMs);
                    getSharedPreferences("astra_video_analyses", MODE_PRIVATE)
                            .edit().putString(cacheKey, analysis.toString()).apply();
                }
                String strength = baseConfig.optString("challengeStrength", "matched");
                JSONArray generatedModes = generateModes(analysis, strength);
                baseConfig.put("url", videoUri.toString());
                baseConfig.put("modes", generatedModes);
                String profile = analysis.getString("profile");
                int blocks = generatedModes.length();
                runJs("window.astraVideoAnalysisResult({ok:true,profile:" + quote(profile)
                        + ",blocks:" + blocks + "});");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Profil erkannt: " + profile, Toast.LENGTH_LONG).show();
                    launchVideo(baseConfig.toString());
                });
            } catch (Exception error) {
                runJs("window.astraVideoAnalysisResult({ok:false,error:" + quote(error.getMessage()) + "});");
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Videoanalyse fehlgeschlagen: " + error.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private JSONObject buildVideoAnalysis(MediaMetadataRetriever retriever, long durationMs) throws Exception {
        int sampleSeconds = durationMs > 30 * 60_000L ? 5 : durationMs > 10 * 60_000L ? 3 : 2;
        long stepUs = sampleSeconds * 1_000_000L;
        List<Double> activity = new ArrayList<>();
        int[] previous = null;
        for (long timeUs = 0; timeUs < durationMs * 1000L; timeUs += stepUs) {
            Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) continue;
            Bitmap small = Bitmap.createScaledBitmap(frame, 24, 14, true);
            if (small != frame) frame.recycle();
            int[] pixels = new int[24 * 14];
            small.getPixels(pixels, 0, 24, 0, 0, 24, 14);
            small.recycle();
            if (previous != null) activity.add(frameDifference(previous, pixels));
            previous = pixels;
        }
        if (activity.isEmpty()) throw new IllegalStateException("Das Video enthält zu wenige analysierbare Bilder.");

        List<Double> sorted = new ArrayList<>(activity);
        Collections.sort(sorted);
        double average = 0;
        for (double value : activity) average += value;
        average /= activity.size();
        double peak = sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * 0.9)));
        double profileScore = average * 0.65 + peak * 0.35;
        String profile = profileScore < 0.035 ? "Soft"
                : profileScore < 0.065 ? "Moderat"
                : profileScore < 0.11 ? "Dynamisch"
                : profileScore < 0.18 ? "Intensiv" : "Sehr intensiv";

        JSONArray activityValues = new JSONArray();
        for (double value : activity) activityValues.put(value);
        JSONObject result = new JSONObject();
        result.put("profile", profile);
        result.put("profileScore", profileScore);
        result.put("durationMs", durationMs);
        result.put("sampleSeconds", sampleSeconds);
        result.put("activity", activityValues);
        return result;
    }

    private JSONArray generateModes(JSONObject analysis, String strength) throws Exception {
        JSONArray activitySource = analysis.getJSONArray("activity");
        int sampleSeconds = analysis.getInt("sampleSeconds");
        long durationSeconds = Math.max(1, analysis.getLong("durationMs") / 1000L);
        long warmupSeconds = Math.min(90, Math.max(20, Math.round(durationSeconds * 0.08)));
        double profileScore = analysis.getDouble("profileScore");
        double strengthOffset = "relaxed".equals(strength) ? -0.035
                : "boosted".equals(strength) ? 0.04 : 0;
        JSONArray modes = new JSONArray();
        String lastName = null;
        String previousSpeed = null;
        int accumulated = 0;
        for (int index = 0; index < activitySource.length(); index++) {
            double value = activitySource.getDouble(index);
            double smoothed = value;
            if (index > 0) smoothed = (activitySource.getDouble(index - 1) + value * 2) / 3;
            long elapsedSeconds = (long) index * sampleSeconds;
            String name;
            if (elapsedSeconds < warmupSeconds / 2) {
                name = "Slow";
            } else if (elapsedSeconds < warmupSeconds) {
                name = "Normal";
            } else {
                name = speedForActivity(smoothed + strengthOffset, profileScore);
                name = limitSpeedStep(previousSpeed, name);
            }
            previousSpeed = name;
            if (name.equals(lastName)) {
                accumulated += sampleSeconds;
            } else {
                if (lastName != null) modes.put(modeJson(lastName, accumulated));
                lastName = name;
                accumulated = sampleSeconds;
            }
        }
        if (lastName != null) modes.put(modeJson(lastName, accumulated));
        return modes;
    }

    private String limitSpeedStep(String previous, String requested) {
        String[] speeds = {"Slow", "Normal", "Fast", "Faster", "Super fast", "Speed", "Super Speed", "Ultraspeed"};
        if (previous == null) return requested;
        int previousIndex = 0;
        int requestedIndex = 0;
        for (int index = 0; index < speeds.length; index++) {
            if (speeds[index].equals(previous)) previousIndex = index;
            if (speeds[index].equals(requested)) requestedIndex = index;
        }
        if (requestedIndex > previousIndex + 1) requestedIndex = previousIndex + 1;
        if (requestedIndex < previousIndex - 2) requestedIndex = previousIndex - 2;
        return speeds[Math.max(0, Math.min(speeds.length - 1, requestedIndex))];
    }

    private double frameDifference(int[] first, int[] second) {
        long difference = 0;
        for (int index = 0; index < first.length; index++) {
            int a = first[index];
            int b = second[index];
            difference += Math.abs(((a >> 16) & 255) - ((b >> 16) & 255));
            difference += Math.abs(((a >> 8) & 255) - ((b >> 8) & 255));
            difference += Math.abs((a & 255) - (b & 255));
        }
        return difference / (double) (first.length * 3 * 255);
    }

    private String speedForActivity(double activity, double profileScore) {
        double adjusted = activity + profileScore * 0.35;
        if (adjusted < 0.035) return "Slow";
        if (adjusted < 0.06) return "Normal";
        if (adjusted < 0.085) return "Fast";
        if (adjusted < 0.115) return "Faster";
        if (adjusted < 0.15) return "Super fast";
        if (adjusted < 0.19) return "Speed";
        if (adjusted < 0.24) return "Super Speed";
        return "Ultraspeed";
    }

    private JSONObject modeJson(String name, int seconds) throws Exception {
        JSONObject mode = new JSONObject();
        mode.put("name", name);
        mode.put("seconds", Math.max(2, seconds));
        return mode;
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format("%02x", item));
        return result.toString();
    }

    private void showBiometricPrompt() {
        runOnUiThread(() -> {
            BiometricManager manager = BiometricManager.from(MainActivity.this);
            int availability = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.BIOMETRIC_WEAK);
            if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
                String message;
                if (availability == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                    message = "Auf diesem Gerät ist noch kein Fingerabdruck oder keine Biometrie eingerichtet.";
                } else if (availability == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
                    message = "Dieses Gerät besitzt keinen unterstützten Biometriesensor.";
                } else if (availability == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
                    message = "Der Biometriesensor ist momentan nicht verfügbar. Bitte versuche es erneut.";
                } else {
                    message = "Biometrisches Entsperren wird auf diesem Gerät derzeit nicht unterstützt.";
                }
                runJs("window.astraBiometricResult({ok:false,error:" + quote(message) + "});");
                return;
            }
            BiometricPrompt prompt = new BiometricPrompt(
                    MainActivity.this,
                    ContextCompat.getMainExecutor(MainActivity.this),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            runJs("window.astraBiometricResult({ok:true});");
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errorMessage) {
                            super.onAuthenticationError(errorCode, errorMessage);
                            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                    || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) return;
                            runJs("window.astraBiometricResult({ok:false,error:"
                                    + quote(errorMessage.toString()) + "});");
                        }
                    }
            );
            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Astra Pulse entsperren")
                    .setSubtitle("Mit Fingerabdruck oder Gerätebiometrie bestätigen")
                    .setNegativeButtonText("Passwort verwenden")
                    .build();
            prompt.authenticate(info);
        });
    }

    public class BiometricBridge {
        @JavascriptInterface
        public boolean isAvailable() {
            BiometricManager manager = BiometricManager.from(MainActivity.this);
            return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
        }

        @JavascriptInterface
        public void authenticate() {
            showBiometricPrompt();
        }
    }

    private String readText(String sourceUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "application/json");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }

        try (InputStream input = connection.getInputStream()) {
            byte[] buffer = new byte[8192];
            StringBuilder builder = new StringBuilder();
            int read;
            while ((read = input.read(buffer)) != -1) {
                builder.append(new String(buffer, 0, read));
            }
            return builder.toString();
        } finally {
            connection.disconnect();
        }
    }

    private File downloadApk(String apkUrl) throws Exception {
        File updateDir = new File(getCacheDir(), "updates");
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            throw new IllegalStateException("Update-Ordner konnte nicht erstellt werden.");
        }

        File target = new File(updateDir, "astra-pulse-update.apk");
        HttpURLConnection connection = (HttpURLConnection) new URL(apkUrl).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("APK Download fehlgeschlagen: HTTP " + code);
        }

        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }

        if (target.length() <= 0) {
            throw new IllegalStateException("APK Download war leer.");
        }
        return target;
    }

    private void openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        }
    }

    public class UpdateBridge {
        @JavascriptInterface
        public int currentVersionCode() {
            return BuildConfig.VERSION_CODE;
        }

        @JavascriptInterface
        public String currentVersionName() {
            return BuildConfig.VERSION_NAME;
        }

        @JavascriptInterface
        public void checkUpdate(String manifestUrl) {
            executor.execute(() -> {
                try {
                    String text = readText(manifestUrl);
                    JSONObject manifest = new JSONObject(text);
                    int versionCode = manifest.optInt("versionCode", 0);
                    String versionName = manifest.optString("versionName", "");
                    String apkUrl = manifest.optString("apkUrl", "");
                    String notes = manifest.optString("notes", "");
                    String script = "window.astraUpdateResult({"
                            + "ok:true,"
                            + "currentVersionCode:" + BuildConfig.VERSION_CODE + ","
                            + "currentVersionName:" + quote(BuildConfig.VERSION_NAME) + ","
                            + "versionCode:" + versionCode + ","
                            + "versionName:" + quote(versionName) + ","
                            + "apkUrl:" + quote(apkUrl) + ","
                            + "notes:" + quote(notes)
                            + "});";
                    runJs(script);
                } catch (Exception error) {
                    runJs("window.astraUpdateResult({ok:false,error:" + quote(error.getMessage()) + "});");
                }
            });
        }

        @JavascriptInterface
        public void installUpdate(String apkUrl) {
            executor.execute(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            && !getPackageManager().canRequestPackageInstalls()) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Bitte einmal Installationen fuer Astra Pulse erlauben.", Toast.LENGTH_LONG).show();
                            openInstallPermissionSettings();
                        });
                        return;
                    }

                    File apk = downloadApk(apkUrl);
                    Uri uri = FileProvider.getUriForFile(
                            MainActivity.this,
                            getPackageName() + ".fileprovider",
                            apk
                    );

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (Exception error) {
                    runJs("window.astraUpdateInstallResult({ok:false,error:" + quote(error.getMessage()) + "});");
                }
            });
        }
    }
}
