package com.liveoverflow.pet

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
        const val PET_W = 240
        const val PET_H = 260
        val HTML = """<!DOCTYPE html><html><head><meta charset=utf-8><meta name=viewport content="width=240,height=260"><style>*{margin:0;padding:0;box-sizing:border-box}body{width:240px;height:260px;display:flex;justify-content:center;align-items:center;overflow:hidden;background:rgba(255,150,150,.5)}#wrap{width:130px;height:170px;background:rgba(255,255,255,.7);border-radius:18px;display:flex;align-items:center;justify-content:center}#pet{width:120px;height:120px;transition:transform .3s}@keyframes bounce{0%,100%{transform:translateY(0)}50%{transform:translateY(-8px)}}.bounce{animation:bounce .4s}</style></head><body><div id=wrap><svg id=pet viewBox="0 0 140 140" xmlns="http://www.w3.org/2000/svg"><ellipse cx=70 cy=74 rx=56 ry=50 fill=#f0f0f0 stroke=#aaa stroke-width=1.8></ellipse><ellipse cx=26 cy=34 rx=17 ry=20 fill=#f0f0f0 stroke=#aaa stroke-width=1.8 transform="rotate(-15,26,34)"></ellipse><ellipse cx=114 cy=34 rx=17 ry=20 fill=#f0f0f0 stroke=#aaa stroke-width=1.8 transform="rotate(15,114,34)"></ellipse><ellipse cx=26 cy=34 rx=8 ry=12 fill=#ffd0d8 transform="rotate(-15,26,34)"></ellipse><ellipse cx=114 cy=34 rx=8 ry=12 fill=#ffd0d8 transform="rotate(15,114,34)"></ellipse><ellipse cx=70 cy=70 rx=42 ry=35 fill=#fff stroke=#aaa stroke-width=1.3></ellipse><ellipse cx=54 cy=64 rx=7 ry=9.5 fill=#333></ellipse><ellipse cx=86 cy=64 rx=7 ry=9.5 fill=#333></ellipse><circle cx=56 cy=60 r=2.5 fill=#fff></circle><circle cx=88 cy=60 r=2.5 fill=#fff></circle><path d="M60 82 Q70 90 80 82" fill=none stroke=#999 stroke-width=1.8 stroke-linecap=round></path><ellipse cx=38 cy=76 rx=9 ry=6 fill=#ffb3b3 opacity=.3></ellipse><ellipse cx=102 cy=76 rx=9 ry=6 fill=#ffb3b3 opacity=.3></ellipse><ellipse cx=44 cy=122 rx=14 ry=8 fill=#e0e0e0 stroke=#aaa stroke-width=1.5></ellipse><ellipse cx=96 cy=122 rx=14 ry=8 fill=#e0e0e0 stroke=#aaa stroke-width=1.5></ellipse><path d="M124 70 Q140 54 134 84 Q128 98 118 92" fill=none stroke=#aaa stroke-width=1.8></path></svg></div><script>var p=document.getElementById('pet');function trigger(t){if(t==='tap'){p.classList.add('bounce');setTimeout(function(){p.classList.remove('bounce')},400)}}</script></body></html>"""
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
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            setBackgroundColor(0x00000000)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = object : WebViewClient() {}
            setOnTouchListener { _, e -> handleTouch(e); true }
            loadDataWithBaseURL(null, HTML, "text/html", "UTF-8", null)
        }
        val lp = WindowManager.LayoutParams(PET_W, PET_H,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 50; lp.y = 100
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
            MotionEvent.ACTION_UP -> wv.evaluateJavascript("trigger('tap')", null)
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