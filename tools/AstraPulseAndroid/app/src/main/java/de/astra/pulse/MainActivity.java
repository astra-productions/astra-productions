package de.astra.pulse;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends FragmentActivity {
    private WebView webView;
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

    public class BiometricBridge {
        @JavascriptInterface
        public boolean isAvailable() {
            BiometricManager manager = BiometricManager.from(MainActivity.this);
            return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
        }

        @JavascriptInterface
        public void authenticate() {
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
