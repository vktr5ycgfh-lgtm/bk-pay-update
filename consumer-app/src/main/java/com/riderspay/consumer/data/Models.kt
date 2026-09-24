package com.riderspay.consumer.data

data class Point(val lat:Double=0.0,val lng:Double=0.0,val address:String="")
data class DriverSummary(val driverId:String="",val name:String="",val vehicleNumber:String="",val phone:String="",val rating:Double=0.0,val photoUrl:String="",val upiId:String="")
data class Ride(val rideId:String="",val pickup:Point=Point(),val drop:Point=Point(),val fare:Long=0,val distance:Double=0.0,val durationSeconds:Long=0,val status:String="REQUESTED",val driverId:String?=null,val encodedPolyline:String="",val driverSnapshot:DriverSummary?=null)
data class NearbyDriver(val id:String,val lat:Double,val lng:Double,val bearing:Float)
data class Quote(val farePaise:Long,val distanceMeters:Double,val durationSeconds:Long,val encodedPolyline:String)
