package com.liveoverflow.pet

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.*

class AppDetector(private val context: Context, private val onAppChanged: (String) -> Unit) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var lastPkg = ""

    companion object {
        val APP_REACTIONS = mapOf(
            "com.ss.android.ugc.aweme" to Pair("jealous", "\u5728\u5237\u6296\u97f3\u2026\u2026\u54fc\uff01"),
            "com.taobao.taobao" to Pair("rich", "\u8d2d\u7269\u8f66\u6e05\u7a7a\uff01"),
            "com.netease.cloudmusic" to Pair("music", "\u597d\u542c\u5417\u8fd9\u9996~"),
            "com.tencent.mm" to Pair("normal", "\u5728\u804a\u5929\u5417\uff1f"),
            "com.tencent.mobileqq" to Pair("normal", "QQ\u4e5f\u884c~"),
            "com.miHoYo.Yuanshen" to Pair("game", "\u539f\u795e\u542f\u52a8\uff01"),
            "com.chaoxing.mobile" to Pair("study", "\u5b66\u4e60\u52a0\u6cb9~")
        )
    }

    fun start() {
        scope.launch {
            while (isActive) {
                delay(3000)
                try {
                    val pkg = getForegroundPackage()
                    if (pkg != null && pkg != lastPkg) {
                        lastPkg = pkg
                        withContext(Dispatchers.Main) { onAppChanged(pkg) }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun stop() { scope.cancel() }

    @Suppress("DEPRECATION")
    private fun getForegroundPackage(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val stats = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10000, now)
        } else {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10000, now)
        }
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}