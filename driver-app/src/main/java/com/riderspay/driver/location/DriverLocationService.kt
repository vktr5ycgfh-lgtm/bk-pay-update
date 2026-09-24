package com.riderspay.driver.location

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.*
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.riderspay.driver.MainActivity
import com.riderspay.driver.R

class DriverLocationService : Service() {
    companion object { const val START = "driver.location.START"; const val STOP = "driver.location.STOP" }
    private lateinit var fused: FusedLocationProviderClient
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var previous: Location? = null
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) { result.lastLocation?.let(::publish) }
    }

    override fun onCreate() {
        super.onCreate(); fused = LocationServices.getFusedLocationProviderClient(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("driver_location", "Active ride location", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { fused.removeLocationUpdates(callback); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "driver_location").setSmallIcon(R.drawable.ic_auto)
            .setContentTitle("RIDERS PAY · active ride").setContentText("Sharing trip location with your commuter")
            .setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(2101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(2101, notification)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_500L)
            .setMinUpdateIntervalMillis(1_500L).setMinUpdateDistanceMeters(2f).build()
        fused.requestLocationUpdates(request, callback, mainLooper)
        return START_STICKY
    }

    private fun publish(location: Location) {
        if (location.accuracy > 60f) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        previous?.let { if (it.distanceTo(location) > 250 && location.time - it.time < 5_000) return }
        val prefs = getSharedPreferences("driver_live", MODE_PRIVATE)
        if (prefs.getString("active_ride_id", null) != null) {
            val delta = previous?.distanceTo(location)?.takeIf { it in 2f..250f } ?: 0f
            prefs.edit().putFloat("trip_meters", prefs.getFloat("trip_meters", 0f) + delta).apply()
        }
        previous = location
        prefs.edit().putFloat("speed_mps", location.speed.coerceAtLeast(0f))
            .putLong("lat", location.latitude.toBits()).putLong("lng", location.longitude.toBits())
            .putFloat("bearing", location.bearing).apply()
        val live = mapOf("location" to GeoPoint(location.latitude, location.longitude), "bearing" to location.bearing,
            "speedMps" to location.speed.coerceAtLeast(0f), "updatedAt" to Timestamp.now())
        db.collection("drivers").document(uid).set(live, SetOptions.merge())
        prefs.getString("active_ride_id", null)?.let { rideId ->
            db.collection("rides").document(rideId).collection("telemetry").document("current").set(live)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
