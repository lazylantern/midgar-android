package com.lazylantern.midgar;

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.*

open class MidgarApplication : Application(), Application.ActivityLifecycleCallbacks {

    var lastScreenHash: String = ""

    override fun onCreate() {
        super.onCreate()

        if(checkIsAppEnabled()){
            //Register activity lifecycle callback
            registerActivityLifecycleCallbacks(this)
            //Listen for Fragment manager changes

        }
    }

    companion object {
        fun registerFragmentManager(){

        }
    }

    private fun handleHierarchyChange() {

    }

    fun computeScreenHierarchyHash(): String {
        return ""
    }

    private fun checkIsAppEnabled(): Boolean {
        return true
    }

    override fun onActivityDestroyed(activity: Activity?) {
        handleHierarchyChange()
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        handleHierarchyChange()
    }

    override fun onActivityPaused(activity: Activity?) { }

    override fun onActivityResumed(activity: Activity?) { }

    override fun onActivityStarted(activity: Activity?) { }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) { }

    override fun onActivityStopped(activity: Activity?) { }
}

data class Event(val type: String, val name: String, val source: String, val timestampMs: Date)
