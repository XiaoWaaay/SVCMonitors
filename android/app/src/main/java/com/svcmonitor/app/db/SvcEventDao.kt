package com.svcmonitor.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class ThreadStat(
    val pid: Int,
    val count: Int
)

data class ThreadEdge(
    val seq: Long,
    val parentPid: Int,
    val childPid: Long
)

@Dao
interface SvcEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<SvcEventEntity>)

    @Query("SELECT * FROM events ORDER BY seq DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<SvcEventEntity>

    @Query("SELECT * FROM events WHERE pid = :tid ORDER BY seq DESC LIMIT :limit")
    suspend fun byTid(tid: Int, limit: Int): List<SvcEventEntity>

    @Query(
        """
        SELECT events.* FROM events
        JOIN events_fts ON events.rowid = events_fts.rowid
        WHERE events_fts MATCH :query
        ORDER BY events.seq DESC
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int): List<SvcEventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE (
            name LIKE '%' || :query || '%' OR
            comm LIKE '%' || :query || '%' OR
            desc LIKE '%' || :query || '%' OR
            fpChain LIKE '%' || :query || '%' OR
            bt LIKE '%' || :query || '%' OR
            CAST(seq AS TEXT) LIKE '%' || :query || '%' OR
            CAST(nr AS TEXT) LIKE '%' || :query || '%' OR
            CAST(tgid AS TEXT) LIKE '%' || :query || '%' OR
            CAST(pid AS TEXT) LIKE '%' || :query || '%' OR
            CAST(uid AS TEXT) LIKE '%' || :query || '%' OR
            CAST(ret AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a0 AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a1 AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a2 AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a3 AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a4 AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a5 AS TEXT) LIKE '%' || :query || '%'
        )
        ORDER BY seq DESC
        LIMIT :limit
        """
    )
    suspend fun searchAll(query: String, limit: Int): List<SvcEventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE pid = :tid AND (
            name LIKE '%' || :query || '%' OR
            comm LIKE '%' || :query || '%' OR
            desc LIKE '%' || :query || '%' OR
            fpChain LIKE '%' || :query || '%' OR
            bt LIKE '%' || :query || '%' OR
            CAST(seq AS TEXT) LIKE '%' || :query || '%' OR
            CAST(nr AS TEXT) LIKE '%' || :query || '%' OR
            CAST(tgid AS TEXT) LIKE '%' || :query || '%' OR
            CAST(pid AS TEXT) LIKE '%' || :query || '%' OR
            CAST(uid AS TEXT) LIKE '%' || :query || '%' OR
            CAST(ret AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a0 AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a1 AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a2 AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a3 AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a4 AS TEXT) LIKE '%' || :query || '%' OR
            CAST(a5 AS TEXT) LIKE '%' || :query || '%'
        )
        ORDER BY seq DESC
        LIMIT :limit
        """
    )
    suspend fun searchAllByTid(query: String, tid: Int, limit: Int): List<SvcEventEntity>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun countAll(): Int

    @Query("DELETE FROM events")
    suspend fun clearAll()

    @Query("UPDATE events SET fpChain = :fpChain WHERE seq = :seq")
    suspend fun updateFpChain(seq: Long, fpChain: String)

    @Query(
        """
        SELECT pid AS pid, COUNT(*) AS count
        FROM events
        WHERE tgid = :tgid
        GROUP BY pid
        ORDER BY count DESC
        LIMIT :limit
        """
    )
    suspend fun threadStats(tgid: Int, limit: Int = 200): List<ThreadStat>

    @Query(
        """
        SELECT seq AS seq, pid AS parentPid, ret AS childPid
        FROM events
        WHERE tgid = :tgid AND nr IN (220, 435) AND ret > 0
        ORDER BY seq ASC
        LIMIT :limit
        """
    )
    suspend fun threadEdges(tgid: Int, limit: Int = 2000): List<ThreadEdge>
}
