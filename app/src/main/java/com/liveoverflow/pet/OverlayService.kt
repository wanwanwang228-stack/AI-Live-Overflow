package com.liveoverflow.pet

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.webkit.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var webView: WebView
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var appDetector: AppDetector? = null

    companion object {
        const val SUPABASE_URL = "https://hrxyjjcghrjwrcdcbhfq.supabase.co"
        const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhyeHlqamNnaHJqd3JjZGNiaGZxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODUyMjMxNDYsImV4cCI6MjEwMDc5OTE0Nn0.aLZem-JvA7gA71dppwreyRIY98LgEsYRKjPqfVi2rKg"
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupWebView()
        startPolling()
        startAppDetection()
        startBatteryMonitor()
        startTimeCheck()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(0x00000000)
            isFocusable = false
            webViewClient = object : WebViewClient {
                override fun onPageFinished(view: WebView, url: String) {
                    injectSupabaseConfig()
                }
            }
            setOnTouchListener { _, event -> handleTouch(event); true }
            loadUrl("file:///android_asset/pet.html")
        }
        val params = WindowManager.LayoutParams(
            200, 200,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 300
        }
        windowManager.addView(webView, params)
    }

    private fun injectSupabaseConfig() {
        webView.evaluateJavascript("""
            window.SUPABASE_URL = "${'$'}SUPABASE_URL";
            window.SUPABASE_KEY = "${'$'}SUPABASE_KEY";
        """.trimIndent(), null)
    }

    private var initialX = 0; private var initialY = 0
    private var touchX = 0f; private var touchY = 0f
    private var touchTime = 0L; private var tapCount = 0

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = (webView.layoutParams as WindowManager.LayoutParams).x
                initialY = (webView.layoutParams as WindowManager.LayoutParams).y
                touchX = event.rawX; touchY = event.rawY
                val now = System.currentTimeMillis()
                if (now - touchTime < 500) tapCount++ else tapCount = 1
                touchTime = now
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - touchX).toInt()
                val dy = (event.rawY - touchY).toInt()
                (webView.layoutParams as WindowManager.LayoutParams).apply {
                    x = initialX - dx; y = initialY + dy
                }
                windowManager.updateViewLayout(webView, webView.layoutParams)
            }
            MotionEvent.ACTION_UP -> {
                when {
                    tapCount >= 5 -> callJS("trigger('combo5')")
                    tapCount >= 3 -> callJS("trigger('combo3')")
                    tapCount == 2 -> callJS("trigger('double_tap')")
                    event.eventTime - event.downTime > 500 -> callJS("trigger('long_press')")
                    else -> callJS("trigger('tap')")
                }
                if (tapCount >= 3) tapCount = 0
                scope.launch { logGesture() }
            }
        }
        return true
    }

    private fun callJS(code: String) {
        webView.evaluateJavascript("javascript:" + code, null)
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel("pet", "\u5c0f\u5bb6\u4f19", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, "pet")
            .setContentTitle("\u5c0f\u5bb6\u4f19")
            .setContentText("\u5728\u8fd9\u91cc\u966a\u7740\u4f60~")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true).build()
    }

    private fun startAppDetection() {
        appDetector = AppDetector(this) { pkg ->
            val reaction = AppDetector.APP_REACTIONS[pkg]
            if (reaction != null) {
                callJS("trigger('${'$'}{reaction.first}')")
                callJS("showBubbleText('${'$'}{reaction.second}')")
                scope.launch { postAppUsage(pkg) }
            }
        }
        appDetector?.start()
    }

    private fun startBatteryMonitor() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> callJS("trigger('charging')")
                    Intent.ACTION_POWER_DISCONNECTED -> callJS("trigger('unplugged')")
                    Intent.ACTION_BATTERY_LOW -> callJS("trigger('lowbattery')")
                }
            }
        }, filter)
    }

    private fun startTimeCheck() {
        scope.launch {
            while (isActive) {
                delay(60000)
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                when (hour) {
                    in 0..5 -> callJS("trigger('late_night')")
                    in 6..9 -> callJS("trigger('morning')")
                }
            }
        }
    }

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                delay(5000)
                try { pollSupabaseState() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun pollSupabaseState() = withContext(Dispatchers.IO) {
        val url = URL(SUPABASE_URL + "/rest/v1/pet_state?order=updated_at.desc&limit=1")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY)
        val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
        conn.disconnect()
        if (body != "[]") {
            try {
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val state = arr.getJSONObject(0)
                    val key = state.optString("state_key", "")
                    val value = state.optString("state_value", "")
                    withContext(Dispatchers.Main) {
                        callJS("applyState('" + key + "', '" + value + "')")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun logGesture() = withContext(Dispatchers.IO) {
        try {
            val url = URL(SUPABASE_URL + "/rest/v1/gesture_log")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY)
            conn.setRequestProperty("Prefer", "return=minimal")
            val body = "{\"gesture_type\":\"tap\"}"
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {}
    }

    private suspend fun postAppUsage(pkg: String) = withContext(Dispatchers.IO) {
        try {
            val url = URL(SUPABASE_URL + "/rest/v1/app_usage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY)
            conn.setRequestProperty("Prefer", "return=minimal")
            val body = "{\"package_name\":\"" + pkg + "\"}"
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.disconnect()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        appDetector?.stop()
        scope.cancel()
        windowManager.removeView(webView)
        super.onDestroy()
    }
}