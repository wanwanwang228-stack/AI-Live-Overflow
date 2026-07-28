package com.liveoverflow.pet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (Settings.canDrawOverlays(this).not()) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse(\"package:\" + packageName)))
            } else {
                startOverlay()
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }

        if (Settings.canDrawOverlays(this)) {
            findViewById<TextView>(R.id.tvStatus).text = "\u2705 \u60ac\u6d6e\u7a97\u6743\u9650\u5df2\u5f00"
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            findViewById<TextView>(R.id.tvStatus).text = "\u2705 \u60ac\u6d6e\u7a97\u6743\u9650\u5df2\u5f00"
            startOverlay()
        }
    }

    private fun startOverlay() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish()
    }
}