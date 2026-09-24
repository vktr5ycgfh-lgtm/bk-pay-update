package com.riderspay.consumer.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ConsumerApi(private val db:FirebaseFirestore=FirebaseFirestore.getInstance(),private val fn:FirebaseFunctions=FirebaseFunctions.getInstance()){
    suspend fun nearby(lat:Double,lng:Double,radiusKm:Double=3.0):List<NearbyDriver>{val data=call("nearbyDrivers",mapOf("lat" to lat,"lng" to lng,"radiusKm" to radiusKm));return (data["drivers"] as? List<*>)?.mapNotNull{val d=it as? Map<*,*>?:return@mapNotNull null;NearbyDriver(d["id"].toString(),(d["lat"] as Number).toDouble(),(d["lng"] as Number).toDouble(),(d["bearing"] as? Number)?.toFloat()?:0f)}?:emptyList()}
    suspend fun quote(pickup:Point,drop:Point):Quote{val d=call("quoteRide",mapOf("pickup" to mapOf("lat" to pickup.lat,"lng" to pickup.lng),"drop" to mapOf("lat" to drop.lat,"lng" to drop.lng)));return Quote((d["farePaise"] as Number).toLong(),(d["distanceMeters"] as Number).toDouble(),(d["durationSeconds"] as Number).toLong(),d["encodedPolyline"].toString())}
    suspend fun book(standId:String,pickup:Point,drop:Point,quote:Quote):String{fun wire(p:Point)=mapOf("lat" to p.lat,"lng" to p.lng,"address" to p.address);val d=call("createRide",mapOf("standId" to standId,"pickup" to wire(pickup),"drop" to wire(drop)));return d["rideId"].toString()}
    suspend fun cancel(rideId:String)=call("cancelRide",mapOf("rideId" to rideId))
    fun ride(id:String)=callbackFlow<Ride?>{val reg=db.collection("rides").document(id).addSnapshotListener{s,e->if(e!=null)close(e)else trySend(s?.toObject(Ride::class.java)?.copy(rideId=id))};awaitClose{reg.remove()}}
    fun telemetry(id:String)=callbackFlow<Pair<com.google.android.gms.maps.model.LatLng,Float>?>{val reg=db.collection("rides").document(id).collection("telemetry").document("current").addSnapshotListener{s,e->if(e!=null)close(e)else{val g=s?.getGeoPoint("location");trySend(g?.let{com.google.android.gms.maps.model.LatLng(it.latitude,it.longitude) to ((s.getDouble("bearing")?:0.0).toFloat())})}};awaitClose{reg.remove()}}
    private suspend fun call(name:String,data:Any):Map<*,*>{return fn.getHttpsCallable(name).call(data).await().getData() as? Map<*,*>?:error("Invalid cloud response")}
}
