package com.svcmonitor.app;

/**
 * KpmBridge v8.1.0 — ctl0 output first.
 *
 * FIX: Use sh -c with properly escaped shell command string
 * to avoid argument splitting issues with Runtime.exec(String[]).
 *
 * When using Runtime.exec(arrayOf("su","-c","...")), the third element
 * is passed to su which hands it to sh -c. So we need a single properly
 * quoted shell command string.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u0007\n\u0002\u0010\t\n\u0002\b\r\n\u0002\u0010\u0012\n\u0002\b\u0006\n\u0002\u0010 \n\u0002\b\u000b\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\t\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0002JKB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0011\u0010\f\u001a\u00020\rH\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eJ\u0011\u0010\u000f\u001a\u00020\u0010H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eJ\u0011\u0010\u0011\u001a\u00020\rH\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eJ\u0011\u0010\u0012\u001a\u00020\rH\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eJ\u0019\u0010\u0013\u001a\u00020\r2\u0006\u0010\u0014\u001a\u00020\u0015H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0016J\u001b\u0010\u0017\u001a\u00020\r2\b\b\u0002\u0010\u0018\u001a\u00020\u0015H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0016J\u0011\u0010\u0019\u001a\u00020\rH\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eJ\u0011\u0010\u001a\u001a\u00020\rH\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eJ\u0019\u0010\u001b\u001a\u00020\r2\u0006\u0010\u0014\u001a\u00020\u0015H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0016J\u0011\u0010\u001c\u001a\u00020\u001dH\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eJ\u0011\u0010\u001e\u001a\u00020\rH\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eJ\u0019\u0010\u001f\u001a\u00020\r2\u0006\u0010 \u001a\u00020\u0004H\u0082@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010!J\u0006\u0010\"\u001a\u00020\u0004J\u0006\u0010#\u001a\u00020\u0004J\u0019\u0010$\u001a\u00020\r2\u0006\u0010%\u001a\u00020\u0004H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010!J#\u0010&\u001a\u00020\u00042\u0006\u0010\'\u001a\u00020\u001d2\b\b\u0002\u0010(\u001a\u00020\u0015H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010)J!\u0010*\u001a\u00020+2\u0006\u0010\'\u001a\u00020\u001d2\u0006\u0010(\u001a\u00020\u0015H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010)J!\u0010,\u001a\u00020\u00042\u0006\u0010-\u001a\u00020\u00152\u0006\u0010.\u001a\u00020\u001dH\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010/J\u0019\u00100\u001a\u00020\u00042\u0006\u0010-\u001a\u00020\u0015H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0016J1\u00101\u001a\b\u0012\u0004\u0012\u00020\u001d022\u0006\u0010-\u001a\u00020\u00152\u0006\u00103\u001a\u00020\u001d2\b\b\u0002\u00104\u001a\u00020\u0015H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u00105J\u0011\u00106\u001a\u00020\u0010H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eJ\u0019\u00107\u001a\u00020\r2\u0006\u00108\u001a\u00020\u0010H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u00109J\u001f\u0010:\u001a\u00020\r2\f\u0010;\u001a\b\u0012\u0004\u0012\u00020\u001502H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010<J\u000e\u0010=\u001a\u00020>2\u0006\u0010?\u001a\u00020\u0004J\u0019\u0010@\u001a\u00020\r2\u0006\u0010A\u001a\u00020\u0015H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0016J\u001c\u0010B\u001a\u000e\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u00040C2\u0006\u0010D\u001a\u00020\u0004H\u0002J\u001c\u0010E\u001a\u000e\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020+0C2\u0006\u0010D\u001a\u00020\u0004H\u0002J\u0011\u0010F\u001a\u00020\rH\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eJ\u0019\u0010G\u001a\u00020\r2\u0006\u0010H\u001a\u00020\u0010H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u00109J\u0011\u0010I\u001a\u00020\u0010H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u000eR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u0082\u0002\u0004\n\u0002\b\u0019\u00a8\u0006L"}, d2 = {"Lcom/svcmonitor/app/KpmBridge;", "", "()V", "EVENT_FILE", "", "KPATCH", "MODULE", "OUT_FILE", "TAG", "mutex", "Lkotlinx/coroutines/sync/Mutex;", "superKey", "clear", "Lcom/svcmonitor/app/KpmBridge$KpmResult;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "clearEventFile", "", "disable", "disableAll", "disableNr", "nr", "", "(ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "drain", "max", "enable", "enableAll", "enableNr", "eventFileSize", "", "events", "execute", "command", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getEventFilePath", "getSuperKey", "preset", "name", "readEventFile", "offset", "maxBytes", "(JILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "readEventFileChunk", "", "readProcFdLink", "pid", "fd", "(IJLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "readProcMaps", "readProcMemQwords", "", "addr", "qwords", "(IJILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "rotateEventFile", "setDoFilpOpen", "enabled", "(ZLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "setNrs", "nrs", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "setSuperKey", "", "key", "setUid", "uid", "shellExec", "Lkotlin/Pair;", "cmd", "shellExecBytes", "status", "tier2", "on", "truncateEventFile", "KpmResult", "PersistentSuShell", "app_debug"})
public final class KpmBridge {
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String TAG = "KpmBridge";
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String KPATCH = "/data/adb/ap/bin/kpatch";
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String MODULE = "svc_monitor";
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String OUT_FILE = "/data/local/tmp/svc_out.json";
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String EVENT_FILE = "/data/local/tmp/svc_events.bin";
    @org.jetbrains.annotations.NotNull
    private static java.lang.String superKey = "XiaoLu0129";
    @org.jetbrains.annotations.NotNull
    private static final kotlinx.coroutines.sync.Mutex mutex = null;
    @org.jetbrains.annotations.NotNull
    public static final com.svcmonitor.app.KpmBridge INSTANCE = null;
    
    private KpmBridge() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.lang.String getSuperKey() {
        return null;
    }
    
    public final void setSuperKey(@org.jetbrains.annotations.NotNull
    java.lang.String key) {
    }
    
    /**
     * Execute a shell command via su and return stdout+stderr.
     */
    private final kotlin.Pair<java.lang.Integer, java.lang.String> shellExec(java.lang.String cmd) {
        return null;
    }
    
    private final kotlin.Pair<java.lang.Integer, byte[]> shellExecBytes(java.lang.String cmd) {
        return null;
    }
    
    /**
     * Core execution.
     *
     * IMPORTANT: If kpatch does NOT join remaining args (i.e. only takes exactly
     * one arg after module name), we need to quote:
     *  /path/kpatch SUPERKEY kpm ctl0 svc_monitor 'uid 10234'
     * We use single quotes to be safe.
     */
    private final java.lang.Object execute(java.lang.String command, kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    /**
     * Start monitoring (enable callbacks)
     */
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object enable(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    /**
     * Stop monitoring (disable callbacks)
     */
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object disable(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    /**
     * Get module status
     */
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object status(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    /**
     * Set target UID (-1 for all)
     */
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object setUid(int uid, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    /**
     * Enable logging for a specific NR
     */
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object enableNr(int nr, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    /**
     * Disable logging for a specific NR
     */
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object disableNr(int nr, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    /**
     * Batch set NRs (replaces all)
     */
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object setNrs(@org.jetbrains.annotations.NotNull
    java.util.List<java.lang.Integer> nrs, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    /**
     * Enable all hooked NRs
     */
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object enableAll(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    /**
     * Disable all NRs
     */
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object disableAll(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    /**
     * Apply a preset
     */
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object preset(@org.jetbrains.annotations.NotNull
    java.lang.String name, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object tier2(boolean on, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object drain(int max, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object events(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object clear(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object clearEventFile(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object readEventFileChunk(long offset, int maxBytes, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super byte[]> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object setDoFilpOpen(boolean enabled, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.svcmonitor.app.KpmBridge.KpmResult> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.lang.String getEventFilePath() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object eventFileSize(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object readEventFile(long offset, int maxBytes, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object truncateEventFile(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object rotateEventFile(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object readProcMaps(int pid, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object readProcFdLink(int pid, long fd, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object readProcMemQwords(int pid, long addr, int qwords, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<java.lang.Long>> $completion) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u000e\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0005H\u00c6\u0003J\'\u0010\u0010\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u0011\u001a\u00020\u00032\b\u0010\u0012\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0013\u001a\u00020\u0014H\u00d6\u0001J\t\u0010\u0015\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0016"}, d2 = {"Lcom/svcmonitor/app/KpmBridge$KpmResult;", "", "success", "", "output", "", "error", "(ZLjava/lang/String;Ljava/lang/String;)V", "getError", "()Ljava/lang/String;", "getOutput", "getSuccess", "()Z", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "", "toString", "app_debug"})
    public static final class KpmResult {
        private final boolean success = false;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String output = null;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String error = null;
        
        public KpmResult(boolean success, @org.jetbrains.annotations.NotNull
        java.lang.String output, @org.jetbrains.annotations.NotNull
        java.lang.String error) {
            super();
        }
        
        public final boolean getSuccess() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getOutput() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getError() {
            return null;
        }
        
        public final boolean component1() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final com.svcmonitor.app.KpmBridge.KpmResult copy(boolean success, @org.jetbrains.annotations.NotNull
        java.lang.String output, @org.jetbrains.annotations.NotNull
        java.lang.String error) {
            return null;
        }
        
        @java.lang.Override
        public boolean equals(@org.jetbrains.annotations.Nullable
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override
        @org.jetbrains.annotations.NotNull
        public java.lang.String toString() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0002\n\u0000\b\u00c2\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\t\u001a\u00020\nH\u0002J$\u0010\u000b\u001a\u000e\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u000e0\f2\u0006\u0010\u000f\u001a\u00020\u000e2\b\b\u0002\u0010\u0010\u001a\u00020\u0011J\b\u0010\u0012\u001a\u00020\u0013H\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0014"}, d2 = {"Lcom/svcmonitor/app/KpmBridge$PersistentSuShell;", "", "()V", "proc", "Ljava/lang/Process;", "reader", "Ljava/io/BufferedReader;", "writer", "Ljava/io/BufferedWriter;", "ensure", "", "exec", "Lkotlin/Pair;", "", "", "cmd", "timeoutMs", "", "reset", "", "app_debug"})
    static final class PersistentSuShell {
        @org.jetbrains.annotations.Nullable
        private static java.lang.Process proc;
        @org.jetbrains.annotations.Nullable
        private static java.io.BufferedReader reader;
        @org.jetbrains.annotations.Nullable
        private static java.io.BufferedWriter writer;
        @org.jetbrains.annotations.NotNull
        public static final com.svcmonitor.app.KpmBridge.PersistentSuShell INSTANCE = null;
        
        private PersistentSuShell() {
            super();
        }
        
        private final boolean ensure() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull
        public final kotlin.Pair<java.lang.Integer, java.lang.String> exec(@org.jetbrains.annotations.NotNull
        java.lang.String cmd, long timeoutMs) {
            return null;
        }
        
        private final void reset() {
        }
    }
}