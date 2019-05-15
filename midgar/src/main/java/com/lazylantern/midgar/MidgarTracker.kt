package com.lazylantern.midgar

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.schedule
import kotlin.coroutines.CoroutineContext

class MidgarTracker private constructor(app: Application) : Application.ActivityLifecycleCallbacks, CoroutineScope  {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    private var lastHierarchyHash: String = ""
    private var midgarAppId: String = ""
    private var managers: HashMap<FragmentManager, FragmentManager.FragmentLifecycleCallbacks> = HashMap()
    private var hasBeenRemotelyEnabled = false
    private var timer = Timer()
    private var eventsQueue: Queue<Event> = ArrayDeque<Event>()
    private lateinit var apiService: ApiService

    init {
        init(app)
        Log.d(TAG,"Midgar has initialized")
    }

    private fun init(app: Application) {
        midgarAppId = app.getString(R.string.midgar_app_id)
        if (midgarAppId.isBlank()){
            throw RuntimeException("Midgar App ID not set")
        }
    }

    private fun start(app: Application){
        apiService = ApiService(midgarAppId, app.getString(R.string.api_url))
        try{
            this.hasBeenRemotelyEnabled = apiService.checkAppIsEnabled()
            if(hasBeenRemotelyEnabled){
                //Register activity lifecycle callback
                app.registerActivityLifecycleCallbacks(this)
                startUploadLoop()
            }
        } catch (e: Exception){
            Log.e(TAG, "Midgar failed to start:", e)
        }

    }

    private fun shutdown(app: Application){
        try {
            stopUploadLoop()
            app.unregisterActivityLifecycleCallbacks(this)
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
            eventsQueue.offer(createEvent(newHierarchyHash))
        }
    }

    private fun createEvent(hierarchyHash: String): Event{
        return Event(Event.TYPE_IMPRESSION, hierarchyHash, Event.SOURCE_ANDROID, Date().time)
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
                launch { apiService.uploadBatch(events)}
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

    fun startTracker(app: Application){
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
    }
}

data class Event(val type: String, val name: String, val source: String, val timestampMs: Long){
    companion object {
        const val TYPE_IMPRESSION = "impression"
        const val SOURCE_ANDROID = "android"
    }

    fun toMap(): HashMap<String, Any>{
         return HashMap<String, Any>().apply {
            put("type", type)
            put("screen",  name)
            put("source", source)
            put("timestamp", timestampMs)
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
        if(responseCode == 200){
            Log.d(MidgarTracker.TAG,"Batch uploaded successfully")
        } else {
            Log.d(MidgarTracker.TAG,"Batch upload failed. Events got lost.")
        }
    }

    private fun createPostRequest(url: String, body: String): HttpsURLConnection {
        val connection = URL(this.apiUrl + url ).openConnection() as HttpsURLConnection
        with(connection){
            readTimeout = 3000
            connectTimeout = 3000
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
