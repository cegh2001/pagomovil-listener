package com.cegh.pagomovillistener

import android.app.Application
import android.util.Log

class PagoMovilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("PagoMovilApp", "Application initialized")
    }
}
