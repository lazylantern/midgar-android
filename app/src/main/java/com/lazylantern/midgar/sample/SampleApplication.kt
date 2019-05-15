package com.lazylantern.midgar.sample

import android.app.Application
import com.lazylantern.midgar.MidgarTracker


class SampleApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        MidgarTracker.getInstance(this).startTracker(this)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        MidgarTracker.getInstance(this).stopTracker(this)
    }
}