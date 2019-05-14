package com.lazylantern.midgar;

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import java.util.*
import kotlin.collections.HashMap

open class MidgarApplication : Application(), Application.ActivityLifecycleCallbacks {

    var lastHierarchyHash: String = ""
    var managers: HashMap<FragmentManager, FragmentManager.FragmentLifecycleCallbacks> = HashMap()

    override fun onCreate() {
        super.onCreate()

        if(checkIsAppEnabled()){
            //Register activity lifecycle callback
            registerActivityLifecycleCallbacks(this)
            //Listen for Fragment manager changes

        }
    }

    private fun handleHierarchyChange(activity: Activity?) {
        val newHierarchyHash = computeScreenHierarchyHash(activity)
        if (newHierarchyHash != lastHierarchyHash){
            Log.d(this.packageName, "Got a new hierarchy: $newHierarchyHash")
        }
    }

    private fun registerFragmentManager(activity: Activity?) {
        if (activity is AppCompatActivity){
            val fm = activity.supportFragmentManager
            val callbacks = object: FragmentManager.FragmentLifecycleCallbacks(){
                override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                    super.onFragmentStarted(fm, f)
                    handleHierarchyChange(f.activity)
                }

                override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
                    super.onFragmentStopped(fm, f)
                    handleHierarchyChange(f.activity)
                }

            }
            fm.registerFragmentLifecycleCallbacks(callbacks, true)
            managers[fm] = callbacks
        }
    }

    private fun unregisterFragmentManager(activity: Activity?) {
        if (activity is AppCompatActivity){
            val fm = activity.supportFragmentManager
            val callbacks = managers[fm]
            if(callbacks != null){
                fm.unregisterFragmentLifecycleCallbacks(callbacks)
                managers.remove(fm)
            }
        }
    }

    private fun computeScreenHierarchyHash(activity: Activity?): String {
        return activity?.javaClass!!.simpleName + " " + getVisibleFragmentsStringified(activity)
    }

    private fun getVisibleFragmentsStringified(activity: Activity?): String {
        if(activity is AppCompatActivity){
                val sb = StringBuilder()
                for (f in activity.supportFragmentManager.fragments){
                    if(f.isVisible){
                        sb.append(f.javaClass.simpleName)
                        sb.append(" ")
                    }
                }
            return sb.toString()
        }
        return "No Fragments"
    }

    private fun checkIsAppEnabled(): Boolean {
        return true
    }

    override fun onActivityPaused(activity: Activity?) {
        unregisterFragmentManager(activity)
    }

    override fun onActivityResumed(activity: Activity?) {
        registerFragmentManager(activity)
        handleHierarchyChange(activity)
    }

    override fun onActivityDestroyed(activity: Activity?) { }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) { }

    override fun onActivityStarted(activity: Activity?) { }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) { }

    override fun onActivityStopped(activity: Activity?) { }
}

data class Event(val type: String, val name: String, val source: String, val timestampMs: Date)
