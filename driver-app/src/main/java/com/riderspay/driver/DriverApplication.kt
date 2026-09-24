package com.riderspay.driver
import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
class DriverApplication:Application(){override fun onCreate(){super.onCreate();if(com.google.firebase.FirebaseApp.initializeApp(this)!=null)FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())}}
