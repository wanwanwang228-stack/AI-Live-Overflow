package com.liveoverflow.pet

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var wv: WebView
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        const val SU = "https://hrxyjjcghrjwrcdcbhfq.supabase.co"
        const val SK = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhyeHlqamNnaHJqd3JjZGNiaGZxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODUyMjMxNDYsImV4cCI6MjEwMDc5OTE0Nn0.aLZem-JvA7gA71dppwreyRIY98LgEsYRKjPqfVi2rKg"
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("p","pet",NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        startForeground(1, NotificationCompat.Builder(this,"p")
            .setContentTitle("pet").setContentText("here~")
            .setSmallIcon(android.R.drawable.sym_def_app_icon).setOngoing(true).build())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            setBackgroundColor(0x00000000)
            webViewClient = object : WebViewClient() {}
            setOnTouchListener { _, e -> handleTouch(e); true }
            loadUrl("file:///android_asset/pet.html")
        }
        val lp = WindowManager.LayoutParams(200,200,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.END
        lp.x = 0; lp.y = 300
        wm.addView(wv, lp)
        startPolling()
    }

    private var ix=0; private var iy=0; private var tx=0f; private var ty=0f

    private fun handleTouch(e: MotionEvent): Boolean {
        when(e.action) {
            MotionEvent.ACTION_DOWN -> { ix=wv.layoutParams.x; iy=wv.layoutParams.y; tx=e.rawX; ty=e.rawY }
            MotionEvent.ACTION_MOVE -> {
                val lp = wv.layoutParams as WindowManager.LayoutParams
                lp.x = ix - (e.rawX - tx).toInt()
                lp.y = iy + (e.rawY - ty).toInt()
                wm.updateViewLayout(wv, lp)
            }
            MotionEvent.ACTION_UP -> { wv.evaluateJavascript("javascript:trigger('tap')", null) }
        }
        return true
    }

    private fun startPolling() {
        scope.launch {
            while(isActive) { delay(5000); try { pollState() } catch(_:Exception){} }
        }
    }

    private suspend fun pollState() = withContext(Dispatchers.IO) {
        val c = URL(SU+"/rest/v1/pet_state?order=updated_at.desc&limit=1").openConnection() as HttpURLConnection
        c.setRequestProperty("apikey",SK)
        c.setRequestProperty("Authorization","Bearer "+SK)
        val body = c.inputStream.bufferedReader().readText()
        c.disconnect()
        if(body!="[]") {
            try {
                val arr = org.json.JSONArray(body)
                if(arr.length()>0) {
                    val s = arr.getJSONObject(0)
                    val k = s.optString("state_key","")
                    val v = s.optString("state_value","")
                    withContext(Dispatchers.Main) {
                        wv.evaluateJavascript("javascript:applyState('"+k+"','"+v+"')", null)
                    }
                }
            } catch(_:Exception){}
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wm.removeView(wv)
        super.onDestroy()
    }
}
