package com.riderspay.consumer
import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
class NeedRideApplication:Application(){override fun onCreate(){super.onCreate();if(com.google.firebase.FirebaseApp.initializeApp(this)!=null)FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())}}
