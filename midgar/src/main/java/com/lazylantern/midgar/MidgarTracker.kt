package com.lazylantern.midgar

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.schedule
import kotlin.coroutines.CoroutineContext
import androidx.lifecycle.ProcessLifecycleOwner



class MidgarTracker private constructor(app: Application) : Application.ActivityLifecycleCallbacks, CoroutineScope,
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
            this.getSessionId())
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
        return (1..10)
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

    override fun onActivityDestroyed(activity: Activity?) { }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) { }

    override fun onActivityStarted(activity: Activity?) { }

    override fun onActivityStopped(activity: Activity?) { }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) { }

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

data class Event(val type: String,
                 val name: String,
                 val timestampMs: Long,
                 val platform: String,
                 val sdk: String,
                 val deviceId: String,
                 val sessionId: String){
    companion object {
        const val TYPE_BACKGROUND = "background"
        const val TYPE_FOREGROUND = "foreground"
        const val TYPE_IMPRESSION = "impression"
        const val PLATFORM_ANDROID = "android"
        const val SDK_KOTLIN = "kotlin"
    }

    fun toMap(): HashMap<String, Any>{
         return HashMap<String, Any>().apply {
            put("type", type)
            put("screen",  name)
            put("timestamp", timestampMs)
            put("platform", platform)
            put("sdk", sdk)
            put("device_id", deviceId)
            put("session_id", sessionId)
        }
    }
}

class ApiService(private val appId: String, private val apiUrl: String) {

    @WorkerThread
    fun checkAppIsEnabled(): Boolean{
        val params = HashMap<String, String>()
        params["app_token"] = this.appId
        val connection = createPostRequest("/apps/kill", JSONObject(params).toString())
        connection.connect()
        val responseCode = connection.responseCode
        if(responseCode == 200){
            Log.d(MidgarTracker.TAG,"Midgar App is enabled")
            return true
        }
        Log.d(MidgarTracker.TAG,"Midgar App is DISABLED")
        return false
    }

    @WorkerThread
    fun uploadBatch(events: List<Event>){
        val params = HashMap<String, Any>()
        params["app_token"] = this.appId
        params["events"] = events.map { it.toMap() }
        val connection = createPostRequest("/events", JSONObject(params).toString())
        connection.connect()
        val responseCode = connection.responseCode
        if(responseCode < 400){
            Log.d(MidgarTracker.TAG,"Batch uploaded successfully")
        } else {
            Log.d(MidgarTracker.TAG,"Batch upload failed. Events got lost.")
        }
    }

    private fun createPostRequest(url: String, body: String): HttpURLConnection {
        val connection = URL(this.apiUrl + url ).openConnection() as HttpURLConnection
        with(connection){
            readTimeout = 30000
            connectTimeout = 30000
            requestMethod = "POST"
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val out = OutputStreamWriter(connection.outputStream        )
        out.write(body)
        out.close()
        return connection
    }
}

open class SingletonHolder<out T, in A>(creator: (A) -> T) {
    private var creator: ((A) -> T)? = creator
    @Volatile private var instance: T? = null

    fun getInstance(arg: A): T {
        val i = instance
        if (i != null) {
            return i
        }

        return synchronized(this) {
            val i2 = instance
            if (i2 != null) {
                i2
            } else {
                val created = creator!!(arg)
                instance = created
                creator = null
                created
            }
        }
    }
}
