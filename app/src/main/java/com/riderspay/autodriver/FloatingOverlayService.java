package com.riderspay.autodriver;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Draggable edge-aware floating meter with a long-press radial action menu. */
public final class FloatingOverlayService extends Service {
    public static final String SHOW = "com.riderspay.autodriver.SHOW_FLOATING_METER";
    public static final String HIDE = "com.riderspay.autodriver.HIDE_FLOATING_METER";
    private static final String CHANNEL = "floating_meter";
    private static final int NOTIFICATION_ID = 1402;
    private static final int YELLOW = Color.rgb(255, 204, 0);
    private static final int DARK = Color.rgb(12, 14, 17);

    private SharedPreferences prefs;
    private WindowManager wm;
    private FrameLayout bubble;
    private FrameLayout menu;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams menuParams;
    private TextView speedText, distanceText, fareText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean longPressed;
    private boolean moved;
    private float downRawX, downRawY;
    private int downX, downY;

    private final Runnable longPress = () -> {
        if (!moved && bubble != null) {
            longPressed = true;
            toggleMenu();
            bubble.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }
    };
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateMetrics();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Floating fare meter", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION_ID, notification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && HIDE.equals(intent.getAction())) {
            removeViews();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (bubble == null) showBubble();
        prefs.edit().putBoolean("overlay_visible", true).apply();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        return START_STICKY;
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent action = PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("Riders Pay floating meter")
                .setContentText("Long-press the yellow meter for ride actions")
                .setSmallIcon(R.drawable.riders_pay_logo)
                .setContentIntent(action)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable shape(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private TextView label(String value, float sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private WindowManager.LayoutParams params(int width, int height) {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        return new WindowManager.LayoutParams(width, height, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
    }

    private void showBubble() {
        bubble = new FrameLayout(this);
        bubble.setPadding(dp(5), dp(5), dp(5), dp(5));
        bubble.setBackground(shape(Color.BLACK, 24, Color.rgb(255, 231, 112), 2));
        bubble.setElevation(dp(14));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(7), dp(6), dp(7), dp(7));
        panel.setBackground(shape(YELLOW, 19, 0, 0));
        bubble.addView(panel, new FrameLayout.LayoutParams(-1, -1));

        speedText = label("0 km/h", 16, DARK, true);
        panel.addView(speedText, new LinearLayout.LayoutParams(-1, 0, 1.05f));
        View rule = new View(this);
        rule.setBackgroundColor(Color.argb(80, 0, 0, 0));
        panel.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        distanceText = label("0.0\nkm", 10, DARK, true);
        fareText = label("₹0\nfare", 10, DARK, true);
        bottom.addView(distanceText, new LinearLayout.LayoutParams(0, -1, 1));
        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(75, 0, 0, 0));
        bottom.addView(divider, new LinearLayout.LayoutParams(dp(1), -1));
        bottom.addView(fareText, new LinearLayout.LayoutParams(0, -1, 1));
        panel.addView(bottom, new LinearLayout.LayoutParams(-1, 0, 1));

        bubbleParams = params(dp(132), dp(94));
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = prefs.getInt("overlay_x", dp(14));
        bubbleParams.y = prefs.getInt("overlay_y", dp(180));
        bubble.setOnTouchListener(this::handleTouch);
        wm.addView(bubble, bubbleParams);
        updateMetrics();
    }

    private boolean handleTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = bubbleParams.x;
                downY = bubbleParams.y;
                longPressed = false;
                moved = false;
                handler.postDelayed(longPress, 480L);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (Math.abs(dx) > dp(7) || Math.abs(dy) > dp(7)) {
                    moved = true;
                    handler.removeCallbacks(longPress);
                }
                bubbleParams.x = clamp(downX + Math.round(dx), 0,
                        getResources().getDisplayMetrics().widthPixels - bubbleParams.width);
                bubbleParams.y = clamp(downY + Math.round(dy), 0,
                        getResources().getDisplayMetrics().heightPixels - bubbleParams.height);
                wm.updateViewLayout(bubble, bubbleParams);
                if (menu != null) positionMenu();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPress);
                prefs.edit().putInt("overlay_x", bubbleParams.x).putInt("overlay_y", bubbleParams.y).apply();
                if (event.getActionMasked() == MotionEvent.ACTION_UP && !moved && !longPressed) openMain(null);
                return true;
            default:
                return false;
        }
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(value, max)); }

    private void toggleMenu() {
        if (menu != null) closeMenu();
        else showMenu();
    }

    private void showMenu() {
        boolean right = opensRight();
        menu = new FrameLayout(this);
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.addView(new GlassArcView(this, right), new FrameLayout.LayoutParams(-1, -1));
        String[] icons = {"▶", "⚑", "●", "⌛", "⚐", "▣"};
        String[] names = {"Start", "Arrived", "Picked", "Waiting", "Dropped", "Fare QR"};
        Runnable[] actions = {
                this::startRide,
                () -> stage("ARRIVED", "Arrived marked"),
                () -> stage("PICKED", "Passenger picked up"),
                this::toggleWaiting,
                () -> { stage("DROPPED", "Passenger dropped"); openMain("dropped"); },
                () -> openMain("fare_qr")
        };
        int size = dp(54);
        int width = dp(292), height = dp(320);
        float cx = right ? dp(18) : width - dp(18);
        float cy = height / 2f;
        float radius = dp(116);
        for (int i = 0; i < names.length; i++) {
            double degrees = right ? -67 + (i * 134.0 / 5.0) : 247 - (i * 134.0 / 5.0);
            double radians = Math.toRadians(degrees);
            TextView action = label(icons[i] + "\n" + names[i], names[i].length() > 6 ? 8 : 9, Color.WHITE, true);
            action.setLineSpacing(0, .88f);
            action.setBackground(shape(Color.argb(235, 28, 31, 36), 28, YELLOW, 1));
            action.setElevation(dp(8));
            final Runnable callback = actions[i];
            action.setOnClickListener(v -> { callback.run(); closeMenu(); });
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(size, size);
            p.leftMargin = Math.round(cx + radius * (float)Math.cos(radians) - size / 2f);
            p.topMargin = Math.round(cy + radius * (float)Math.sin(radians) - size / 2f);
            menu.addView(action, p);
        }
        menuParams = params(width, height);
        menuParams.gravity = Gravity.TOP | Gravity.START;
        positionMenu();
        wm.addView(menu, menuParams);
    }

    private boolean opensRight() {
        return bubbleParams.x + bubbleParams.width / 2
                < getResources().getDisplayMetrics().widthPixels / 2;
    }

    private void positionMenu() {
        if (menuParams == null || bubbleParams == null) return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int overlap = dp(18);
        menuParams.x = opensRight()
                ? bubbleParams.x + bubbleParams.width - overlap
                : bubbleParams.x - menuParams.width + overlap;
        menuParams.y = bubbleParams.y - (menuParams.height - bubbleParams.height) / 2;
        menuParams.x = clamp(menuParams.x, 0, screenWidth - menuParams.width);
        menuParams.y = clamp(menuParams.y, 0, screenHeight - menuParams.height);
        if (menu != null && menu.isAttachedToWindow()) wm.updateViewLayout(menu, menuParams);
    }

    private void closeMenu() {
        if (menu == null) return;
        try { wm.removeView(menu); } catch (Exception ignored) { }
        menu = null;
        menuParams = null;
    }

    private void startRide() {
        if (prefs.getBoolean("ride_active", false)) {
            toast("Ride is already active");
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            openMain("start");
            return;
        }
        long now = System.currentTimeMillis();
        prefs.edit()
                .putBoolean("duty_on", true)
                .putBoolean("ride_active", true)
                .putLong("ride_started", now)
                .putFloat("ride_meters", 0f)
                .putFloat("ride_speed_mps", 0f)
                .putInt("ride_fixes", 0)
                .putString("overlay_stage", "STARTED")
                .remove("ride_last_time").remove("ride_last_lat").remove("ride_last_lon")
                .putLong("ride_rate_base", prefs.getLong("rate_base", 4000))
                .putLong("ride_rate_km", prefs.getLong("rate_km", 1500))
                .putLong("ride_rate_min", prefs.getLong("rate_min", 4000))
                .apply();
        Intent track = new Intent(this, RideTrackingService.class).setAction(RideTrackingService.START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(track); else startService(track);
        toast("Ride started");
        updateMetrics();
    }

    private void stage(String stage, String message) {
        if (!prefs.getBoolean("ride_active", false)) {
            toast("Start a ride first");
            return;
        }
        prefs.edit().putString("overlay_stage", stage).apply();
        toast(message);
    }

    private void toggleWaiting() {
        if (!prefs.getBoolean("ride_active", false)) {
            toast("Start a ride first");
            return;
        }
        boolean waiting = !prefs.getBoolean("ride_waiting", false);
        prefs.edit().putBoolean("ride_waiting", waiting)
                .putLong("ride_wait_started", waiting ? System.currentTimeMillis() : 0L)
                .putString("overlay_stage", waiting ? "WAITING" : "PICKED")
                .apply();
        toast(waiting ? "Waiting started" : "Waiting ended");
    }

    private void openMain(String action) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (action != null) open.putExtra("overlay_action", action);
        startActivity(open);
    }

    private void updateMetrics() {
        if (speedText == null) return;
        boolean active = prefs.getBoolean("ride_active", false);
        float meters = active ? prefs.getFloat("ride_meters", 0f) : 0f;
        float speed = active ? prefs.getFloat("ride_speed_mps", 0f) * 3.6f : 0f;
        long base = active ? prefs.getLong("ride_rate_base", prefs.getLong("rate_base", 4000)) : prefs.getLong("rate_base", 4000);
        long perKm = active ? prefs.getLong("ride_rate_km", prefs.getLong("rate_km", 1500)) : prefs.getLong("rate_km", 1500);
        long minimum = active ? prefs.getLong("ride_rate_min", prefs.getLong("rate_min", 4000)) : prefs.getLong("rate_min", 4000);
        long fare = FareEngine.calculatePaise(base, perKm, minimum, meters);
        speedText.setText(String.format(Locale.US, "%.0f km/h", speed));
        distanceText.setText(String.format(Locale.US, "%.1f\nkm", meters / 1000f));
        fareText.setText(String.format(Locale.US, "₹%.0f\nfare", fare / 100.0));
        bubble.setAlpha(active ? 1f : .88f);
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    private void removeViews() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(longPress);
        closeMenu();
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) { }
            bubble = null;
        }
        if (prefs != null) prefs.edit().putBoolean("overlay_visible", false).apply();
    }

    @Override public void onDestroy() {
        removeViews();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private final class GlassArcView extends View {
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean right;

        GlassArcView(Context context, boolean right) {
            super(context);
            this.right = right;
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(dp(62));
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setColor(Color.argb(92, 225, 232, 240));
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            arc.setShadowLayer(dp(18), 0, 0, Color.argb(120, 255, 204, 0));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = dp(34);
            float cx = right ? dp(18) : getWidth() - dp(18);
            float cy = getHeight() / 2f;
            float radius = dp(116);
            RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(oval, right ? -70 : 110, 140, false, arc);
            Paint edge = new Paint(arc);
            edge.clearShadowLayer();
            edge.setStrokeWidth(dp(2));
            edge.setColor(Color.argb(180, 255, 255, 255));
            canvas.drawArc(oval, right ? -70 : 110, 140, false, edge);
        }
    }
}
