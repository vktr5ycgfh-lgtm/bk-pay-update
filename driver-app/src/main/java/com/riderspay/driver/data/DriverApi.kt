package com.riderspay.driver.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class DriverApi(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    val uid: String get() = requireNotNull(FirebaseAuth.getInstance().currentUser?.uid) { "Driver is not signed in" }

    fun ride(rideId: String): Flow<Ride?> = callbackFlow {
        val registration = db.collection("rides").document(rideId).addSnapshotListener { snap, error ->
            if (error != null) close(error) else trySend(snap?.toObject(Ride::class.java)?.copy(rideId = rideId))
        }
        awaitClose { registration.remove() }
    }

    suspend fun accept(rideId: String) = call("acceptRide", mapOf("rideId" to rideId))
    suspend fun decline(rideId: String) = call("declineRide", mapOf("rideId" to rideId))
    suspend fun transition(rideId: String, action: String) = call("transitionRide", mapOf("rideId" to rideId, "action" to action))
    suspend fun joinStand(standId: String) = call("joinStand", mapOf("standId" to standId))
    suspend fun leaveStand(standId: String) = call("leaveStand", mapOf("standId" to standId))

    private suspend fun call(name: String, payload: Map<String, Any?>): Map<*, *> {
        val result = functions.getHttpsCallable(name).call(payload).await().getData()
        return result as? Map<*, *> ?: emptyMap<String, Any>()
    }
}
