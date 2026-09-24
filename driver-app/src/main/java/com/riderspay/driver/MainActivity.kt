package com.riderspay.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.LatLng
import com.riderspay.driver.data.*
import com.riderspay.driver.location.DriverLocationService
import com.riderspay.driver.overlay.FloatwareService
import com.riderspay.driver.ui.DriverNavigationScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var qrRequested by mutableStateOf(false)
    private val permission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(it[Manifest.permission.ACCESS_FINE_LOCATION]==true)startLocation()}
    override fun onCreate(state:Bundle?){super.onCreate(state);qrRequested=intent.getBooleanExtra("show_fare_qr",false);setContent{
        if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
            MaterialTheme { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement=Arrangement.Center) {
                Text("RIDERS PAY • Developer setup",style=MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Firebase is not configured. Add driver-app/google-services.json, supply Maps / stand / UPI settings, and rebuild. This APK does not provide live dispatch without a backend.")
            } }
        } else DriverApp()
    }}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);qrRequested=intent.getBooleanExtra("show_fare_qr",false)}
    private fun startLocation(){ContextCompat.startForegroundService(this,Intent(this,DriverLocationService::class.java).setAction(DriverLocationService.START))}
    private fun showFloatware(){if(!Settings.canDrawOverlays(this)){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")));return};ContextCompat.startForegroundService(this,Intent(this,FloatwareService::class.java).setAction(FloatwareService.SHOW))}

    @Composable private fun DriverApp(){
        val prefs=remember{getSharedPreferences("driver_live",MODE_PRIVATE)};var rideId by remember{mutableStateOf(prefs.getString("active_ride_id",null))};val api=remember{DriverApi()};val scope=rememberCoroutineScope()
        val ride=rideId?.let{api.ride(it).collectAsState(initial=null).value};var tick by remember{mutableIntStateOf(0)}
        LaunchedEffect(Unit){while(true){kotlinx.coroutines.delay(1_000);tick++}}
        val lat=Double.fromBits(prefs.getLong("lat",0));val lng=Double.fromBits(prefs.getLong("lng",0));val live=if(lat==0.0&&lng==0.0)null else LatLng(lat,lng);val bearing=prefs.getFloat("bearing",0f)
        MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFFFCC00))){
            if(ride!=null){LaunchedEffect(Unit){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startLocation() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))};DriverNavigationScreen(ride,live,bearing,{a->scope.launch{api.transition(ride.rideId,a);if(a=="DROPPED")qrRequested=true}},qrRequested||ride.status=="COMPLETED",{qrRequested=false;if(ride.status=="COMPLETED"){prefs.edit().remove("active_ride_id").apply();rideId=null;startService(Intent(this,DriverLocationService::class.java).setAction(DriverLocationService.STOP))}})}
            else Column(Modifier.fillMaxSize().background(Color(0xFF0C0E11)).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){Text("RIDERS PAY",color=Color(0xFFFFCC00),fontWeight=FontWeight.Black,style=MaterialTheme.typography.headlineLarge);Text("Driver command center",color=Color.White);Button(onClick={scope.launch{api.joinStand(BuildConfig.DEFAULT_STAND_ID)}},modifier=Modifier.fillMaxWidth()){Text("GO ON DUTY AT STAND")};Button(onClick={showFloatware()},modifier=Modifier.fillMaxWidth()){Text("SHOW FLOATING HUD")};Text("A signed-in Firebase driver account with role=driver is required. Stand: ${BuildConfig.DEFAULT_STAND_ID}",color=Color.LightGray)}
        }
    }
}
