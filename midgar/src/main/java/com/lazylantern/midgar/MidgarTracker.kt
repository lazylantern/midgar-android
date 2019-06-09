package com.lazylantern.midgar

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.lazylantern.midgar.api.ApiService
import com.lazylantern.midgar.models.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.schedule
import kotlin.coroutines.CoroutineContext


class MidgarTracker private constructor(app: Application) : ActivityLifecycleCallbacks(), CoroutineScope,
    LifecycleObserver {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    private var lastHierarchyHash: String = ""
    private var midgarAppId: String = ""
    private var midgarDeviceId: String = ""
    private var managers: HashMap<FragmentManager, FragmentManager.FragmentLifecycleCallbacks> = HashMap()
    private var hasBeenRemotelyEnabled = false
    private var timer = Timer()
    private var eventsQueue: Queue<Event> = ArrayDeque<Event>()
    private lateinit var apiService: ApiService
    private var sessionId: String? = null
    private var timeOfLastBackgroundEvent: Long = Long.MAX_VALUE
    private var contextInfo = ContextInfoBuilder().buildContextInfo(app)

    init {
        init(app)
        Log.d(TAG,"Midgar has initialized")
    }

    @SuppressLint("HardwareIds")
    private fun init(app: Application) {
        midgarAppId = app.getString(R.string.midgar_app_id)
        midgarDeviceId = Settings.Secure.getString(
            app.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (midgarAppId.isBlank()){
            throw RuntimeException("Midgar App ID not set")
        }
        apiService = ApiService(midgarAppId, app.getString(R.string.api_url))
    }

    private fun start(app: Application){
        try{
            this.hasBeenRemotelyEnabled = apiService.checkAppIsEnabled()
            val wasPreviouslyRunning = getPersistedKillSwitchState(app)
            if(!wasPreviouslyRunning){
                registerLifecycleCallbaks(app)
            }
            persistKillSwitchState(app, this.hasBeenRemotelyEnabled)
            if(hasBeenRemotelyEnabled){
                startUploadLoop()
            }
        } catch (e: Exception){
            Log.e(TAG, "Midgar failed to start:", e)
        }
    }

    private fun shutdown(app: Application){
        try {
            stopUploadLoop()
            unregisterLifecycleCallbacks(app)
            for ((manager, callback) in managers){
                manager.unregisterFragmentLifecycleCallbacks(callback)
            }
            managers.clear()
        } catch (e: Exception){
            Log.e(TAG, "Midgar failed to shutdown:", e)
        }

    }

    private fun handleHierarchyChange(activity: Activity?) {
        val newHierarchyHash = computeScreenHierarchyHash(activity)
        if (newHierarchyHash != lastHierarchyHash){
            Log.d(TAG, "Got a new hierarchy: $newHierarchyHash")
            eventsQueue.offer(createEvent(newHierarchyHash, Event.TYPE_IMPRESSION))
        }
    }

    private fun createEvent(hierarchyHash: String, type: String): Event{
        return Event(
            type,
            hierarchyHash,
            Date().time,
            Event.PLATFORM_ANDROID,
            Event.SDK_KOTLIN,
            this.midgarDeviceId,
            this.getSessionId(),
            contextInfo)
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

    private fun registerLifecycleCallbaks(app: Application){
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun unregisterLifecycleCallbacks(app: Application){
        app.unregisterActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }

    private fun startUploadLoop(){
        timer.schedule(
            UPLOAD_PERIOD_MS,
            UPLOAD_PERIOD_MS,
            uploadTimerTask()
        )
    }

    private fun stopUploadLoop(){
        timer.purge()
    }

    private fun uploadTimerTask(): TimerTask.() -> Unit {
        return {
            Log.d(TAG, "Processing batch")
            try{
                val events = ArrayList<Event>()
                dequeue@ while (!eventsQueue.isEmpty()) {
                    val event = eventsQueue.poll()
                    events.add(event)
                    if(events.size >= MAX_UPLOAD_BATCH_SIZE){
                        break@dequeue
                    }
                }
                if(events.size > 0){
                    launch { apiService.uploadBatch(events)}
                }
            } catch (e: Exception){
                Log.e(TAG, "Midgar encountered an error while processing batch:", e)
            }
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

    private fun persistKillSwitchState(app: Application, isAppEnabled: Boolean){
        app.getSharedPreferences(
            app.getString(R.string.shared_preferences_store), Context.MODE_PRIVATE)
            .edit()
            .putBoolean(app.getString(R.string.shared_preferences_kill_switch_key), isAppEnabled)
            .apply()
    }

    private fun getPersistedKillSwitchState(app: Application): Boolean {
        return  app.getSharedPreferences(
            app.getString(R.string.shared_preferences_store), Context.MODE_PRIVATE)
            .getBoolean(app.getString(R.string.shared_preferences_kill_switch_key), false)
    }

    private fun getSessionId(): String{
        if(sessionId == null || !checkSessionIdValidity()){
            sessionId = generateSessionId()
        }
        return sessionId as String
    }

    private fun checkSessionIdValidity(): Boolean{
        return System.currentTimeMillis() - timeOfLastBackgroundEvent < SESSION_MAX_LENGTH_MS
    }

    private fun generateSessionId():String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz0123456789"
        return (1..6)
            .map { allowedChars.random() }
            .joinToString("")
    }

    override fun onActivityPaused(activity: Activity?) {
        unregisterFragmentManager(activity)
    }

    override fun onActivityResumed(activity: Activity?) {
        registerFragmentManager(activity)
        handleHierarchyChange(activity)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun onMoveToForeground() {
        eventsQueue.offer(createEvent("", Event.TYPE_FOREGROUND))
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun onMoveToBackground() {
        eventsQueue.offer(createEvent("", Event.TYPE_BACKGROUND))
        timeOfLastBackgroundEvent  = System.currentTimeMillis()
    }

    fun startTracker(app: Application){
        if(getPersistedKillSwitchState(app)){
            // Start to capture events right away of persisted kill switch is positive
            // This ensures we can capture the first screen before we have a kill switch answer from the network.
            registerLifecycleCallbaks(app)
        }
        launch { start(app) }
    }

    fun stopTracker(app: Application){
        hasBeenRemotelyEnabled = false
        shutdown(app)
    }

    companion object : SingletonHolder<MidgarTracker, Application>(::MidgarTracker) {
        const val TAG = "MidgarSDK"
        const val MAX_UPLOAD_BATCH_SIZE = 10
        val UPLOAD_PERIOD_MS = TimeUnit.SECONDS.toMillis(60)
        val SESSION_MAX_LENGTH_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
