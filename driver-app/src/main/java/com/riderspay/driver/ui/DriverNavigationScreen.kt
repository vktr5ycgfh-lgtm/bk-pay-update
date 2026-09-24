package com.riderspay.driver.ui

import android.content.*
import android.graphics.*
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.riderspay.driver.BuildConfig
import com.riderspay.driver.data.Ride

@Composable fun DriverNavigationScreen(ride: Ride, live: LatLng?, bearing: Float, onAction:(String)->Unit, showQr:Boolean, dismissQr:()->Unit) {
    val camera=rememberCameraPositionState(); LaunchedEffect(live){live?.let{camera.animate(CameraUpdateFactory.newLatLngZoom(it,16f))}}
    Box(Modifier.fillMaxSize()){
        GoogleMap(Modifier.fillMaxSize(),cameraPositionState=camera,properties=MapProperties(isMyLocationEnabled=false)){
            live?.let{Marker(state=MarkerState(position=it),title="Your auto",rotation=bearing,flat=true,icon=BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))}
            Marker(state=MarkerState(position=LatLng(ride.pickup.lat,ride.pickup.lng)),title="Pickup")
            Marker(state=MarkerState(position=LatLng(ride.drop.lat,ride.drop.lng)),title="Drop")
            if(ride.encodedPolyline.isNotBlank()) Polyline(points=PolyUtil.decode(ride.encodedPolyline),color=Color(0xFFFFCC00),width=14f)
        }
        Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp),colors=CardDefaults.cardColors(containerColor=Color(0xF21A1C20))){Column(Modifier.padding(16.dp)){Text(ride.status,color=Color(0xFFFFCC00));Text(ride.pickup.address,color=Color.White);Text("→ ${ride.drop.address}",color=Color.LightGray);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("ARRIVED","PICKED","DROPPED").forEach{OutlinedButton({onAction(it)},modifier=Modifier.weight(1f)){Text(it.take(4))}}}}}
    }
    if(showQr) FareQrDialog(ride.fare,ride.rideId,dismissQr)
}

@Composable private fun FareQrDialog(farePaise:Long,rideId:String,onDismiss:()->Unit){
    val qr=remember(farePaise,rideId){val uri=Uri.Builder().scheme("upi").authority("pay").appendQueryParameter("pa",BuildConfig.UPI_ID).appendQueryParameter("pn","RIDERS PAY DRIVER").appendQueryParameter("am","%.2f".format(java.util.Locale.US,farePaise/100.0)).appendQueryParameter("cu","INR").appendQueryParameter("tn","Auto ride $rideId").build().toString(); val m=MultiFormatWriter().encode(uri,BarcodeFormat.QR_CODE,720,720);Bitmap.createBitmap(720,720,Bitmap.Config.ARGB_8888).apply{for(y in 0 until 720)for(x in 0 until 720)setPixel(x,y,if(m[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE)}}
    AlertDialog(onDismissRequest=onDismiss,confirmButton={Button(onClick=onDismiss){Text("DONE")}},title={Text("Fare ₹${"%.2f".format(farePaise/100.0)}")},text={Column{Image(qr.asImageBitmap(),"UPI payment QR",Modifier.fillMaxWidth().aspectRatio(1f));Text("Confirm receipt in your UPI app. QR generation does not verify payment.")}})
}
