package com.iranjan.hotspotscheduler.data.usage

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class UsageSample(val bytes: Long, val source: String) {
    companion object {
        const val SOURCE_TETHER = "tether-uids"

        /**
         * Whole-device cellular volume, NOT hotspot volume. The data cap is enforced against
         * whichever sample comes back, so a fallback to this source silently applies the user's
         * hotspot cap to their entire mobile-data bill. Callers must treat it accordingly.
         */
        const val SOURCE_MOBILE_TOTAL = "mobile-total"
    }

    val isHotspotAccurate: Boolean get() = source == SOURCE_TETHER
}

@Singleton
class UsageMonitor @Inject constructor(@ApplicationContext private val context: Context) {

    private val nsm: NetworkStatsManager? =
        context.getSystemService(NetworkStatsManager::class.java)

    private val appOps: AppOpsManager? =
        context.getSystemService(AppOpsManager::class.java)

    suspend fun hotspotBytesBetween(startMs: Long, endMs: Long = System.currentTimeMillis()): UsageSample? =
        withContext(Dispatchers.IO) {
            if (!hasUsageAccess()) {
                // Without the app-op every query throws SecurityException, and worse, an
                // unprivileged querySummary can silently return an EMPTY bucket list, which the
                // old code reported as "no usage" rather than "permission missing".
                return@withContext null
            }
            queryTetherUids(startMs, endMs) ?: queryMobileTotal(startMs, endMs)
        }

    suspend fun hotspotBytesSinceMidnight(): UsageSample? {
        val startOfDay = ZonedDateTime.now()
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return hotspotBytesBetween(startOfDay)
    }

    /** Mirrors the check SetupViewModel shows, so the cap can explain itself. */
    fun hasUsageAccess(): Boolean = try {
        appOps?.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    } catch (t: Throwable) {
        false
    }

    private fun queryTetherUids(startMs: Long, endMs: Long): UsageSample? {
        val stats = try {
            nsm?.querySummary(ConnectivityManager.TYPE_MOBILE, null, startMs, endMs)
        } catch (t: Throwable) {
            Log.w(TAG, "querySummary failed", t)
            null
        } ?: return null

        // NetworkStats from querySummary holds native resources and must be closed. The previous
        // version closed it after the loop, which any throw inside the loop skipped.
        return try {
            var total = 0L
            var sawTetherUid = false
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                if (bucket.uid in TETHER_UIDS) {
                    total += bucket.rxBytes + bucket.txBytes
                    sawTetherUid = true
                }
            }
            if (sawTetherUid) UsageSample(total, UsageSample.SOURCE_TETHER) else null
        } catch (t: Throwable) {
            Log.w(TAG, "bucket iteration failed", t)
            null
        } finally {
            runCatching { stats.close() }
        }
    }

    private fun queryMobileTotal(startMs: Long, endMs: Long): UsageSample? {
        // querySummaryForDevice returns a plain Bucket snapshot, not a Closeable, so there is
        // nothing to release here (the Closeable is the querySummary result in the function above).
        val bucket: NetworkStats.Bucket? = try {
            nsm?.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, startMs, endMs)
        } catch (t: Throwable) {
            Log.w(TAG, "querySummaryForDevice failed", t)
            null
        }
        val stats = bucket ?: return null
        return UsageSample(
            (stats.rxBytes + stats.txBytes).coerceAtLeast(0L),
            UsageSample.SOURCE_MOBILE_TOTAL
        )
    }

    companion object {
        private const val TAG = "HSUsage"

        /**
         * `android.uid.NETWORK_STACK` (1000) and `android.uid.TETHERING` (1017) are the
         * platform constants. The AOSP *static* values used previously (1026/1039) do not match
         * every build, and One UI ships its own tethering package, so a mismatch made the
         * tether probe find nothing and silently fall back to whole-device totals.
         *
         * 0/1000/1017 are the documented framework-reserved ids and hold on AOSP and Samsung;
         * the legacy values are kept so a device that does use them still works.
         */
        val TETHER_UIDS: Set<Int> = setOf(
            0,
            1000,   // NETWORK_STACK
            1017,   // TETHERING
            1026,   // legacy/observed
            1039,   // legacy/observed
        )
    }
}