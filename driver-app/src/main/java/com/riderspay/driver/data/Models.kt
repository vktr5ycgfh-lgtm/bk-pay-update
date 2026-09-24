package com.riderspay.driver.data

import com.google.firebase.Timestamp

data class Ride(
    val rideId: String = "", val standId: String = "", val customerId: String = "",
    val driverId: String? = null, val offeredDriverId: String? = null,
    val pickup: RidePoint = RidePoint(), val drop: RidePoint = RidePoint(),
    val fare: Long = 0, val distance: Double = 0.0, val durationSeconds: Long = 0,
    val status: String = "REQUESTED", val encodedPolyline: String = "",
    val createdAt: Timestamp? = null
)

data class RidePoint(val lat: Double = 0.0, val lng: Double = 0.0, val address: String = "")
