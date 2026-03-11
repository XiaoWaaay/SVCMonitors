package com.svcmonitor.app.db;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0006\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0006\bg\u0018\u00002\u00020\u0001J\'\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\u0006H\u00a7@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\bJ\u0011\u0010\t\u001a\u00020\nH\u00a7@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000bJ\u0011\u0010\f\u001a\u00020\u0006H\u00a7@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000bJ\u001f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\u0006\u0010\u0007\u001a\u00020\u0006H\u00a7@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eJ\'\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0007\u001a\u00020\u0006H\u00a7@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0012J\'\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0007\u001a\u00020\u0006H\u00a7@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0012J/\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\u0006H\u00a7@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0015J)\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\u00170\u00032\u0006\u0010\u0018\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\u0006H\u00a7@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\bJ)\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u001a0\u00032\u0006\u0010\u0018\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\u0006H\u00a7@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\bJ!\u0010\u001b\u001a\u00020\n2\u0006\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\u0011H\u00a7@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u001fJ\u001f\u0010 \u001a\u00020\n2\f\u0010!\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00a7@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\"\u0082\u0002\u0004\n\u0002\b\u0019\u00a8\u0006#"}, d2 = {"Lcom/svcmonitor/app/db/SvcEventDao;", "", "byTid", "", "Lcom/svcmonitor/app/db/SvcEventEntity;", "tid", "", "limit", "(IILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "clearAll", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "countAll", "latest", "(ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "search", "query", "", "(Ljava/lang/String;ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "searchAll", "searchAllByTid", "(Ljava/lang/String;IILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "threadEdges", "Lcom/svcmonitor/app/db/ThreadEdge;", "tgid", "threadStats", "Lcom/svcmonitor/app/db/ThreadStat;", "updateFpChain", "seq", "", "fpChain", "(JLjava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "upsertAll", "events", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
@androidx.room.Dao
public abstract interface SvcEventDao {
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object upsertAll(@org.jetbrains.annotations.NotNull
    java.util.List<com.svcmonitor.app.db.SvcEventEntity> events, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM events ORDER BY seq DESC LIMIT :limit")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object latest(int limit, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.svcmonitor.app.db.SvcEventEntity>> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM events WHERE pid = :tid ORDER BY seq DESC LIMIT :limit")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object byTid(int tid, int limit, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.svcmonitor.app.db.SvcEventEntity>> $completion);
    
    @androidx.room.Query(value = "\n        SELECT events.* FROM events\n        JOIN events_fts ON events.rowid = events_fts.rowid\n        WHERE events_fts MATCH :query\n        ORDER BY events.seq DESC\n        LIMIT :limit\n        ")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object search(@org.jetbrains.annotations.NotNull
    java.lang.String query, int limit, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.svcmonitor.app.db.SvcEventEntity>> $completion);
    
    @androidx.room.Query(value = "\n        SELECT * FROM events\n        WHERE (\n            name LIKE \'%\' || :query || \'%\' OR\n            comm LIKE \'%\' || :query || \'%\' OR\n            desc LIKE \'%\' || :query || \'%\' OR\n            fpChain LIKE \'%\' || :query || \'%\' OR\n            bt LIKE \'%\' || :query || \'%\' OR\n            CAST(seq AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(nr AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(tgid AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(pid AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(uid AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(ret AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a0 AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a1 AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a2 AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a3 AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a4 AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a5 AS TEXT) LIKE \'%\' || :query || \'%\'\n        )\n        ORDER BY seq DESC\n        LIMIT :limit\n        ")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object searchAll(@org.jetbrains.annotations.NotNull
    java.lang.String query, int limit, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.svcmonitor.app.db.SvcEventEntity>> $completion);
    
    @androidx.room.Query(value = "\n        SELECT * FROM events\n        WHERE pid = :tid AND (\n            name LIKE \'%\' || :query || \'%\' OR\n            comm LIKE \'%\' || :query || \'%\' OR\n            desc LIKE \'%\' || :query || \'%\' OR\n            fpChain LIKE \'%\' || :query || \'%\' OR\n            bt LIKE \'%\' || :query || \'%\' OR\n            CAST(seq AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(nr AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(tgid AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(pid AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(uid AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(ret AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a0 AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a1 AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a2 AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a3 AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a4 AS TEXT) LIKE \'%\' || :query || \'%\' OR\n            CAST(a5 AS TEXT) LIKE \'%\' || :query || \'%\'\n        )\n        ORDER BY seq DESC\n        LIMIT :limit\n        ")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object searchAllByTid(@org.jetbrains.annotations.NotNull
    java.lang.String query, int tid, int limit, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.svcmonitor.app.db.SvcEventEntity>> $completion);
    
    @androidx.room.Query(value = "SELECT COUNT(*) FROM events")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object countAll(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion);
    
    @androidx.room.Query(value = "DELETE FROM events")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object clearAll(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "UPDATE events SET fpChain = :fpChain WHERE seq = :seq")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object updateFpChain(long seq, @org.jetbrains.annotations.NotNull
    java.lang.String fpChain, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "\n        SELECT pid AS pid, COUNT(*) AS count\n        FROM events\n        WHERE tgid = :tgid\n        GROUP BY pid\n        ORDER BY count DESC\n        LIMIT :limit\n        ")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object threadStats(int tgid, int limit, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.svcmonitor.app.db.ThreadStat>> $completion);
    
    @androidx.room.Query(value = "\n        SELECT seq AS seq, pid AS parentPid, ret AS childPid\n        FROM events\n        WHERE tgid = :tgid AND nr IN (220, 435) AND ret > 0\n        ORDER BY seq ASC\n        LIMIT :limit\n        ")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object threadEdges(int tgid, int limit, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.svcmonitor.app.db.ThreadEdge>> $completion);
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
    }
}