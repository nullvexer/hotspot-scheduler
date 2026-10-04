package com.iranjan.hotspotscheduler.data.repo

import com.iranjan.hotspotscheduler.core.RoutineEvaluator
import com.iranjan.hotspotscheduler.data.db.RoutineDao
import com.iranjan.hotspotscheduler.data.db.RoutineEntity
import com.iranjan.hotspotscheduler.data.db.UsageDao
import com.iranjan.hotspotscheduler.data.db.UsageDayEntity
import com.iranjan.hotspotscheduler.data.model.Routine
import com.iranjan.hotspotscheduler.data.model.toDomain
import com.iranjan.hotspotscheduler.data.model.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
    private val usageDao: UsageDao
) {
    fun observeRoutines(): Flow<List<Routine>> =
        routineDao.observeAll()
            .map { list -> list.map { it.toDomain() } }
            // toDomain() decrypts every stored passphrase, which is a Keystore JNI call. Doing
            // that on Dispatchers.Main for every row on every DB invalidation is a jank source.
            .flowOn(Dispatchers.Default)

    fun observeUsage(limit: Int = 7): Flow<List<UsageDayEntity>> = usageDao.observeRecent(limit)

    suspend fun routine(id: Long): Routine? = routineDao.getById(id)?.toDomain()

    suspend fun enabledRoutines(): List<Routine> = routineDao.enabledOnce()
        .map { it.toDomain() }

    /**
     * Preserves createdAt on update. It used to be overwritten with now() on every save while
     * also being the ORDER BY column, so editing any routine sent it to the bottom of the list.
     */
    suspend fun save(routine: Routine) {
        val existing = if (routine.id != 0L) routineDao.getById(routine.id)?.createdAt else null
        routineDao.upsert(routine.toEntity(existing ?: System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = routineDao.delete(id)

    suspend fun overlappingWith(routine: Routine): List<Routine> {
        val all = routineDao.allOnce().map { it.toDomain() }
        return RoutineEvaluator.overlapping(routine, all.filter { it.id != routine.id })
    }

    suspend fun overlappingPairs(): List<Pair<Routine, Routine>> {
        val all = routineDao.allOnce().map { it.toDomain() }
        val result = mutableListOf<Pair<Routine, Routine>>()
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                if (RoutineEvaluator.overlapping(all[i], listOf(all[j])).isNotEmpty()) {
                    result.add(all[i] to all[j])
                }
            }
        }
        return result
    }

    suspend fun upsertUsageDay(epochDay: Long, bytes: Long) {
        usageDao.upsert(UsageDayEntity(epochDay, bytes, System.currentTimeMillis()))
    }

    suspend fun pruneUsage(beforeEpochDay: Long) = usageDao.prune(beforeEpochDay)

    suspend fun latestUsage(): UsageDayEntity? = usageDao.latest()

    suspend fun exportJson(): String {
        val routines = routineDao.allOnce().map { it.toDomain() }
        val root = JSONObject()
        root.put("version", EXPORT_VERSION)
        val array = JSONArray()
        for (r in routines) {
            val obj = JSONObject()
            obj.put("name", r.name)
            obj.put("days", JSONArray(r.days.sorted()))
            obj.put("startMinutes", r.startMinutes)
            obj.put("endMinutes", r.endMinutes)
            obj.put("capMb", r.capMb ?: JSONObject.NULL)
            obj.put("enabled", r.enabled)
            // Previously omitted, so an export/import round trip silently reset this flag.
            obj.put("mobileData", r.mobileData)
            // hotspotPassword is intentionally never exported (see README).
            array.put(obj)
        }
        root.put("routines", array)
        return root.toString(2)
    }

    /**
     * Validates and imports atomically: a malformed entry used to abort mid-loop and report
     * failure while having already persisted the routines before it.
     */
    suspend fun importJson(text: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(text)
        val array = root.optJSONArray("routines") ?: throw IllegalArgumentException("missing routines array")
        val parsed = ArrayList<Routine>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val days = obj.optJSONArray("days")?.let { arr ->
                (0 until arr.length()).map { arr.getInt(it) }.filter { it in 1..7 }.toSet()
            } ?: DEFAULT_DAYS
            val start = obj.optInt("startMinutes", 480)
            val end = obj.optInt("endMinutes", 570)
            require(start in 0..MINUTES_PER_DAY) { "routine ${i + 1}: startMinutes out of range ($start)" }
            require(end in 0..MINUTES_PER_DAY) { "routine ${i + 1}: endMinutes out of range ($end)" }
            require(days.isNotEmpty()) { "routine ${i + 1}: no valid days" }
            parsed.add(
                Routine(
                    id = 0,
                    name = obj.optString("name").ifBlank { "Imported ${i + 1}" },
                    days = days,
                    startMinutes = start,
                    endMinutes = end,
                    capMb = if (obj.isNull("capMb")) null else obj.optLong("capMb").takeIf { it > 0 },
                    enabled = obj.optBoolean("enabled", true),
                    mobileData = obj.optBoolean("mobileData", false)
                )
            )
        }
        val now = System.currentTimeMillis()
        val entities: List<RoutineEntity> = parsed.mapIndexed { i, r -> r.toEntity(now + i) }
        routineDao.upsertAll(entities)
        parsed.size
    }

    companion object {
        const val EXPORT_VERSION = 2
        private const val MINUTES_PER_DAY = 24 * 60
        private val DEFAULT_DAYS = setOf(1, 2, 3, 4, 5)
    }
}