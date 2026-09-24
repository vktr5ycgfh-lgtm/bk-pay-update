package com.riderspay.autodriver;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.EnumMap;
import java.util.Locale;

/**
 * First clean native Android implementation based ONLY on the supplied explainer video:
 * adjustable local rate card; duty state; live GPS metering; QR on trip completion;
 * local ride/payment history. Neither provider settlement nor customer dispatch is claimed.
 */
public class MainActivity extends Activity {
    private static final int YELLOW = Color.rgb(255, 212, 0);
    private static final int AMBER = Color.rgb(255, 174, 0);
    private static final int BG = Color.rgb(10, 10, 10);
    private static final int PANEL = Color.rgb(24, 24, 24);
    private static final int PANEL_RAISED = Color.rgb(32, 32, 32);
    private static final int LINE = Color.rgb(57, 57, 57);
    private static final int GREY = Color.rgb(165, 165, 165);
    private static final int WHITE = Color.rgb(250, 250, 247);
    private static final int SUCCESS = Color.rgb(68, 214, 132);
    private static final int REQUEST_LOCATION = 12;
    private static final int REQUEST_NOTIFICATIONS = 13;

    private SharedPreferences prefs;
    private TripStore store;
    private LinearLayout content;
    private LinearLayout bottomNav;
    private TextView meterDistance, meterFare, meterDuration, meterGps;
    private int activeTab = 0;
    private String paymentRideId;
    private long paymentAmountPaise;
    private boolean paymentPreviewOnly;
    private boolean waitingForOverlayPermission;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (prefs != null && prefs.getBoolean("ride_active", false)) updateMeter();
            handler.postDelayed(this, 1000);
        }
    };
    private final BroadcastReceiver gpsUpdates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateMeter(); }
    };
    private boolean receiverRegistered;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        store = new TripStore(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        render();
        handleOverlayIntent(getIntent());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(RideTrackingService.UPDATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(gpsUpdates, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(gpsUpdates, filter);
        receiverRegistered = true;
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        updateMeter();
        if (waitingForOverlayPermission && Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = false;
            startFloatingMeter();
            render();
        }
    }

    @Override protected void onPause() {
        if (receiverRegistered) { unregisterReceiver(gpsUpdates); receiverRegistered = false; }
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable s = new GradientDrawable();
        s.setColor(fill);
        s.setCornerRadius(dp(radius));
        if (stroke != 0) s.setStroke(dp(1), stroke);
        return s;
    }
    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable s = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        s.setCornerRadius(dp(radius));
        return s;
    }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setFontFeatureSettings("kern");
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(dp(2), 1);
        return t;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private LinearLayout.LayoutParams margin(int width, int height, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }
    private LinearLayout card() {
        LinearLayout l = vertical();
        l.setBackground(shape(PANEL, 22, LINE));
        l.setPadding(dp(18), dp(18), dp(18), dp(18));
        l.setElevation(dp(2));
        return l;
    }
    private View gap(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return v;
    }
    private Button button(String value, boolean primary, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? BG : WHITE);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                shape(primary ? AMBER : Color.rgb(48, 48, 48), 16, primary ? 0 : LINE));
        states.addState(new int[]{},
                shape(primary ? YELLOW : PANEL_RAISED, 16, primary ? 0 : LINE));
        b.setBackground(states);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setMinHeight(dp(56));
        b.setElevation(primary ? dp(3) : 0);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void note(LinearLayout parent, String msg) {
        TextView t = text(msg, 13, GREY, false);
        parent.addView(t, margin(-1, -2, 9, 2));
    }

    private TextView badge(String value, int color) {
        TextView b = text(value, 11, color, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(11), dp(7), dp(11), dp(7));
        b.setBackground(shape(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)), 50,
                Color.argb(100, Color.red(color), Color.green(color), Color.blue(color))));
        return b;
    }

    private void sectionTitle(LinearLayout parent, String eyebrow, String title, String subtitle) {
        parent.addView(text(eyebrow, 11, YELLOW, true));
        parent.addView(text(title, 27, WHITE, true), margin(-1, -2, 5, 0));
        if (subtitle != null && !subtitle.isEmpty()) note(parent, subtitle);
    }

    private LinearLayout quickAction(String icon, String title, String detail, Runnable action) {
        LinearLayout tile = vertical();
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(8), dp(14), dp(8), dp(13));
        tile.setBackground(shape(PANEL_RAISED, 18, LINE));
        TextView mark = text(icon, 22, YELLOW, true);
        mark.setGravity(Gravity.CENTER);
        tile.addView(mark);
        TextView heading = text(title, 12, WHITE, true);
        heading.setGravity(Gravity.CENTER);
        tile.addView(heading, margin(-1, -2, 5, 0));
        TextView small = text(detail, 10, GREY, false);
        small.setGravity(Gravity.CENTER);
        tile.addView(small, margin(-1, -2, 2, 0));
        tile.setOnClickListener(v -> action.run());
        return tile;
    }

    private void render() {
        meterDistance = meterFare = meterDuration = meterGps = null;
        if (!prefs.getBoolean("setup_complete", false)) {
            showSetup();
            return;
        }
        LinearLayout shell = vertical();
        shell.setBackgroundColor(BG);
        shell.setFitsSystemWindows(true);
        setContentView(shell);
        LinearLayout header = horizontal();
        header.setPadding(dp(18), dp(14), dp(18), dp(13));
        TextView autoMark = text("AUTO", 10, BG, true);
        autoMark.setGravity(Gravity.CENTER);
        autoMark.setBackground(shape(YELLOW, 12, 0));
        header.addView(autoMark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout wordmark = vertical();
        wordmark.setPadding(dp(11), 0, 0, 0);
        wordmark.addView(text("RIDERS PAY", 20, WHITE, true));
        String vehicle = prefs.getString("vehicle_number", "").trim();
        wordmark.addView(text(vehicle.isEmpty() ? "AUTO DRIVER PARTNER" : vehicle.toUpperCase(Locale.US), 10, GREY, true));
        header.addView(wordmark, new LinearLayout.LayoutParams(0, -2, 1));
        TextView status = badge(prefs.getBoolean("duty_on", false) ? "●  ON DUTY" : "○  OFF DUTY",
                prefs.getBoolean("duty_on", false) ? SUCCESS : GREY);
        header.addView(status);
        shell.addView(header);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        content = vertical();
        content.setPadding(dp(16), dp(16), dp(16), dp(28));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (paymentRideId == null) {
            bottomNav = horizontal();
            bottomNav.setBackgroundColor(Color.rgb(15, 15, 15));
            bottomNav.setPadding(dp(8), dp(6), dp(8), dp(7));
            shell.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(70)));
            buildNav();
        }
        if (paymentRideId != null) showPaymentScreen();
        else if (activeTab == 0) showDashboard();
        else if (activeTab == 1) showHistory();
        else showSettings();
    }
    private void buildNav() {
        String[] icons = {"⌂", "▤", "⚙"};
        String[] pages = {"Home", "Trips", "Settings"};
        for (int i = 0; i < pages.length; i++) {
            int index = i;
            LinearLayout tab = vertical();
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(4), dp(6), dp(4), dp(5));
            if (i == activeTab) tab.setBackground(shape(PANEL_RAISED, 15, LINE));
            TextView icon = text(icons[i], 20, i == activeTab ? YELLOW : GREY, true);
            icon.setGravity(Gravity.CENTER);
            TextView label = text(pages[i], 10, i == activeTab ? YELLOW : GREY, i == activeTab);
            label.setGravity(Gravity.CENTER);
            tab.addView(icon);
            tab.addView(label);
            bottomNav.addView(tab, new LinearLayout.LayoutParams(0, -1, 1));
            tab.setOnClickListener(v -> { activeTab = index; render(); });
        }
    }

    private void showDashboard() {
        boolean riding = prefs.getBoolean("ride_active", false);
        String driverName = prefs.getString("driver_name", "Driver");
        String day = new SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(System.currentTimeMillis());
        sectionTitle(content, day.toUpperCase(Locale.US), "Vanakkam, " + driverName + "!", "Your auto business, all in one place.");
        content.addView(gap(8));

        LinearLayout duty = card();
        LinearLayout dutyTop = horizontal();
        LinearLayout dutyCopy = vertical();
        dutyCopy.addView(text("DRIVER STATUS", 11, YELLOW, true));
        dutyCopy.addView(text(prefs.getBoolean("duty_on", false) ? "Ready for rides" : "Start your work day", 19, WHITE, true), margin(-1, -2, 4, 0));
        dutyCopy.addView(text(prefs.getBoolean("duty_on", false) ? "GPS meter is ready" : "Go online when you are ready", 12, GREY, false));
        dutyTop.addView(dutyCopy, new LinearLayout.LayoutParams(0, -2, 1));
        dutyTop.addView(badge(prefs.getBoolean("duty_on", false) ? "ONLINE" : "OFFLINE",
                prefs.getBoolean("duty_on", false) ? SUCCESS : GREY));
        duty.addView(dutyTop);
        duty.addView(button(prefs.getBoolean("duty_on", false) ? "Go off duty" : "GO ON DUTY", !prefs.getBoolean("duty_on", false), () -> {
            if (prefs.getBoolean("ride_active", false)) { toast("Finish the ride before going off duty"); return; }
            boolean goingOn = !prefs.getBoolean("duty_on", false);
            prefs.edit().putBoolean("duty_on", goingOn).apply();
            if (goingOn) startFloatingMeter(); else stopFloatingMeter();
            render();
        }), margin(-1, -2, 15, 0));
        content.addView(duty, margin(-1, -2, 8, 12));

        LinearLayout meter = card();
        meter.setBackground(riding ? gradient(Color.rgb(58, 47, 0), Color.rgb(25, 23, 15), 22)
                : shape(PANEL, 22, LINE));
        LinearLayout meterHeader = horizontal();
        meterHeader.addView(text(riding ? "●  LIVE RIDE" : "AUTO FARE METER", 11, riding ? SUCCESS : YELLOW, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        meterHeader.addView(badge(riding ? "TRACKING" : "READY", riding ? SUCCESS : YELLOW));
        meter.addView(meterHeader);
        meterFare = text("₹0.00", 46, WHITE, true);
        meter.addView(meterFare, margin(-1, -2, 16, 0));
        meter.addView(text("CURRENT FARE", 10, GREY, true));
        LinearLayout metrics = horizontal();
        LinearLayout left = vertical();
        meterDistance = text("0.00 km", 19, WHITE, true);
        left.addView(meterDistance);
        left.addView(text("Distance", 12, GREY, false));
        LinearLayout middle = vertical();
        middle.addView(text(FareEngine.currency(riding ? prefs.getLong("ride_rate_km", getPerKm()) : getPerKm()), 19, WHITE, true));
        middle.addView(text("Per km", 12, GREY, false));
        LinearLayout right = vertical();
        meterDuration = text("00:00", 19, WHITE, true);
        right.addView(meterDuration);
        right.addView(text("Duration", 12, GREY, false));
        metrics.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        metrics.addView(middle, new LinearLayout.LayoutParams(0, -2, 1));
        metrics.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        meter.addView(metrics, margin(-1, -2, 20, 12));
        meterGps = text(riding ? "Waiting for GPS..." : "Ready to start", 12, GREY, false);
        meter.addView(meterGps);
        if (riding) {
            meter.addView(button("END RIDE & COLLECT FARE", true, this::endRide), margin(-1, -2, 18, 0));
        } else {
            meter.addView(button("START NEW RIDE  →", true, this::attemptStartRide), margin(-1, -2, 18, 0));
        }
        content.addView(meter, margin(-1, -2, 2, 12));
        updateMeter();

        content.addView(text("QUICK ACCESS", 11, YELLOW, true), margin(-1, -2, 5, 8));
        LinearLayout actions = horizontal();
        actions.setWeightSum(3);
        actions.addView(quickAction("₹", "Rate card", "Edit fares", () -> { activeTab = 2; render(); }),
                new LinearLayout.LayoutParams(0, dp(105), 1));
        LinearLayout.LayoutParams tileGap = new LinearLayout.LayoutParams(0, dp(105), 1);
        tileGap.setMargins(dp(8), 0, dp(8), 0);
        actions.addView(quickAction("◉", "Float meter", "Over your maps", this::startFloatingMeter), tileGap);
        actions.addView(quickAction("▤", "Trips", "Ride history", () -> { activeTab = 1; render(); }),
                new LinearLayout.LayoutParams(0, dp(105), 1));
        content.addView(actions, margin(-1, -2, 0, 12));

        LinearLayout rate = card();
        LinearLayout rateRow = horizontal();
        LinearLayout rateCopy = vertical();
        rateCopy.addView(text("TODAY'S RATE CARD", 11, YELLOW, true));
        rateCopy.addView(text(FareEngine.currency(getBase()) + " base  ·  " + FareEngine.currency(getPerKm()) + "/km", 17, WHITE, true), margin(-1, -2, 7, 0));
        rateCopy.addView(text("Minimum " + FareEngine.currency(getMinimum()), 12, GREY, false));
        rateRow.addView(rateCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = badge("EDIT  ›", YELLOW);
        edit.setOnClickListener(v -> { activeTab = 2; render(); });
        rateRow.addView(edit);
        rate.addView(rateRow);
        content.addView(rate);
    }

    private long getBase() { return prefs.getLong("rate_base", 4000); }
    private long getPerKm() { return prefs.getLong("rate_km", 1500); }
    private long getMinimum() { return prefs.getLong("rate_min", 4000); }
    private long currentFare() {
        boolean riding = prefs.getBoolean("ride_active", false);
        return FareEngine.calculatePaise(
                riding ? prefs.getLong("ride_rate_base", getBase()) : getBase(),
                riding ? prefs.getLong("ride_rate_km", getPerKm()) : getPerKm(),
                riding ? prefs.getLong("ride_rate_min", getMinimum()) : getMinimum(),
                riding ? prefs.getFloat("ride_meters", 0f) : 0f);
    }
    private void updateMeter() {
        if (meterFare == null || meterDistance == null || meterDuration == null || meterGps == null) return;
        boolean active = prefs.getBoolean("ride_active", false);
        meterFare.setText(active ? FareEngine.currency(currentFare()) : "₹0.00");
        meterDistance.setText(String.format(Locale.US, "%.2f km", active ? prefs.getFloat("ride_meters", 0f) / 1000f : 0f));
        long elapsed = active ? Math.max(0, (System.currentTimeMillis() - prefs.getLong("ride_started", System.currentTimeMillis())) / 1000) : 0;
        meterDuration.setText(String.format(Locale.US, "%02d:%02d:%02d", elapsed / 3600, (elapsed / 60) % 60, elapsed % 60));
        int fixes = prefs.getInt("ride_fixes", 0);
        meterGps.setText(!active ? "Start your ride when ready" : fixes == 0 ? "Waiting for your first accurate GPS fix..."
                : "● GPS tracking  ·  " + fixes + " location fixes");
        meterGps.setTextColor(active && fixes > 0 ? YELLOW : GREY);
    }

    private void attemptStartRide() {
        if (!prefs.getBoolean("duty_on", false)) {
            toast("Switch on duty before starting your ride");
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            android.location.LocationManager manager = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            if (!manager.isLocationEnabled()) {
                new AlertDialog.Builder(this).setTitle("Location is turned off")
                        .setMessage("Turn on your device's location to meter the trip accurately.")
                        .setPositiveButton("Location settings", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                        .setNegativeButton("Cancel", null).show();
                return;
            }
        }
        long now = System.currentTimeMillis();
        boolean saved = prefs.edit()
                .putBoolean("ride_active", true)
                .putLong("ride_started", now)
                .putFloat("ride_meters", 0f)
                .putFloat("ride_speed_mps", 0f)
                .putInt("ride_fixes", 0)
                .remove("ride_last_time")
                .remove("ride_last_lat")
                .remove("ride_last_lon")
                .putLong("ride_rate_base", getBase())
                .putLong("ride_rate_km", getPerKm())
                .putLong("ride_rate_min", getMinimum())
                .commit();
        if (!saved) { toast("Unable to save ride session. Try again."); return; }
        try {
            Intent start = new Intent(this, RideTrackingService.class).setAction(RideTrackingService.START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(start);
            else startService(start);
            startFloatingMeter();
            render();
        } catch (Exception ex) {
            prefs.edit().putBoolean("ride_active", false).apply();
            new AlertDialog.Builder(this).setTitle("Couldn't start tracking")
                    .setMessage("Check location permissions and device settings. " + ex.getMessage())
                    .setPositiveButton("OK", null).show();
            render();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) attemptStartRide();
            else toast("Precise location is needed to measure distance; no ride started.");
        }
    }

    private void endRide() {
        if (!prefs.getBoolean("ride_active", false)) return;
        if (prefs.getInt("ride_fixes", 0) < 2) {
            new AlertDialog.Builder(this).setTitle("GPS is not ready")
                    .setMessage("Fewer than two location fixes were recorded. This fare may not reflect the distance traveled. End this ride anyway?")
                    .setPositiveButton("End anyway", (d, w) -> finishRide())
                    .setNegativeButton("Keep tracking", null).show();
        } else finishRide();
    }
    private void finishRide() {
        long started = prefs.getLong("ride_started", System.currentTimeMillis());
        long ended = System.currentTimeMillis();
        double meters = prefs.getFloat("ride_meters", 0f);
        long amount = currentFare();
        String id = "RP" + ended;
        try { store.add(id, started, ended, meters, amount); }
        catch (Exception ex) {
            new AlertDialog.Builder(this).setTitle("Ride couldn't be saved")
                    .setMessage("Ride tracking is still active. Please retry. " + ex.getMessage())
                    .setPositiveButton("OK", null).show();
            return;
        }
        prefs.edit().putBoolean("ride_active", false)
                .putFloat("ride_speed_mps", 0f)
                .remove("ride_waiting").remove("ride_wait_started").remove("overlay_stage")
                .remove("ride_last_time").remove("ride_last_lat")
                .remove("ride_last_lon").commit();
        startService(new Intent(this, RideTrackingService.class).setAction(RideTrackingService.STOP));
        render();
        showPayment(id, amount);
    }

    private void showPayment(String rideId, long amountPaise) {
        paymentPreviewOnly = false;
        paymentRideId = rideId;
        paymentAmountPaise = amountPaise;
        render();
    }

    private void showPaymentPreview() {
        if (!prefs.getBoolean("ride_active", false)) { toast("Start a ride first"); return; }
        paymentPreviewOnly = true;
        paymentRideId = "LIVE RIDE";
        paymentAmountPaise = currentFare();
        render();
    }

    private void showPaymentScreen() {
        String vpa = prefs.getString("upi_id", "").trim();
        String payee = prefs.getString("payee_name", "Auto Driver").trim();
        String rideId = paymentRideId;
        long amountPaise = paymentAmountPaise;

        TextView back = badge(paymentPreviewOnly ? "‹  BACK TO LIVE RIDE" : "‹  BACK TO TRIPS", GREY);
        back.setOnClickListener(v -> closePayment());
        content.addView(back, new LinearLayout.LayoutParams(-2, -2));
        sectionTitle(content, paymentPreviewOnly ? "LIVE FARE QR" : "FARE COLLECTION",
                paymentPreviewOnly ? "Show current fare" : "Collect payment",
                paymentPreviewOnly ? "Preview only · ride is still tracking" : "Ride " + rideId);

        LinearLayout amountCard = vertical();
        amountCard.setGravity(Gravity.CENTER_HORIZONTAL);
        amountCard.setPadding(dp(20), dp(22), dp(20), dp(22));
        amountCard.setBackground(gradient(YELLOW, AMBER, 24));
        amountCard.addView(text("TOTAL RIDE FARE", 11, BG, true));
        TextView amount = text(FareEngine.currency(amountPaise), 45, BG, true);
        amount.setGravity(Gravity.CENTER);
        amountCard.addView(amount, margin(-1, -2, 4, 0));
        amountCard.addView(text("Show this amount to your customer", 12, Color.rgb(70, 53, 0), false));
        content.addView(amountCard, margin(-1, -2, 14, 12));

        LinearLayout qrCard = card();
        qrCard.setGravity(Gravity.CENTER_HORIZONTAL);
        if (validVpa(vpa)) {
            try {
                Uri upi = new Uri.Builder().scheme("upi").authority("pay")
                        .appendQueryParameter("pa", vpa)
                        .appendQueryParameter("pn", payee.isEmpty() ? "Auto Driver" : payee)
                        .appendQueryParameter("am", String.format(Locale.US, "%.2f", amountPaise / 100.0))
                        .appendQueryParameter("cu", "INR")
                        .appendQueryParameter("tn", "Auto ride " + rideId)
                        .build();
                ImageView qr = new ImageView(this);
                qr.setImageBitmap(makeQr(upi.toString(), 650));
                qr.setAdjustViewBounds(true);
                qr.setBackground(shape(Color.WHITE, 18, 0));
                qr.setPadding(dp(12), dp(12), dp(12), dp(12));
                qrCard.addView(qr, margin(dp(248), dp(248), 2, 12));
                TextView scan = text("SCAN TO PAY", 15, WHITE, true);
                scan.setGravity(Gravity.CENTER);
                qrCard.addView(scan);
                TextView payeeText = text(vpa, 12, GREY, false);
                payeeText.setGravity(Gravity.CENTER);
                qrCard.addView(payeeText, margin(-1, -2, 5, 0));
            } catch (Exception ex) {
                qrCard.addView(text("QR couldn't be generated. Check your UPI ID under Settings.", 14, Color.rgb(255, 110, 110), false));
            }
        } else {
            qrCard.addView(text("ADD UPI ID", 15, YELLOW, true));
            note(qrCard, "Set a valid UPI ID in Settings to generate a payment QR. You can still record a cash payment.");
        }
        content.addView(qrCard, margin(-1, -2, 0, 12));

        if (paymentPreviewOnly) {
            note(content, "This is a live preview. The final fare is saved only after Dropped or End Ride; payment is not bank-verified.");
            content.addView(button("BACK TO LIVE RIDE", true, this::closePayment), margin(-1, -2, 8, 0));
        } else {
            LinearLayout paymentActions = horizontal();
            Button cash = button("CASH RECEIVED", false, () -> manualPayment(rideId, "CASH"));
            Button upi = button("UPI RECEIVED", true, () -> manualPayment(rideId, "UPI"));
            paymentActions.addView(cash, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams upiParams = new LinearLayout.LayoutParams(0, -2, 1);
            upiParams.setMargins(dp(8), 0, 0, 0);
            paymentActions.addView(upi, upiParams);
            content.addView(paymentActions);
            note(content, "Mark payment only after checking your UPI or bank app. QR payment is not automatically verified.");
            Button pending = button("SAVE AS PAYMENT PENDING", false, () -> {
                toast("Ride saved as payment pending");
                closePayment();
            });
            content.addView(pending, margin(-1, -2, 8, 0));
        }
    }

    private void closePayment() {
        boolean preview = paymentPreviewOnly;
        paymentPreviewOnly = false;
        paymentRideId = null;
        paymentAmountPaise = 0;
        activeTab = preview ? 0 : 1;
        render();
    }
    private Bitmap makeQr(String data, int px) throws Exception {
        EnumMap<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix encoded = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, px, px, hints);
        int[] pixels = new int[px * px];
        for (int y = 0; y < px; y++) for (int x = 0; x < px; x++) {
            pixels[y * px + x] = encoded.get(x, y) ? Color.BLACK : Color.WHITE;
        }
        return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888);
    }
    private boolean validVpa(String vpa) {
        return vpa.matches("[a-zA-Z0-9.\\-_]{2,}@[a-zA-Z0-9.\\-_]{2,}");
    }
    private void manualPayment(String id, String method) {
        if (store.markPaid(id, method)) {
            toast("Marked " + method + " received (manual record, not bank-verified)");
            closePayment();
        } else toast("Could not update the payment. Please try from History.");
    }

    private void startFloatingMeter() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true;
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(permission);
            toast("Allow display over other apps to use the floating fare meter");
            return;
        }
        try {
            Intent show = new Intent(this, FloatingOverlayService.class).setAction(FloatingOverlayService.SHOW);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(show); else startService(show);
            toast("Floating meter enabled · long-press it for ride actions");
        } catch (Exception ex) {
            toast("Floating meter could not start. Open the app and try again.");
        }
    }

    private void stopFloatingMeter() {
        startService(new Intent(this, FloatingOverlayService.class).setAction(FloatingOverlayService.HIDE));
    }

    private void handleOverlayIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("overlay_action");
        if (action == null) return;
        intent.removeExtra("overlay_action");
        if ("start".equals(action)) attemptStartRide();
        else if ("dropped".equals(action)) endRide();
        else if ("fare_qr".equals(action)) showPaymentPreview();
    }

    private void showHistory() {
        sectionTitle(content, "TRIP LEDGER", "Your ride history", "Fares and payment status saved on this phone.");
        JSONArray trips = store.all();
        long paid = 0, pending = 0;
        for (int i = 0; i < trips.length(); i++) {
            JSONObject t = trips.optJSONObject(i);
            if (t == null) continue;
            if (t.optBoolean("paid")) paid += t.optLong("amountPaise");
            else pending += t.optLong("amountPaise");
        }
        LinearLayout total = card();
        total.setBackground(gradient(Color.rgb(54, 43, 0), Color.rgb(26, 24, 17), 22));
        total.addView(text("TOTAL RIDES", 10, YELLOW, true));
        total.addView(text(String.valueOf(trips.length()), 34, WHITE, true), margin(-1, -2, 4, 0));
        content.addView(total, margin(-1, -2, 8, 10));

        LinearLayout summary = horizontal();
        LinearLayout paidCard = card();
        paidCard.addView(text("RECEIVED", 10, SUCCESS, true));
        paidCard.addView(text(FareEngine.currency(paid), 20, WHITE, true), margin(-1, -2, 6, 0));
        LinearLayout pendingCard = card();
        pendingCard.addView(text("PENDING", 10, AMBER, true));
        pendingCard.addView(text(FareEngine.currency(pending), 20, WHITE, true), margin(-1, -2, 6, 0));
        summary.addView(paidCard, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams pendingParams = new LinearLayout.LayoutParams(0, -2, 1);
        pendingParams.setMargins(dp(9), 0, 0, 0);
        summary.addView(pendingCard, pendingParams);
        content.addView(summary, margin(-1, -2, 0, 14));
        if (trips.length() == 0) {
            LinearLayout empty = card();
            empty.addView(text("Your first trip will appear here.", 17, WHITE, true));
            note(empty, "Trips are stored locally on this device. Back up before clearing app data or changing phones.");
            content.addView(empty);
            return;
        }
        for (int i = 0; i < trips.length(); i++) {
            JSONObject ride = trips.optJSONObject(i);
            if (ride == null) continue;
            LinearLayout item = card();
            item.addView(text("AUTO RIDE  ·  " + ride.optString("id"), 10, YELLOW, true));
            LinearLayout row = horizontal();
            row.addView(text(FareEngine.currency(ride.optLong("amountPaise")), 24, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(badge(ride.optBoolean("paid") ? "●  RECEIVED" : "○  PENDING",
                    ride.optBoolean("paid") ? SUCCESS : AMBER));
            item.addView(row, margin(-1, -2, 8, 6));
            item.addView(text(String.format(Locale.US, "%.2f km", ride.optLong("meters") / 1000.0) + "  ·  "
                    + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(ride.optLong("ended")),
                    13, GREY, false));
            item.setOnClickListener(v -> details(ride));
            content.addView(item, margin(-1, -2, 2, 10));
        }
        note(content, "Payment status is self-recorded. It is not automatic UPI/bank confirmation.");
    }
    private void details(JSONObject t) {
        String info = "Distance: " + String.format(Locale.US, "%.2f km", t.optLong("meters") / 1000.0)
                + "\nStarted: " + DateFormat.getDateTimeInstance().format(t.optLong("started"))
                + "\nEnded: " + DateFormat.getDateTimeInstance().format(t.optLong("ended"))
                + "\nAmount: " + FareEngine.currency(t.optLong("amountPaise"))
                + "\nPayment: " + (t.optBoolean("paid") ? t.optString("paymentMethod") + " (self-recorded)" : "Pending");
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(t.optString("id"))
                .setMessage(info).setNegativeButton("Close", null);
        if (!t.optBoolean("paid")) {
            dialog.setPositiveButton("Show payment QR", (d, w) -> showPayment(t.optString("id"), t.optLong("amountPaise")));
        }
        dialog.show();
    }

    private EditText numberField(String title, long valuePaise, LinearLayout parent) {
        parent.addView(text(title, 13, GREY, true), margin(-1, -2, 12, 5));
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setText(String.format(Locale.US, "%.2f", valuePaise / 100.0));
        field.setSelectAllOnFocus(true);
        field.setTextColor(WHITE);
        field.setTextSize(19);
        field.setHintTextColor(GREY);
        field.setBackground(shape(PANEL_RAISED, 14, LINE));
        field.setPadding(dp(13), dp(12), dp(13), dp(12));
        parent.addView(field, margin(-1, -2, 3, 12));
        return field;
    }
    private EditText plainField(String label, String value, LinearLayout parent) {
        parent.addView(text(label, 13, GREY, true), margin(-1, -2, 12, 5));
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(value);
        field.setTextColor(WHITE);
        field.setTextSize(17);
        field.setHintTextColor(GREY);
        field.setBackground(shape(PANEL_RAISED, 14, LINE));
        field.setPadding(dp(13), dp(12), dp(13), dp(12));
        parent.addView(field, margin(-1, -2, 3, 12));
        return field;
    }
    private long money(EditText e) {
        double input = Double.parseDouble(e.getText().toString().trim());
        if (!Double.isFinite(input) || input < 0 || input > 100000) throw new IllegalArgumentException("Enter a valid amount from ₹0 to ₹100,000.");
        return Math.round(input * 100);
    }

    /** First-launch setup. The video makes an editable manual fare card the first step. */
    private void showSetup() {
        LinearLayout shell = vertical();
        shell.setBackgroundColor(BG);
        shell.setFitsSystemWindows(true);
        setContentView(shell);
        LinearLayout brand = vertical();
        brand.setBackground(gradient(YELLOW, AMBER, 0));
        brand.setPadding(dp(23), dp(28), dp(23), dp(25));
        TextView setupAuto = text("AUTO", 11, YELLOW, true);
        setupAuto.setGravity(Gravity.CENTER);
        setupAuto.setBackground(shape(BG, 12, 0));
        brand.addView(setupAuto, new LinearLayout.LayoutParams(dp(52), dp(52)));
        brand.addView(text("RIDERS PAY", 31, BG, true), margin(-1, -2, 14, 0));
        brand.addView(text("YOUR DIGITAL AUTO PARTNER", 11, Color.rgb(65, 48, 0), true));
        shell.addView(brand);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout setup = vertical();
        setup.setPadding(dp(18), dp(23), dp(18), dp(30));
        scroll.addView(setup);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setup.addView(badge("3 QUICK STEPS", YELLOW), new LinearLayout.LayoutParams(-2, -2));
        setup.addView(text("Let's set up your auto.", 28, WHITE, true), margin(-1, -2, 11, 3));
        note(setup, "Add your details, fare card and payment ID. Everything stays safely on this phone.");

        LinearLayout profile = card();
        profile.addView(text("01  DRIVER DETAILS", 14, YELLOW, true));
        EditText driver = plainField("Your name", "", profile);
        EditText vehicle = plainField("Auto registration (optional)", "", profile);
        setup.addView(profile, margin(-1, -2, 13, 13));

        LinearLayout rates = card();
        rates.addView(text("02  YOUR FARE CARD", 14, YELLOW, true));
        note(rates, "Example figures only. Enter fares permitted under applicable rules in your area.");
        EditText base = numberField("Base fare (₹)", getBase(), rates);
        EditText perKm = numberField("Distance charge (₹/km)", getPerKm(), rates);
        EditText minimum = numberField("Minimum fare (₹)", getMinimum(), rates);
        setup.addView(rates, margin(-1, -2, 0, 13));

        LinearLayout payment = card();
        payment.addView(text("03  PAYMENT DETAILS", 14, YELLOW, true));
        note(payment, "Optional during setup. Add your own UPI ID for a fare-filled payment QR after a ride.");
        EditText vpa = plainField("Your UPI ID (optional)", "", payment);
        setup.addView(payment, margin(-1, -2, 0, 14));

        setup.addView(button("OPEN MY DRIVER DASHBOARD  →", true, () -> {
            String driverName = driver.getText().toString().trim();
            String vpaValue = vpa.getText().toString().trim();
            if (driverName.length() < 2 || driverName.length() > 60) {
                toast("Enter your name (2–60 characters)"); return;
            }
            if (!vpaValue.isEmpty() && !validVpa(vpaValue)) {
                toast("Enter a valid UPI ID or leave it blank for now"); return;
            }
            try {
                long b = money(base), k = money(perKm), m = money(minimum);
                boolean ok = prefs.edit()
                        .putString("driver_name", driverName)
                        .putString("vehicle_number", vehicle.getText().toString().trim())
                        .putString("payee_name", driverName)
                        .putString("upi_id", vpaValue)
                        .putLong("rate_base", b).putLong("rate_km", k).putLong("rate_min", m)
                        .putBoolean("setup_complete", true).commit();
                if (!ok) { toast("Setup couldn't be saved. Please retry."); return; }
                render();
            } catch (Exception ex) {
                toast("Enter valid amounts in all three rate fields");
            }
        }));
        note(setup, "Location permission is requested only when you begin a ride. Payment status is recorded manually, not verified by a bank.");
    }

    private void showSettings() {
        sectionTitle(content, "APP SETTINGS", "Make it yours", "Profile, fare card and payment preferences.");
        LinearLayout profile = card();
        profile.addView(text("01  DRIVER PROFILE", 15, YELLOW, true));
        EditText driver = plainField("Driver name", prefs.getString("driver_name", ""), profile);
        EditText vehicle = plainField("Auto registration (optional)", prefs.getString("vehicle_number", ""), profile);
        profile.addView(button("Save driver details", true, () -> {
            String nameValue = driver.getText().toString().trim();
            if (nameValue.length() < 2 || nameValue.length() > 60) {
                toast("Enter your name (2–60 characters)"); return;
            }
            if (prefs.edit().putString("driver_name", nameValue)
                    .putString("vehicle_number", vehicle.getText().toString().trim()).commit()) {
                toast("Driver details saved"); render();
            } else toast("Could not save driver details");
        }));
        content.addView(profile, margin(-1, -2, 2, 15));
        LinearLayout rates = card();
        rates.addView(text("02  MANUAL RATE CARD", 15, YELLOW, true));
        note(rates, "Illustrative initial rates only. Replace these with fares permitted in your area.");
        EditText base = numberField("Base fare (₹)", getBase(), rates);
        EditText perKm = numberField("Distance charge (₹ per km)", getPerKm(), rates);
        EditText minimum = numberField("Minimum fare (₹)", getMinimum(), rates);
        rates.addView(button("Save rate card", true, () -> {
            try {
                if (prefs.getBoolean("ride_active", false)) { toast("End the active ride before changing fares"); return; }
                long b = money(base), k = money(perKm), m = money(minimum);
                if (!prefs.edit().putLong("rate_base", b).putLong("rate_km", k).putLong("rate_min", m).commit()) {
                    toast("Could not save the rate card."); return;
                }
                toast("Rate card saved locally");
                render();
            } catch (Exception ex) { toast("Enter valid amounts in all rate fields"); }
        }));
        content.addView(rates, margin(-1, -2, 2, 15));

        LinearLayout upi = card();
        upi.addView(text("03  PAYMENT QR", 15, YELLOW, true));
        note(upi, "The QR contains only your payee UPI ID and the ride fare. It does not collect a platform commission.");
        EditText name = plainField("UPI payee name", prefs.getString("payee_name", ""), upi);
        EditText vpa = plainField("Your UPI ID (e.g. driver@bank)", prefs.getString("upi_id", ""), upi);
        upi.addView(button("Save UPI details", true, () -> {
            String id = vpa.getText().toString().trim();
            if (!validVpa(id)) { toast("Enter a valid UPI ID in name@provider format"); return; }
            String payeeName = name.getText().toString().trim();
            if (payeeName.length() < 2) { toast("Enter your UPI payee name"); return; }
            if (prefs.edit().putString("upi_id", id).putString("payee_name", payeeName).commit()) {
                toast("UPI details saved on this device"); render();
            } else toast("Failed to save UPI details");
        }));
        content.addView(upi, margin(-1, -2, 2, 15));

        LinearLayout floating = card();
        floating.addView(text("04  FLOATING DRIVER METER", 15, YELLOW, true));
        note(floating, "Shows speed, distance and live fare over navigation apps. Long-press for Start, Arrived, Picked, Waiting, Dropped and Fare QR.");
        floating.addView(button(Settings.canDrawOverlays(this) ? "SHOW FLOATING METER" : "ALLOW & SHOW FLOATING METER",
                true, this::startFloatingMeter), margin(-1, -2, 8, 0));
        floating.addView(button("Hide floating meter", false, this::stopFloatingMeter), margin(-1, -2, 8, 0));
        content.addView(floating, margin(-1, -2, 2, 15));

        LinearLayout privacy = card();
        privacy.addView(text("ABOUT RIDERS PAY BETA", 15, YELLOW, true));
        note(privacy, "Works without an account. Your fare card and trip history remain on this device. Precise location is used only while a ride is active; a foreground notification remains visible.");
        note(privacy, "Payments are made directly to the driver's UPI ID. The app does not verify deposits or automatically deduct commissions.");
        note(privacy, "From the presentation, a customer booking app, dispatch, the proposed 3% platform commission (with cap/incentive concept) and Smart Meter POS require separate later development and, where applicable, payment-provider approval.");
        content.addView(privacy);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOverlayIntent(intent);
    }

    @Override public void onBackPressed() {
        if (paymentRideId != null) {
            closePayment();
            return;
        }
        if (activeTab != 0) {
            activeTab = 0;
            render();
            return;
        }
        super.onBackPressed();
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
