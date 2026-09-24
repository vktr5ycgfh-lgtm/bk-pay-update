package com.riderspay.consumer.data

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.*
import kotlinx.coroutines.tasks.await

data class PlacePrediction(val id:String,val primary:String,val secondary:String)
class PlacesSearch(context:Context){private val client=Places.createClient(context)
    suspend fun predictions(query:String):List<PlacePrediction>{if(query.length<3)return emptyList();val req=FindAutocompletePredictionsRequest.builder().setQuery(query).setCountries("IN").build();return client.findAutocompletePredictions(req).await().autocompletePredictions.map{PlacePrediction(it.placeId,it.getPrimaryText(null).toString(),it.getSecondaryText(null).toString())}}
    suspend fun resolve(p:PlacePrediction):Point{val req=FetchPlaceRequest.builder(p.id,listOf(Place.Field.LAT_LNG,Place.Field.FORMATTED_ADDRESS,Place.Field.NAME)).build();val place=client.fetchPlace(req).await().place;val ll=requireNotNull(place.latLng);return Point(ll.latitude,ll.longitude,place.formattedAddress?:place.name?:p.primary)}
}
