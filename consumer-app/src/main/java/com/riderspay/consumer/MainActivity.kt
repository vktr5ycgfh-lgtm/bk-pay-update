package com.riderspay.consumer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.riderspay.consumer.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity(){
    override fun onCreate(state:Bundle?){super.onCreate(state);if(!Places.isInitialized())Places.initialize(applicationContext,BuildConfig.MAPS_API_KEY);setContent{NeedRideApp()}}
    @Composable private fun NeedRideApp(){val api=remember{ConsumerApi()};var rideId by remember{mutableStateOf<String?>(null)};MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFFFFCC00),onPrimary=Color.Black)){if(rideId==null)BookingScreen(api){rideId=it}else LiveRideScreen(api,rideId!!){rideId=null}}}

    @Composable private fun BookingScreen(api:ConsumerApi,onBooked:(String)->Unit){
        val scope=rememberCoroutineScope();val places=remember{PlacesSearch(this)};var pickup by remember{mutableStateOf<Point?>(null)};var drop by remember{mutableStateOf<Point?>(null)};var quote by remember{mutableStateOf<Quote?>(null)};var autos by remember{mutableStateOf(emptyList<NearbyDriver>())};var loading by remember{mutableStateOf(false)}
        val camera=rememberCameraPositionState{position=CameraPosition.fromLatLngZoom(LatLng(11.0168,76.9558),13f)}
        LaunchedEffect(pickup){pickup?.let{camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat,it.lng),14f));autos=runCatching{api.nearby(it.lat,it.lng)}.getOrDefault(emptyList())}}
        LaunchedEffect(pickup,drop){quote=if(pickup!=null&&drop!=null)runCatching{api.quote(pickup!!,drop!!)}.getOrNull() else null}
        Box(Modifier.fillMaxSize()){
            GoogleMap(Modifier.fillMaxSize(),cameraPositionState=camera){autos.forEach{Marker(state=rememberUpdatedMarkerState(LatLng(it.lat,it.lng)),title="Available auto",rotation=it.bearing,flat=true,icon=BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))};pickup?.let{Marker(state=rememberUpdatedMarkerState(LatLng(it.lat,it.lng)),title="Pickup")};drop?.let{Marker(state=rememberUpdatedMarkerState(LatLng(it.lat,it.lng)),title="Drop")};quote?.let{Polyline(PolyUtil.decode(it.encodedPolyline),color=Color(0xFFFFCC00),width=14f)}}
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp).background(Color.White,RoundedCornerShape(20.dp)).padding(12.dp)){Text("NEED RIDE",fontWeight=FontWeight.Black);PlaceField("Pickup",places){pickup=it};PlaceField("Drop-off",places){drop=it}}
            quote?.let{q->Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp)){Column(Modifier.padding(18.dp)){Text("${"%.1f".format(q.distanceMeters/1000)} km · ${q.durationSeconds/60} min");Text("Estimated fare ₹${"%.2f".format(q.farePaise/100.0)}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("Final fare follows the configured regulatory slab and metered trip.",style=MaterialTheme.typography.bodySmall);Button(onClick={loading=true;scope.launch{runCatching{api.book(BuildConfig.DEFAULT_STAND_ID,pickup!!,drop!!,q)}.onSuccess(onBooked);loading=false}},enabled=!loading,modifier=Modifier.fillMaxWidth().height(56.dp)){Text(if(loading)"SEARCHING…" else "BOOK AUTO")}}}}
        }
    }

    @Composable private fun PlaceField(label:String,places:PlacesSearch,onSelected:(Point)->Unit){var query by remember{mutableStateOf("")};var predictions by remember{mutableStateOf(emptyList<PlacePrediction>())};val scope=rememberCoroutineScope();Column{OutlinedTextField(query,{query=it;scope.launch{delay(250);if(it==query)predictions=runCatching{places.predictions(it)}.getOrDefault(emptyList())}},label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true);predictions.take(4).forEach{p->Text("${p.primary} · ${p.secondary}",Modifier.fillMaxWidth().clickable{scope.launch{val point=places.resolve(p);query=point.address;predictions=emptyList();onSelected(point)}}.padding(10.dp))}}}

    @Composable private fun LiveRideScreen(api:ConsumerApi,rideId:String,onClose:()->Unit){
        val scope=rememberCoroutineScope();val ride by api.ride(rideId).collectAsState(initial=null);val telemetry by api.telemetry(rideId).collectAsState(initial=null);val camera=rememberCameraPositionState();val autoState=rememberMarkerState(position=LatLng(11.0168,76.9558))
        LaunchedEffect(telemetry?.first){val target=telemetry?.first?:return@LaunchedEffect;val start=autoState.position;repeat(24){i->val t=(i+1)/24.0;autoState.position=LatLng(start.latitude+(target.latitude-start.latitude)*t,start.longitude+(target.longitude-start.longitude)*t);delay(40)};camera.animate(CameraUpdateFactory.newLatLng(autoState.position))}
        val r=ride?:return Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};Box(Modifier.fillMaxSize()){
            GoogleMap(Modifier.fillMaxSize(),cameraPositionState=camera){Marker(state=autoState,title="Your auto",rotation=telemetry?.second?:0f,flat=true,icon=BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));Marker(state=rememberUpdatedMarkerState(LatLng(r.pickup.lat,r.pickup.lng)),title="Pickup");Marker(state=rememberUpdatedMarkerState(LatLng(r.drop.lat,r.drop.lng)),title="Drop");if(r.encodedPolyline.isNotBlank())Polyline(PolyUtil.decode(r.encodedPolyline),color=Color(0xFFFFCC00),width=14f)}
            Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp)){Column(Modifier.padding(18.dp)){Text("Ride ${r.status}",fontWeight=FontWeight.Black);Progress(r.status);r.driverSnapshot?.let{d->Text("${d.name} · ${d.vehicleNumber} · ★ ${d.rating}");OutlinedButton(onClick={startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:${d.phone}")))}){Text("CALL DRIVER")}};Text("₹${"%.2f".format(r.fare/100.0)}",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);if(r.status in listOf("REQUESTED","ASSIGNED","ARRIVED"))OutlinedButton(onClick={scope.launch{runCatching{api.cancel(rideId)}}},modifier=Modifier.fillMaxWidth()){Text("CANCEL RIDE")};if(r.status=="COMPLETED") {FareQr(r.fare,r.rideId,r.driverSnapshot?.upiId?.takeIf{it.isNotBlank()}?:BuildConfig.UPI_ID);Button(onClick=onClose,modifier=Modifier.fillMaxWidth()){Text("DONE")}};if(r.status=="CANCELLED")Button(onClick=onClose,modifier=Modifier.fillMaxWidth()){Text("BOOK ANOTHER")}}}
        }
    }
    @Composable private fun Progress(status:String){val steps=listOf("ASSIGNED","ARRIVED","ON_TRIP","COMPLETED");val at=steps.indexOf(status);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){steps.forEachIndexed{i,s->Text(if(i<=at)"● ${s.replace("ON_TRIP","ON TRIP")}" else "○ ${s.replace("ON_TRIP","ON TRIP")}",color=if(i<=at)Color(0xFF9B7A00)else Color.Gray,style=MaterialTheme.typography.labelSmall)}}}
    @Composable private fun FareQr(fare:Long,id:String,upiId:String){val bmp=remember(fare,id,upiId){val uri=Uri.Builder().scheme("upi").authority("pay").appendQueryParameter("pa",upiId).appendQueryParameter("am","%.2f".format(java.util.Locale.US,fare/100.0)).appendQueryParameter("cu","INR").appendQueryParameter("tn","Auto ride $id").build().toString();val m=MultiFormatWriter().encode(uri,BarcodeFormat.QR_CODE,600,600);android.graphics.Bitmap.createBitmap(600,600,android.graphics.Bitmap.Config.ARGB_8888).apply{for(y in 0 until 600)for(x in 0 until 600)setPixel(x,y,if(m[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE)}};androidx.compose.foundation.Image(bmp.asImageBitmap(),"UPI QR",Modifier.fillMaxWidth().aspectRatio(1f));Text("Verify the payment in the driver's UPI app.")}
}
