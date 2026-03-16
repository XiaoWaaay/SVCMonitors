package com.svcmonitor.app;

/**
 * MainViewModel v8.0.1 — Filter control + monitoring on/off + status polling.
 *
 * Key design:
 *  - Module loads with g_enabled=0 (paused)
 *  - APP sends "enable" to start monitoring, "disable" to stop
 *  - APP controls filter via uid/set_nrs/preset commands
 *  - Polling paused during command execution to avoid race
 *
 * FIX: More robust error handling in startMonitoring flow.
 *  - Don't abort the entire flow on non-critical step failure
 *  - Log all command results for debugging
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0082\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010!\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\t\n\u0002\b\u0006\n\u0002\u0010#\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0010\u0012\n\u0002\b\u0006\n\u0002\u0010\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u001d\u0018\u0000 i2\u00020\u0001:\u0001iB\u0005\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010C\u001a\u00020D2\u0006\u0010E\u001a\u00020\u0005J\u000e\u0010F\u001a\u00020D2\u0006\u0010G\u001a\u00020\u0010J\u0006\u0010H\u001a\u00020DJ\u0006\u0010I\u001a\u00020DJ\u0006\u0010J\u001a\u00020DJ\u000e\u0010K\u001a\u00020D2\u0006\u0010L\u001a\u00020MJ\b\u0010N\u001a\u00020DH\u0014J\u0011\u0010O\u001a\u00020DH\u0082@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010PJ\u0006\u0010Q\u001a\u00020DJ\u0011\u0010R\u001a\u00020DH\u0082@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010PJ\u000e\u0010S\u001a\u00020D2\u0006\u0010E\u001a\u00020\u0005J\u000e\u0010T\u001a\u00020D2\u0006\u0010U\u001a\u00020\u000bJ\u0014\u0010V\u001a\u00020D2\f\u0010W\u001a\b\u0012\u0004\u0012\u00020\u00050\bJ\u000e\u0010X\u001a\u00020D2\u0006\u0010Y\u001a\u00020\u0010J\u000e\u0010Z\u001a\u00020D2\u0006\u0010[\u001a\u00020\u0005J\u0015\u0010\\\u001a\u00020D2\b\u0010]\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0002\u0010^J\u000e\u0010_\u001a\u00020D2\u0006\u0010[\u001a\u00020\u0005J\u001c\u0010`\u001a\u00020D2\u0006\u0010[\u001a\u00020\u00052\f\u0010W\u001a\b\u0012\u0004\u0012\u00020\u00050\bJ\u0006\u0010a\u001a\u00020DJ\u0006\u0010b\u001a\u00020DJ\u0006\u0010c\u001a\u00020DJ\u001f\u0010d\u001a\u00020D2\f\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\t0\bH\u0082@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010eJ\u000e\u0010f\u001a\u00020D2\u0006\u0010g\u001a\u00020\u000bJ\u0006\u0010h\u001a\u00020DR\u001c\u0010\u0003\u001a\u0010\u0012\f\u0012\n \u0006*\u0004\u0018\u00010\u00050\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0007\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\t0\b0\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001c\u0010\n\u001a\u0010\u0012\f\u0012\n \u0006*\u0004\u0018\u00010\u000b0\u000b0\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\t0\b0\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000e0\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000f\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00100\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0011\u001a\u0004\u0018\u00010\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0013\u001a\u00020\u000bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0014\u0010\u0015\"\u0004\b\u0016\u0010\u0017R\u000e\u0010\u0018\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\t0\u001aX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00050\u001c\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u001d\u0010\u001f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\t0\b0\u001c\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u001eR\u000e\u0010!\u001a\u00020\"X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010#\u001a\u00020\u0005X\u0082D\u00a2\u0006\u0002\n\u0000R\u0017\u0010$\u001a\b\u0012\u0004\u0012\u00020\u000b0\u001c\u00a2\u0006\b\n\u0000\u001a\u0004\b%\u0010\u001eR\u001d\u0010&\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\t0\b0\u001c\u00a2\u0006\b\n\u0000\u001a\u0004\b\'\u0010\u001eR\u0014\u0010(\u001a\b\u0012\u0004\u0012\u00020\u00050)X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010*\u001a\u0004\u0018\u00010+X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010,\u001a\u00020\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010-\u001a\u00020\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001c\u0010.\u001a\u0004\u0018\u00010/X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b0\u00101\"\u0004\b2\u00103R\u001a\u00104\u001a\u00020\u0010X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b5\u00106\"\u0004\b7\u00108R\u0017\u00109\u001a\b\u0012\u0004\u0012\u00020\u000e0\u001c\u00a2\u0006\b\n\u0000\u001a\u0004\b:\u0010\u001eR\u000e\u0010;\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010<\u001a\u00020=X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0012\u0010>\u001a\u0004\u0018\u00010\u0005X\u0082\u000e\u00a2\u0006\u0004\n\u0002\u0010?R\u0019\u0010@\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00100\u001c\u00a2\u0006\b\n\u0000\u001a\u0004\bA\u0010\u001eR\u000e\u0010B\u001a\u00020\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u0082\u0002\u0004\n\u0002\b\u0019\u00a8\u0006j"}, d2 = {"Lcom/svcmonitor/app/MainViewModel;", "Landroidx/lifecycle/ViewModel;", "()V", "_eventCount", "Landroidx/lifecycle/MutableLiveData;", "", "kotlin.jvm.PlatformType", "_events", "", "Lcom/svcmonitor/app/StatusParser$SvcEvent;", "_monitoring", "", "_newEvents", "_status", "Lcom/svcmonitor/app/StatusParser$ModuleStatus;", "_toast", "", "dao", "Lcom/svcmonitor/app/db/SvcEventDao;", "doFilpOpenEnabled", "getDoFilpOpenEnabled", "()Z", "setDoFilpOpenEnabled", "(Z)V", "emptyBinPolls", "eventBuffer", "", "eventCount", "Landroidx/lifecycle/LiveData;", "getEventCount", "()Landroidx/lifecycle/LiveData;", "events", "getEvents", "fileOffset", "", "maxEvents", "monitoring", "getMonitoring", "newEvents", "getNewEvents", "nrSet", "", "pollingJob", "Lkotlinx/coroutines/Job;", "pollingPaused", "searchQuery", "selectedApp", "Lcom/svcmonitor/app/AppInfo;", "getSelectedApp", "()Lcom/svcmonitor/app/AppInfo;", "setSelectedApp", "(Lcom/svcmonitor/app/AppInfo;)V", "selectedPreset", "getSelectedPreset", "()Ljava/lang/String;", "setSelectedPreset", "(Ljava/lang/String;)V", "status", "getStatus", "statusTick", "tailBuf", "", "tidFilter", "Ljava/lang/Integer;", "toast", "getToast", "useJsonFallback", "addNr", "", "nr", "applyPreset", "presetName", "clearEvents", "disableAll", "enableAll", "initDb", "ctx", "Landroid/content/Context;", "onCleared", "pollOnce", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "refreshNow", "refreshUiFromDb", "removeNr", "setDoFilpOpen", "enabled", "setNrs", "nrs", "setSearchQuery", "q", "setTargetUid", "uid", "setTidFilter", "tid", "(Ljava/lang/Integer;)V", "startMonitoring", "startMonitoringWithNrs", "startPolling", "stopMonitoring", "stopPolling", "storeAndPublish", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "tier2", "on", "toastConsumed", "Companion", "app_debug"})
public final class MainViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String TAG = "SVCMon";
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.MutableLiveData<com.svcmonitor.app.StatusParser.ModuleStatus> _status = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.LiveData<com.svcmonitor.app.StatusParser.ModuleStatus> status = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.MutableLiveData<java.util.List<com.svcmonitor.app.StatusParser.SvcEvent>> _events = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.LiveData<java.util.List<com.svcmonitor.app.StatusParser.SvcEvent>> events = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.MutableLiveData<java.util.List<com.svcmonitor.app.StatusParser.SvcEvent>> _newEvents = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.LiveData<java.util.List<com.svcmonitor.app.StatusParser.SvcEvent>> newEvents = null;
    @org.jetbrains.annotations.NotNull
    private final java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> eventBuffer = null;
    private final int maxEvents = 120;
    private boolean doFilpOpenEnabled = false;
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.MutableLiveData<java.lang.Integer> _eventCount = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.LiveData<java.lang.Integer> eventCount = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.MutableLiveData<java.lang.String> _toast = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.LiveData<java.lang.String> toast = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.MutableLiveData<java.lang.Boolean> _monitoring = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.lifecycle.LiveData<java.lang.Boolean> monitoring = null;
    @org.jetbrains.annotations.Nullable
    private com.svcmonitor.app.AppInfo selectedApp;
    @org.jetbrains.annotations.NotNull
    private java.lang.String selectedPreset = "";
    @org.jetbrains.annotations.Nullable
    private kotlinx.coroutines.Job pollingJob;
    private boolean pollingPaused = false;
    @org.jetbrains.annotations.NotNull
    private final java.util.Set<java.lang.Integer> nrSet = null;
    private int statusTick = 0;
    @org.jetbrains.annotations.Nullable
    private com.svcmonitor.app.db.SvcEventDao dao;
    private long fileOffset = 0L;
    @org.jetbrains.annotations.NotNull
    private byte[] tailBuf;
    @org.jetbrains.annotations.NotNull
    private java.lang.String searchQuery = "";
    @org.jetbrains.annotations.Nullable
    private java.lang.Integer tidFilter;
    private boolean useJsonFallback = false;
    private int emptyBinPolls = 0;
    @org.jetbrains.annotations.NotNull
    public static final com.svcmonitor.app.MainViewModel.Companion Companion = null;
    
    public MainViewModel() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public final androidx.lifecycle.LiveData<com.svcmonitor.app.StatusParser.ModuleStatus> getStatus() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final androidx.lifecycle.LiveData<java.util.List<com.svcmonitor.app.StatusParser.SvcEvent>> getEvents() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final androidx.lifecycle.LiveData<java.util.List<com.svcmonitor.app.StatusParser.SvcEvent>> getNewEvents() {
        return null;
    }
    
    public final boolean getDoFilpOpenEnabled() {
        return false;
    }
    
    public final void setDoFilpOpenEnabled(boolean p0) {
    }
    
    @org.jetbrains.annotations.NotNull
    public final androidx.lifecycle.LiveData<java.lang.Integer> getEventCount() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final androidx.lifecycle.LiveData<java.lang.String> getToast() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final androidx.lifecycle.LiveData<java.lang.Boolean> getMonitoring() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final com.svcmonitor.app.AppInfo getSelectedApp() {
        return null;
    }
    
    public final void setSelectedApp(@org.jetbrains.annotations.Nullable
    com.svcmonitor.app.AppInfo p0) {
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.lang.String getSelectedPreset() {
        return null;
    }
    
    public final void setSelectedPreset(@org.jetbrains.annotations.NotNull
    java.lang.String p0) {
    }
    
    public final void initDb(@org.jetbrains.annotations.NotNull
    android.content.Context ctx) {
    }
    
    public final void setSearchQuery(@org.jetbrains.annotations.NotNull
    java.lang.String q) {
    }
    
    public final void setTidFilter(@org.jetbrains.annotations.Nullable
    java.lang.Integer tid) {
    }
    
    public final void startPolling() {
    }
    
    public final void stopPolling() {
    }
    
    private final java.lang.Object pollOnce(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.Object storeAndPublish(java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> events, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.Object refreshUiFromDb(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * One-click start: set UID + apply preset + enable monitoring.
     * All 3 steps are sent sequentially. If a step fails, we log it
     * but continue — the critical step is "enable".
     */
    public final void startMonitoring(int uid) {
    }
    
    /**
     * Start with custom NRs instead of preset
     */
    public final void startMonitoringWithNrs(int uid, @org.jetbrains.annotations.NotNull
    java.util.List<java.lang.Integer> nrs) {
    }
    
    /**
     * Stop monitoring
     */
    public final void stopMonitoring() {
    }
    
    public final void setDoFilpOpen(boolean enabled) {
    }
    
    public final void setTargetUid(int uid) {
    }
    
    public final void applyPreset(@org.jetbrains.annotations.NotNull
    java.lang.String presetName) {
    }
    
    public final void setNrs(@org.jetbrains.annotations.NotNull
    java.util.List<java.lang.Integer> nrs) {
    }
    
    public final void enableAll() {
    }
    
    public final void disableAll() {
    }
    
    public final void tier2(boolean on) {
    }
    
    public final void clearEvents() {
    }
    
    public final void refreshNow() {
    }
    
    public final void toastConsumed() {
    }
    
    public final void addNr(int nr) {
    }
    
    public final void removeNr(int nr) {
    }
    
    @java.lang.Override
    protected void onCleared() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/svcmonitor/app/MainViewModel$Companion;", "", "()V", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}