package com.riderspay.autodriver;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;

/** Foreground GPS ride meter. Continues while the Activity is in the background. */
public class RideTrackingService extends Service implements LocationListener {
    public static final String START = "com.riderspay.autodriver.START_RIDE";
    public static final String STOP = "com.riderspay.autodriver.STOP_RIDE";
    public static final String UPDATE = "com.riderspay.autodriver.RIDE_UPDATE";
    private static final String CHANNEL = "ride_tracking";
    private static final int NOTIFICATION_ID = 1401;
    private SharedPreferences prefs;
    private LocationManager locationManager;
    private long lastNotificationTime;

    @Override public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Active ride tracking", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            prefs.edit().putBoolean("ride_active", false).apply();
            stopTracking();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!prefs.getBoolean("ride_active", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("ride_active", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification notification = makeNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 3f, this, Looper.getMainLooper());
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000L, 5f, this, Looper.getMainLooper());
            }
        } catch (SecurityException ignored) {
            prefs.edit().putBoolean("ride_active", false).apply();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override public void onLocationChanged(Location fix) {
        if (!prefs.getBoolean("ride_active", false)) return;
        // Discard low-confidence or stale fixes and implausible auto-rickshaw speeds.
        long started = prefs.getLong("ride_started", System.currentTimeMillis());
        long time = fix.getTime();
        if (!fix.hasAccuracy() || fix.getAccuracy() > 60f || time < started - 1000L
                || time > System.currentTimeMillis() + 10000L) return;
        long previousTime = prefs.getLong("ride_last_time", 0L);
        double traveled = prefs.getFloat("ride_meters", 0f);
        if (previousTime > 0) {
            long deltaMs = time - previousTime;
            if (deltaMs <= 0) return;
            double delta = GeoMath.distanceMeters(
                    Double.longBitsToDouble(prefs.getLong("ride_last_lat", 0)),
                    Double.longBitsToDouble(prefs.getLong("ride_last_lon", 0)),
                    fix.getLatitude(), fix.getLongitude());
            if (delta > 5 && (delta / (deltaMs / 1000.0)) > 35.0) return;
            if (delta >= 5) traveled += delta;
        }
        prefs.edit()
                .putFloat("ride_meters", (float) traveled)
                .putLong("ride_last_lat", Double.doubleToRawLongBits(fix.getLatitude()))
                .putLong("ride_last_lon", Double.doubleToRawLongBits(fix.getLongitude()))
                .putLong("ride_last_time", time)
                .putInt("ride_fixes", prefs.getInt("ride_fixes", 0) + 1)
                .apply();
        sendBroadcast(new Intent(UPDATE).setPackage(getPackageName()));
        if (System.currentTimeMillis() - lastNotificationTime > 10000L) {
            lastNotificationTime = System.currentTimeMillis();
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, makeNotification());
        }
    }

    private Notification makeNotification() {
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent action = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        double km = prefs.getFloat("ride_meters", 0f) / 1000.0;
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("Riders Pay · ride in progress")
                .setContentText(String.format(java.util.Locale.US, "GPS tracking active · %.2f km", km))
                .setSmallIcon(com.riderspay.autodriver.R.drawable.ic_auto)
                .setContentIntent(action)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void stopTracking() {
        try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
    }

    @Override public void onDestroy() {
        stopTracking();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
