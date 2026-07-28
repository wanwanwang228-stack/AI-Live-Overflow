package com.liveoverflow.pet

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
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
        const val PET_W = 200
        const val PET_H = 220
        val HTML = "<!DOCTYPE html><html><head><meta charset=utf-8><style>*{margin:0;padding:0}body{width:200px;height:220px;background:#f8c8d0;position:relative;overflow:hidden}svg{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:140px;height:140px}</style></head><body><svg viewBox="0 0 140 140" xmlns="http://www.w3.org/2000/svg"><ellipse cx=70 cy=74 rx=56 ry=50 fill=#e8e0d8 stroke=#888 stroke-width=2.5/><ellipse cx=26 cy=34 rx=17 ry=20 fill=#e8e0d8 stroke=#888 stroke-width=2.5 transform="rotate(-15,26,34)"/><ellipse cx=114 cy=34 rx=17 ry=20 fill=#e8e0d8 stroke=#888 stroke-width=2.5 transform="rotate(15,114,34)"/><ellipse cx=26 cy=34 rx=8 ry=12 fill=#ffd0d8 transform="rotate(-15,26,34)"/><ellipse cx=114 cy=34 rx=8 ry=12 fill=#ffd0d8 transform="rotate(15,114,34)"/><ellipse cx=70 cy=70 rx=42 ry=35 fill=#fafafa stroke=#888 stroke-width=1.8/><ellipse cx=54 cy=64 rx=7 ry=9.5 fill=#444/><ellipse cx=86 cy=64 rx=7 ry=9.5 fill=#444/><circle cx=56 cy=60 r=2.5 fill=#fff/><circle cx=88 cy=60 r=2.5 fill=#fff/><path d="M60 82 Q70 90 80 82" fill=none stroke=#777 stroke-width=2 stroke-linecap=round/><ellipse cx=38 cy=76 rx=9 ry=6 fill=#ffb3b3 opacity=.4/><ellipse cx=102 cy=76 rx=9 ry=6 fill=#ffb3b3 opacity=.4/><ellipse cx=44 cy=122 rx=14 ry=8 fill=#d8d0c8 stroke=#888 stroke-width=1.5/><ellipse cx=96 cy=122 rx=14 ry=8 fill=#d8d0c8 stroke=#888 stroke-width=1.5/><path d="M124 70 Q140 54 134 84 Q128 98 118 92" fill=none stroke=#888 stroke-width=2/></svg></body></html>"
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
            loadDataWithBaseURL("http://localhost", HTML, "text/html", "UTF-8", null)
        }
        val lp = WindowManager.LayoutParams(PET_W, PET_H,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.END
        lp.x = 0; lp.y = 100
        wm.addView(wv, lp)
        startPolling()
    }

    private var ix=0; private var iy=0; private var tx=0f; private var ty=0f

    private fun handleTouch(e: MotionEvent): Boolean {
        when(e.action) {
            MotionEvent.ACTION_DOWN -> {
                val lp = wv.layoutParams as WindowManager.LayoutParams
                ix = lp.x; iy = lp.y; tx = e.rawX; ty = e.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val lp = wv.layoutParams as WindowManager.LayoutParams
                lp.x = ix - (e.rawX - tx).toInt()
                lp.y = iy + (e.rawY - ty).toInt()
                wm.updateViewLayout(wv, lp)
            }
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
                        wv.evaluateJavascript("applyState('"+k+"','"+v+"')", null)
                    }
                }
            } catch(_:Exception){}
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wm.removeView(wv)
        wv.destroy()
        super.onDestroy()
    }
}