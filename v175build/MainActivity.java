package com.bkrides.riderpay;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;
import android.net.Uri;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.WindowManager;
import java.util.Locale;
import java.io.ByteArrayInputStream;

public class MainActivity extends Activity {
    private WebView web;
    private TextToSpeech voice;
    private boolean voiceReady = false;
    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_LOCATION = 4517;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);

        voice = new TextToSpeech(this, status -> {
            voiceReady = status == TextToSpeech.SUCCESS;
        });

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

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                    return;
                }
                pendingGeoOrigin = origin;
                pendingGeoCallback = callback;
                requestPermissions(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQ_LOCATION);
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
                    try {
                        return new WebResourceResponse(mime, "UTF-8", getAssets().open(name));
                    } catch (Exception e) {
                        return empty();
                    }
                }
                return null;
            }

            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return navigate(request.getUrl());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return navigate(Uri.parse(url));
            }
        });

        web.loadUrl("https://" + HOST + "/index.html");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        boolean granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                          checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (pendingGeoCallback != null && pendingGeoOrigin != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
        }
        pendingGeoCallback = null;
        pendingGeoOrigin = null;
        if (!granted) {
            Toast.makeText(this, "Location permission is needed for automatic trip distance. Manual km is still available.", Toast.LENGTH_LONG).show();
        }
    }

    private WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

    private boolean navigate(Uri uri) {
        if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost())) return false;
        String scheme = uri.getScheme();
        if ("upi".equals(scheme) || "tel".equals(scheme) || "https".equals(scheme) || "geo".equals(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception e) {
                Toast.makeText(this, "No app available to open this link", Toast.LENGTH_LONG).show();
            }
        }
        return true;
    }

    public class VoiceBridge {
        @JavascriptInterface public void speak(String message, String language) {
            if (message == null || message.length() > 500) return;
            runOnUiThread(() -> {
                if (!voiceReady) {
                    Toast.makeText(MainActivity.this, "Voice is not ready on this device", Toast.LENGTH_SHORT).show();
                    return;
                }
                Locale locale;
                try {
                    locale = (language == null || language.trim().isEmpty()) ? new Locale("en", "IN") : Locale.forLanguageTag(language);
                } catch (Exception e) {
                    locale = new Locale("en", "IN");
                }
                int result = voice.setLanguage(locale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(MainActivity.this, "Install a voice for the selected language in Android speech settings", Toast.LENGTH_LONG).show();
                    return;
                }
                voice.setSpeechRate(0.92f);
                voice.speak(message, TextToSpeech.QUEUE_FLUSH, null, "riders-payment");
            });
        }
    }

    public class RiderBridge {
        @JavascriptInterface public void setTripActive(boolean active) {
            runOnUiThread(() -> {
                if (active) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface public void openLocationSettings() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); } catch (Exception ignored) {}
                }
            });
        }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("AndroidVoice");
            web.removeJavascriptInterface("AndroidRider");
            web.destroy();
        }
        if (voice != null) {
            voice.stop();
            voice.shutdown();
        }
        super.onDestroy();
    }
}
