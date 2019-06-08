package com.lazylantern.midgar.api

import android.util.Log
import androidx.annotation.WorkerThread
import com.lazylantern.midgar.MidgarTracker
import com.lazylantern.midgar.models.Event
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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
