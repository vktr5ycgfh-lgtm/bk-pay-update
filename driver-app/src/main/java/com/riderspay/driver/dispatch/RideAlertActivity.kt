package com.riderspay.driver.dispatch

import android.app.*
import android.media.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.riderspay.driver.R
import com.riderspay.driver.data.DriverApi
import kotlinx.coroutines.*

class RideAlertActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private val api = DriverApi()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val rideId=intent.getStringExtra("rideId") ?: run { finish(); return }
        playLoop(); setContent { Alert(rideId) }
    }

    @Composable private fun Alert(rideId:String) {
        var seconds by remember { mutableIntStateOf(20) }; var busy by remember { mutableStateOf(false) }
        LaunchedEffect(Unit){while(seconds>0){delay(1_000);seconds--};stopAndFinish()}
        MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFFFCC00))){
            Column(Modifier.fillMaxSize().background(Color(0xFF0C0E11)).padding(24.dp),verticalArrangement=Arrangement.SpaceBetween){
                Column{Text("NEW RIDE REQUEST",color=Color(0xFFFFCC00),fontWeight=FontWeight.Bold);Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(70.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(progress={seconds/20f},modifier=Modifier.fillMaxSize(),color=Color(0xFFFFCC00));Text("$seconds",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(18.dp));Column{Text("₹${intent.getStringExtra("fare") ?: "--"}",fontSize=38.sp,color=Color.White,fontWeight=FontWeight.Black);Text("${intent.getStringExtra("distanceKm") ?: "--"} km estimated",color=Color.LightGray)}}
                    Spacer(Modifier.height(28.dp));Place("PICKUP",intent.getStringExtra("pickup") ?: "Pickup");Spacer(Modifier.height(18.dp));Place("DROP",intent.getStringExtra("drop") ?: "Drop destination")}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Button(onClick={busy=true;lifecycleScope.launch{runCatching{api.decline(rideId)};stopAndFinish()}},enabled=!busy,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF3A3D42)),modifier=Modifier.weight(1f).height(60.dp)){Text("DECLINE",color=Color.White)}
                    Button(onClick={busy=true;lifecycleScope.launch{runCatching{api.accept(rideId)}.onSuccess{getSharedPreferences("driver_live",MODE_PRIVATE).edit().putString("active_ride_id",rideId).putFloat("trip_meters",0f).apply()};stopAndFinish()}},enabled=!busy,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFFFCC00)),modifier=Modifier.weight(1.3f).height(60.dp)){Text("ACCEPT",color=Color.Black,fontWeight=FontWeight.Black)} }
            }
        }
    }
    @Composable private fun Place(label:String,value:String){Column(Modifier.fillMaxWidth().background(Color(0xFF202329),RoundedCornerShape(18.dp)).padding(18.dp)){Text(label,color=Color(0xFFFFCC00),fontSize=11.sp,fontWeight=FontWeight.Bold);Text(value,color=Color.White,fontSize=18.sp,fontWeight=FontWeight.SemiBold)}}
    private fun playLoop(){player=MediaPlayer.create(this,R.raw.ride_alert_chime)?.apply{isLooping=true;setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());start()}}
    private fun stopAndFinish(){player?.stop();player?.release();player=null;getSystemService(NotificationManager::class.java).cancel(intent.getStringExtra("rideId")?.hashCode() ?: 0);finish()}
    override fun onDestroy(){player?.release();player=null;super.onDestroy()}
}
