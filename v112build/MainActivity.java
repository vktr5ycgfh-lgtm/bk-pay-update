package com.bkrides.riderpay;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.webkit.*;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;
import android.net.Uri;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.WindowManager;
import org.json.JSONObject;
import java.util.Locale;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Environment;

public class MainActivity extends Activity {
    private WebView web;
    private TextToSpeech voice;
    private boolean voiceReady = false;
    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_LOCATION = 4517;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private boolean pendingDutyStart = false;
    private boolean pendingInstallAfterSettings = false;
    private File pendingUpdateFile = null;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        voice = new TextToSpeech(this, status -> voiceReady = status == TextToSpeech.SUCCESS);
        web = new WebView(this);
        setContentView(web);
        web.setBackgroundColor(0xff09090b);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(true);

        web.addJavascriptInterface(new VoiceBridge(), "AndroidVoice");
        web.addJavascriptInterface(new RiderBridge(), "AndroidRider");
        web.addJavascriptInterface(new UpdateBridge(), "AndroidUpdater");

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocation()) {
                    callback.invoke(origin, true, false);
                    return;
                }
                pendingGeoOrigin = origin;
                pendingGeoCallback = callback;
                requestLocation();
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("https".equals(u.getScheme()) && HOST.equals(u.getHost())) {
                    String path = u.getPath();
                    if (path == null || path.equals("/")) path = "/index.html";
                    if (path.contains("..")) return empty();
                    String name = path.substring(1);
                    String mime = name.endsWith(".js") ? "application/javascript" :
                                  name.endsWith(".css") ? "text/css" :
                                  name.endsWith(".png") ? "image/png" : "text/html";
                    try { return new WebResourceResponse(mime, "UTF-8", getAssets().open(name)); }
                    catch (Exception e) { return empty(); }
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) { return navigate(request.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return navigate(Uri.parse(url)); }
        });

        web.loadUrl("https://" + HOST + "/index.html");
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocation() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
    }

    private boolean canOverlay() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void requestOverlay() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Allow Display over other apps for the floating fare bubble.", Toast.LENGTH_LONG).show();
        }
    }

    private void startDutyIfPossible() {
        if (!pendingDutyStart) return;
        if (!canOverlay()) { requestOverlay(); return; }
        if (!hasLocation()) { requestLocation(); return; }
        pendingDutyStart = false;
        Intent i = new Intent(this, FloatingTripService.class).setAction(FloatingTripService.ACTION_DUTY_ON);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    @Override protected void onResume() {
        super.onResume();
        if (pendingDutyStart) startDutyIfPossible();
        if (pendingInstallAfterSettings && canInstallPackages()) {
            pendingInstallAfterSettings = false;
            if (pendingUpdateFile != null && pendingUpdateFile.exists()) launchPackageInstaller(pendingUpdateFile);
        }
        if (web != null) web.postDelayed(() -> {
            try { web.evaluateJavascript("if(window.onNativeResume){window.onNativeResume();}", null); } catch (Exception ignored) {}
        }, 250);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        boolean granted = hasLocation();
        if (pendingGeoCallback != null && pendingGeoOrigin != null) pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
        pendingGeoCallback = null;
        pendingGeoOrigin = null;
        if (!granted) Toast.makeText(this, "Location permission is needed for trip distance. Manual fare entry still works.", Toast.LENGTH_LONG).show();
        if (granted && pendingDutyStart) startDutyIfPossible();
    }

    private WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

    private boolean navigate(Uri uri) {
        if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost())) return false;
        String scheme = uri.getScheme();
        if ("upi".equals(scheme) || "tel".equals(scheme) || "https".equals(scheme) || "geo".equals(scheme)) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (Exception e) { Toast.makeText(this, "No app available to open this link", Toast.LENGTH_LONG).show(); }
        }
        return true;
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
    }

    private void updateInstallStatus(String message) {
        if (web == null) return;
        final String safe = JSONObject.quote(message == null ? "" : message);
        runOnUiThread(() -> {
            try { web.evaluateJavascript("if(window.RiderUpdateStatus){window.RiderUpdateStatus(" + safe + ");}", null); }
            catch (Exception ignored) {}
        });
    }

    private void requestInstallPermission() {
        try {
            pendingInstallAfterSettings = true;
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            updateInstallStatus("Allow Install unknown apps for RIDER'S PAY, then tap UPDATE NOW again.");
        }
    }

    private void launchPackageInstaller(File apk) {
        try {
            if (apk == null || !apk.exists() || apk.length() < 10000) {
                updateInstallStatus("Update file is incomplete. Tap UPDATE NOW to retry.");
                return;
            }
            if (!canInstallPackages()) {
                pendingUpdateFile = apk;
                updateInstallStatus("One permission needed: allow RIDER'S PAY to install its update.");
                requestInstallPermission();
                return;
            }
            Uri uri = Uri.parse("content://" + UpdateProvider.AUTHORITY + "/riders-pay-update.apk");
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            updateInstallStatus("Update downloaded. Android will now ask you to confirm the update.");
            startActivity(i);
        } catch (Exception e) {
            updateInstallStatus("Could not open the Android installer. Tap UPDATE NOW to retry.");
        }
    }

    private void downloadAndInstall(String urlText) {
        if (urlText == null || !urlText.startsWith("https://")) {
            updateInstallStatus("Invalid update address.");
            return;
        }
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                updateInstallStatus("Downloading update inside RIDER'S PAY…");
                URL url = new URL(urlText);
                con = (HttpURLConnection)url.openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(45000);
                con.setInstanceFollowRedirects(true);
                con.setRequestProperty("User-Agent", "RIDERS-PAY-Updater/1.1.1");
                int code = con.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                int total = con.getContentLength();
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) dir = getCacheDir();
                if (!dir.exists()) dir.mkdirs();
                File tmp = new File(dir, "riders-pay-update.tmp");
                File apk = new File(dir, "riders-pay-update.apk");
                long got = 0;
                try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[32768];
                    int n;
                    int lastPct = -1;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        got += n;
                        if (total > 0) {
                            int pct = (int)Math.min(100, got * 100L / total);
                            if (pct >= lastPct + 10) {
                                lastPct = pct;
                                updateInstallStatus("Downloading update inside RIDER'S PAY… " + pct + "%");
                            }
                        }
                    }
                    out.flush();
                }
                if (tmp.length() < 10000) throw new Exception("download too small");
                if (apk.exists()) apk.delete();
                if (!tmp.renameTo(apk)) {
                    try (InputStream in = new java.io.FileInputStream(tmp); FileOutputStream out = new FileOutputStream(apk)) {
                        byte[] buf = new byte[32768]; int n; while((n=in.read(buf))>0) out.write(buf,0,n);
                    }
                    tmp.delete();
                }
                pendingUpdateFile = apk;
                runOnUiThread(() -> launchPackageInstaller(apk));
            } catch (Exception e) {
                updateInstallStatus("Update download failed. Check internet and tap UPDATE NOW again.");
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    public class UpdateBridge {
        @JavascriptInterface public void installUpdate(String url) {
            downloadAndInstall(url);
        }
    }

    public class VoiceBridge {
        @JavascriptInterface public void speak(String message, String language) {
            if (message == null || message.length() > 500) return;
            runOnUiThread(() -> {
                if (!voiceReady) { Toast.makeText(MainActivity.this, "Voice is not ready on this device", Toast.LENGTH_SHORT).show(); return; }
                Locale locale;
                try { locale = (language == null || language.trim().isEmpty()) ? new Locale("en","IN") : Locale.forLanguageTag(language); }
                catch (Exception e) { locale = new Locale("en","IN"); }
                int result = voice.setLanguage(locale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(MainActivity.this, "Install a voice for the selected language in Android speech settings", Toast.LENGTH_LONG).show(); return;
                }
                voice.setSpeechRate(0.92f);
                voice.speak(message, TextToSpeech.QUEUE_FLUSH, null, "riders-payment");
            });
        }
    }

    public class RiderBridge {
        @JavascriptInterface public void setTripActive(boolean active) {
            runOnUiThread(() -> {
                if (active) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface public void openLocationSettings() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface public boolean isDutyOn() {
            return getSharedPreferences(FloatingTripService.PREFS, Context.MODE_PRIVATE).getBoolean("duty", false);
        }

        @JavascriptInterface public void updatePaymentProfile(String vpa, String name) {
            String safeVpa = vpa == null ? "" : vpa.trim();
            String safeName = (name == null || name.trim().isEmpty()) ? "RIDER'S PAY" : name.trim();
            getSharedPreferences(FloatingTripService.PREFS, Context.MODE_PRIVATE).edit()
                .putString("upiVpa", safeVpa)
                .putString("payeeName", safeName)
                .apply();
        }

        @JavascriptInterface public boolean setDuty(boolean on, String rateJson) {
            if (!on) {
                pendingDutyStart = false;
                getSharedPreferences(FloatingTripService.PREFS, Context.MODE_PRIVATE).edit().putBoolean("duty", false).apply();
                Intent stop = new Intent(MainActivity.this, FloatingTripService.class).setAction(FloatingTripService.ACTION_DUTY_OFF);
                try { startService(stop); } catch (Exception ignored) {}
                return false;
            }
            saveRateCard(rateJson);
            pendingDutyStart = true;
            runOnUiThread(() -> startDutyIfPossible());
            return canOverlay() && hasLocation();
        }

        @JavascriptInterface public void updateRateCard(String rateJson) {
            saveRateCard(rateJson);
            Intent i = new Intent(MainActivity.this, FloatingTripService.class).setAction(FloatingTripService.ACTION_CONFIG);
            try { startService(i); } catch (Exception ignored) {}
        }

        @JavascriptInterface public void floatingAction(String action) {
            String a = "start".equals(action) ? FloatingTripService.ACTION_START_RIDE :
                       "picked".equals(action) ? FloatingTripService.ACTION_PICKED :
                       "waiting".equals(action) ? FloatingTripService.ACTION_WAITING : "";
            if (a.isEmpty()) return;
            Intent i = new Intent(MainActivity.this, FloatingTripService.class).setAction(a);
            try { startService(i); } catch (Exception ignored) {}
        }

        @JavascriptInterface public void endFloatingTrip() {
            Intent i = new Intent(MainActivity.this, FloatingTripService.class).setAction(FloatingTripService.ACTION_END);
            try { startService(i); } catch (Exception ignored) {}
        }

        @JavascriptInterface public void resetFloatingTrip() {
            Intent i = new Intent(MainActivity.this, FloatingTripService.class).setAction(FloatingTripService.ACTION_RESET);
            try { startService(i); } catch (Exception ignored) {}
        }

        @JavascriptInterface public String getTripSnapshot() {
            return FloatingTripService.snapshotJson(MainActivity.this);
        }

        private void saveRateCard(String json) {
            try {
                JSONObject o = new JSONObject(json == null ? "{}" : json);
                getSharedPreferences(FloatingTripService.PREFS, Context.MODE_PRIVATE).edit()
                    .putFloat("shortMaxKm", (float)o.optDouble("shortMaxKm", 9))
                    .putFloat("shortRate", (float)o.optDouble("shortRate", 9.5))
                    .putFloat("longStartKm", (float)o.optDouble("longStartKm", 10))
                    .putFloat("longRate", (float)o.optDouble("longRate", 12))
                    .putInt("nightStart", o.optInt("nightStart", 23))
                    .putInt("nightEnd", o.optInt("nightEnd", 5))
                    .putFloat("nightPercent", (float)o.optDouble("nightPercent", 30))
                    .putFloat("waitPerMin", (float)o.optDouble("waitPerMin", 1))
                    .putInt("waitMaxMinutes", o.optInt("waitMaxMinutes", 15))
                    .putFloat("pickupFreeKm", (float)o.optDouble("pickupFreeKm", 2))
                    .putFloat("pickupRate", (float)o.optDouble("pickupRate", 5))
                    .putFloat("pickupMax", (float)o.optDouble("pickupMax", 15))
                    .apply();
            } catch (Exception ignored) {}
        }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("AndroidVoice");
            web.removeJavascriptInterface("AndroidRider");
            web.removeJavascriptInterface("AndroidUpdater");
            web.destroy();
        }
        if (voice != null) { voice.stop(); voice.shutdown(); }
        super.onDestroy();
    }
}
