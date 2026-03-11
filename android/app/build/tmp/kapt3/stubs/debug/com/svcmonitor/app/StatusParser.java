package com.svcmonitor.app;

/**
 * StatusParser v8.0 — Parse JSON responses from KPM module.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010$\n\u0002\u0010\b\n\u0002\u0010\u000e\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\b\u00c6\u0002\u0018\u00002\u00020\u0001:\b#$%&\'()*B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0016\u001a\u00020\u000b2\u0006\u0010\u0017\u001a\u00020\nJ\u000e\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u000bJ\u0014\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u001c0\u00042\u0006\u0010\u001d\u001a\u00020\u000bJ\u000e\u0010\u001e\u001a\u00020\u001f2\u0006\u0010\u001a\u001a\u00020\u000bJ\u000e\u0010 \u001a\u00020!2\u0006\u0010\u001a\u001a\u00020\u000bJ\u000e\u0010\"\u001a\u00020\u000b2\u0006\u0010\u0017\u001a\u00020\nR\u0017\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\'\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b0\t8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u000e\u0010\u000f\u001a\u0004\b\f\u0010\rR\'\u0010\u0010\u001a\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b0\t8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0012\u0010\u000f\u001a\u0004\b\u0011\u0010\rR\u0017\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00140\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0007\u00a8\u0006+"}, d2 = {"Lcom/svcmonitor/app/StatusParser;", "", "()V", "categories", "", "Lcom/svcmonitor/app/StatusParser$SyscallCategory;", "getCategories", "()Ljava/util/List;", "nrCategoryMap", "", "", "", "getNrCategoryMap", "()Ljava/util/Map;", "nrCategoryMap$delegate", "Lkotlin/Lazy;", "nrNameMap", "getNrNameMap", "nrNameMap$delegate", "presets", "Lcom/svcmonitor/app/StatusParser$Preset;", "getPresets", "nrToName", "nr", "parseDrain", "Lcom/svcmonitor/app/StatusParser$DrainResult;", "json", "parseEventLines", "Lcom/svcmonitor/app/StatusParser$SvcEvent;", "text", "parseSimple", "Lcom/svcmonitor/app/StatusParser$SimpleResult;", "parseStatus", "Lcom/svcmonitor/app/StatusParser$ModuleStatus;", "syscallCategory", "DrainResult", "HookInfo", "ModuleStatus", "Preset", "SimpleResult", "SvcEvent", "SyscallCategory", "SyscallEntry", "app_debug"})
public final class StatusParser {
    @org.jetbrains.annotations.NotNull
    private static final java.util.List<com.svcmonitor.app.StatusParser.Preset> presets = null;
    @org.jetbrains.annotations.NotNull
    private static final java.util.List<com.svcmonitor.app.StatusParser.SyscallCategory> categories = null;
    @org.jetbrains.annotations.NotNull
    private static final kotlin.Lazy nrNameMap$delegate = null;
    @org.jetbrains.annotations.NotNull
    private static final kotlin.Lazy nrCategoryMap$delegate = null;
    @org.jetbrains.annotations.NotNull
    public static final com.svcmonitor.app.StatusParser INSTANCE = null;
    
    private StatusParser() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public final com.svcmonitor.app.StatusParser.ModuleStatus parseStatus(@org.jetbrains.annotations.NotNull
    java.lang.String json) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final com.svcmonitor.app.StatusParser.DrainResult parseDrain(@org.jetbrains.annotations.NotNull
    java.lang.String json) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> parseEventLines(@org.jetbrains.annotations.NotNull
    java.lang.String text) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final com.svcmonitor.app.StatusParser.SimpleResult parseSimple(@org.jetbrains.annotations.NotNull
    java.lang.String json) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.util.List<com.svcmonitor.app.StatusParser.Preset> getPresets() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.util.List<com.svcmonitor.app.StatusParser.SyscallCategory> getCategories() {
        return null;
    }
    
    private final java.util.Map<java.lang.Integer, java.lang.String> getNrNameMap() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.lang.String nrToName(int nr) {
        return null;
    }
    
    private final java.util.Map<java.lang.Integer, java.lang.String> getNrCategoryMap() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.lang.String syscallCategory(int nr) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0015\b\u0086\b\u0018\u00002\u00020\u0001B;\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0005\u0012\u000e\b\u0002\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b\u0012\b\b\u0002\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fJ\t\u0010\u0016\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0017\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0005H\u00c6\u0003J\u000f\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\t0\bH\u00c6\u0003J\t\u0010\u001a\u001a\u00020\u000bH\u00c6\u0003JA\u0010\u001b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\u000e\b\u0002\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b2\b\b\u0002\u0010\n\u001a\u00020\u000bH\u00c6\u0001J\u0013\u0010\u001c\u001a\u00020\u00032\b\u0010\u001d\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001e\u001a\u00020\u0005H\u00d6\u0001J\t\u0010\u001f\u001a\u00020\u000bH\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\n\u001a\u00020\u000b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0017\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u000e\u00a8\u0006 "}, d2 = {"Lcom/svcmonitor/app/StatusParser$DrainResult;", "", "ok", "", "count", "", "total", "events", "", "Lcom/svcmonitor/app/StatusParser$SvcEvent;", "error", "", "(ZIILjava/util/List;Ljava/lang/String;)V", "getCount", "()I", "getError", "()Ljava/lang/String;", "getEvents", "()Ljava/util/List;", "getOk", "()Z", "getTotal", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "other", "hashCode", "toString", "app_debug"})
    public static final class DrainResult {
        private final boolean ok = false;
        private final int count = 0;
        private final int total = 0;
        @org.jetbrains.annotations.NotNull
        private final java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> events = null;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String error = null;
        
        public DrainResult(boolean ok, int count, int total, @org.jetbrains.annotations.NotNull
        java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> events, @org.jetbrains.annotations.NotNull
        java.lang.String error) {
            super();
        }
        
        public final boolean getOk() {
            return false;
        }
        
        public final int getCount() {
            return 0;
        }
        
        public final int getTotal() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> getEvents() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getError() {
            return null;
        }
        
        public final boolean component1() {
            return false;
        }
        
        public final int component2() {
            return 0;
        }
        
        public final int component3() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String component5() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final com.svcmonitor.app.StatusParser.DrainResult copy(boolean ok, int count, int total, @org.jetbrains.annotations.NotNull
        java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> events, @org.jetbrains.annotations.NotNull
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0005H\u00c6\u0003J\'\u0010\u0010\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0014\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0015\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0016"}, d2 = {"Lcom/svcmonitor/app/StatusParser$HookInfo;", "", "nr", "", "name", "", "method", "(ILjava/lang/String;Ljava/lang/String;)V", "getMethod", "()Ljava/lang/String;", "getName", "getNr", "()I", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "toString", "app_debug"})
    public static final class HookInfo {
        private final int nr = 0;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String name = null;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String method = null;
        
        public HookInfo(int nr, @org.jetbrains.annotations.NotNull
        java.lang.String name, @org.jetbrains.annotations.NotNull
        java.lang.String method) {
            super();
        }
        
        public final int getNr() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getName() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getMethod() {
            return null;
        }
        
        public final int component1() {
            return 0;
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
        public final com.svcmonitor.app.StatusParser.HookInfo copy(int nr, @org.jetbrains.annotations.NotNull
        java.lang.String name, @org.jetbrains.annotations.NotNull
        java.lang.String method) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0006\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b(\b\u0086\b\u0018\u00002\u00020\u0001B\u00a1\u0001\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0007\u001a\u00020\b\u0012\b\b\u0002\u0010\t\u001a\u00020\b\u0012\b\b\u0002\u0010\n\u001a\u00020\b\u0012\b\b\u0002\u0010\u000b\u001a\u00020\b\u0012\b\b\u0002\u0010\f\u001a\u00020\b\u0012\b\b\u0002\u0010\r\u001a\u00020\u0003\u0012\u000e\b\u0002\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\b0\u000f\u0012\b\b\u0002\u0010\u0010\u001a\u00020\b\u0012\u000e\b\u0002\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\b0\u000f\u0012\u000e\b\u0002\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00130\u000f\u0012\b\b\u0002\u0010\u0014\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0015J\t\u0010(\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010)\u001a\b\u0012\u0004\u0012\u00020\b0\u000fH\u00c6\u0003J\t\u0010*\u001a\u00020\bH\u00c6\u0003J\u000f\u0010+\u001a\b\u0012\u0004\u0012\u00020\b0\u000fH\u00c6\u0003J\u000f\u0010,\u001a\b\u0012\u0004\u0012\u00020\u00130\u000fH\u00c6\u0003J\t\u0010-\u001a\u00020\u0005H\u00c6\u0003J\t\u0010.\u001a\u00020\u0005H\u00c6\u0003J\t\u0010/\u001a\u00020\u0003H\u00c6\u0003J\t\u00100\u001a\u00020\bH\u00c6\u0003J\t\u00101\u001a\u00020\bH\u00c6\u0003J\t\u00102\u001a\u00020\bH\u00c6\u0003J\t\u00103\u001a\u00020\bH\u00c6\u0003J\t\u00104\u001a\u00020\bH\u00c6\u0003J\t\u00105\u001a\u00020\u0003H\u00c6\u0003J\u00a7\u0001\u00106\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00032\b\b\u0002\u0010\u0007\u001a\u00020\b2\b\b\u0002\u0010\t\u001a\u00020\b2\b\b\u0002\u0010\n\u001a\u00020\b2\b\b\u0002\u0010\u000b\u001a\u00020\b2\b\b\u0002\u0010\f\u001a\u00020\b2\b\b\u0002\u0010\r\u001a\u00020\u00032\u000e\b\u0002\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\b0\u000f2\b\b\u0002\u0010\u0010\u001a\u00020\b2\u000e\b\u0002\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\b0\u000f2\u000e\b\u0002\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00130\u000f2\b\b\u0002\u0010\u0014\u001a\u00020\u0005H\u00c6\u0001J\u0013\u00107\u001a\u00020\u00032\b\u00108\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u00109\u001a\u00020\bH\u00d6\u0001J\t\u0010:\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017R\u0011\u0010\u0014\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0019R\u0011\u0010\f\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u001bR\u0011\u0010\u000b\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u001bR\u0017\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00130\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u0011\u0010\t\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010\u001bR\u0017\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\b0\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u001eR\u0011\u0010\u0010\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\u001bR\u0017\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\b0\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010\u001eR\u0011\u0010\n\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b#\u0010\u001bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010\u0017R\u0011\u0010\u0007\u001a\u00020\b\u00a2\u0006\b\n\u0000\u001a\u0004\b%\u0010\u001bR\u0011\u0010\r\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b&\u0010\u0017R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\'\u0010\u0019\u00a8\u0006;"}, d2 = {"Lcom/svcmonitor/app/StatusParser$ModuleStatus;", "", "ok", "", "version", "", "enabled", "targetUid", "", "hooksInstalled", "nrsLogging", "eventsTotal", "eventsBuffered", "tier2", "loggingNrs", "", "nrCount", "nrList", "hooks", "Lcom/svcmonitor/app/StatusParser$HookInfo;", "error", "(ZLjava/lang/String;ZIIIIIZLjava/util/List;ILjava/util/List;Ljava/util/List;Ljava/lang/String;)V", "getEnabled", "()Z", "getError", "()Ljava/lang/String;", "getEventsBuffered", "()I", "getEventsTotal", "getHooks", "()Ljava/util/List;", "getHooksInstalled", "getLoggingNrs", "getNrCount", "getNrList", "getNrsLogging", "getOk", "getTargetUid", "getTier2", "getVersion", "component1", "component10", "component11", "component12", "component13", "component14", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "equals", "other", "hashCode", "toString", "app_debug"})
    public static final class ModuleStatus {
        private final boolean ok = false;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String version = null;
        private final boolean enabled = false;
        private final int targetUid = 0;
        private final int hooksInstalled = 0;
        private final int nrsLogging = 0;
        private final int eventsTotal = 0;
        private final int eventsBuffered = 0;
        private final boolean tier2 = false;
        @org.jetbrains.annotations.NotNull
        private final java.util.List<java.lang.Integer> loggingNrs = null;
        private final int nrCount = 0;
        @org.jetbrains.annotations.NotNull
        private final java.util.List<java.lang.Integer> nrList = null;
        @org.jetbrains.annotations.NotNull
        private final java.util.List<com.svcmonitor.app.StatusParser.HookInfo> hooks = null;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String error = null;
        
        public ModuleStatus(boolean ok, @org.jetbrains.annotations.NotNull
        java.lang.String version, boolean enabled, int targetUid, int hooksInstalled, int nrsLogging, int eventsTotal, int eventsBuffered, boolean tier2, @org.jetbrains.annotations.NotNull
        java.util.List<java.lang.Integer> loggingNrs, int nrCount, @org.jetbrains.annotations.NotNull
        java.util.List<java.lang.Integer> nrList, @org.jetbrains.annotations.NotNull
        java.util.List<com.svcmonitor.app.StatusParser.HookInfo> hooks, @org.jetbrains.annotations.NotNull
        java.lang.String error) {
            super();
        }
        
        public final boolean getOk() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getVersion() {
            return null;
        }
        
        public final boolean getEnabled() {
            return false;
        }
        
        public final int getTargetUid() {
            return 0;
        }
        
        public final int getHooksInstalled() {
            return 0;
        }
        
        public final int getNrsLogging() {
            return 0;
        }
        
        public final int getEventsTotal() {
            return 0;
        }
        
        public final int getEventsBuffered() {
            return 0;
        }
        
        public final boolean getTier2() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<java.lang.Integer> getLoggingNrs() {
            return null;
        }
        
        public final int getNrCount() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<java.lang.Integer> getNrList() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<com.svcmonitor.app.StatusParser.HookInfo> getHooks() {
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
        public final java.util.List<java.lang.Integer> component10() {
            return null;
        }
        
        public final int component11() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<java.lang.Integer> component12() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<com.svcmonitor.app.StatusParser.HookInfo> component13() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String component14() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String component2() {
            return null;
        }
        
        public final boolean component3() {
            return false;
        }
        
        public final int component4() {
            return 0;
        }
        
        public final int component5() {
            return 0;
        }
        
        public final int component6() {
            return 0;
        }
        
        public final int component7() {
            return 0;
        }
        
        public final int component8() {
            return 0;
        }
        
        public final boolean component9() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull
        public final com.svcmonitor.app.StatusParser.ModuleStatus copy(boolean ok, @org.jetbrains.annotations.NotNull
        java.lang.String version, boolean enabled, int targetUid, int hooksInstalled, int nrsLogging, int eventsTotal, int eventsBuffered, boolean tier2, @org.jetbrains.annotations.NotNull
        java.util.List<java.lang.Integer> loggingNrs, int nrCount, @org.jetbrains.annotations.NotNull
        java.util.List<java.lang.Integer> nrList, @org.jetbrains.annotations.NotNull
        java.util.List<com.svcmonitor.app.StatusParser.HookInfo> hooks, @org.jetbrains.annotations.NotNull
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\'\u0010\u000e\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0012\u001a\u00020\u0013H\u00d6\u0001J\t\u0010\u0014\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\bR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\b\u00a8\u0006\u0015"}, d2 = {"Lcom/svcmonitor/app/StatusParser$Preset;", "", "id", "", "name", "description", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", "getDescription", "()Ljava/lang/String;", "getId", "getName", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    public static final class Preset {
        @org.jetbrains.annotations.NotNull
        private final java.lang.String id = null;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String name = null;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String description = null;
        
        public Preset(@org.jetbrains.annotations.NotNull
        java.lang.String id, @org.jetbrains.annotations.NotNull
        java.lang.String name, @org.jetbrains.annotations.NotNull
        java.lang.String description) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getId() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getName() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getDescription() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String component1() {
            return null;
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
        public final com.svcmonitor.app.StatusParser.Preset copy(@org.jetbrains.annotations.NotNull
        java.lang.String id, @org.jetbrains.annotations.NotNull
        java.lang.String name, @org.jetbrains.annotations.NotNull
        java.lang.String description) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u000b\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\f\u001a\u00020\u0005H\u00c6\u0003J\u001d\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u000e\u001a\u00020\u00032\b\u0010\u000f\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0010\u001a\u00020\u0011H\u00d6\u0001J\t\u0010\u0012\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0013"}, d2 = {"Lcom/svcmonitor/app/StatusParser$SimpleResult;", "", "ok", "", "error", "", "(ZLjava/lang/String;)V", "getError", "()Ljava/lang/String;", "getOk", "()Z", "component1", "component2", "copy", "equals", "other", "hashCode", "", "toString", "app_debug"})
    public static final class SimpleResult {
        private final boolean ok = false;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String error = null;
        
        public SimpleResult(boolean ok, @org.jetbrains.annotations.NotNull
        java.lang.String error) {
            super();
        }
        
        public final boolean getOk() {
            return false;
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
        public final com.svcmonitor.app.StatusParser.SimpleResult copy(boolean ok, @org.jetbrains.annotations.NotNull
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010 \n\u0002\b:\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u00c3\u0001\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0005\u0012\u0006\u0010\t\u001a\u00020\u0005\u0012\u0006\u0010\n\u001a\u00020\u0005\u0012\u0006\u0010\u000b\u001a\u00020\u0007\u0012\b\b\u0002\u0010\f\u001a\u00020\u0003\u0012\b\b\u0002\u0010\r\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u000e\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u000f\u001a\u00020\u0003\u0012\u000e\b\u0002\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00030\u0011\u0012\b\b\u0002\u0010\u0012\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0013\u001a\u00020\u0003\u0012\u0006\u0010\u0014\u001a\u00020\u0003\u0012\u0006\u0010\u0015\u001a\u00020\u0003\u0012\u0006\u0010\u0016\u001a\u00020\u0003\u0012\u0006\u0010\u0017\u001a\u00020\u0003\u0012\u0006\u0010\u0018\u001a\u00020\u0003\u0012\u0006\u0010\u0019\u001a\u00020\u0003\u0012\u0006\u0010\u001a\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\u001bJ\t\u00105\u001a\u00020\u0003H\u00c6\u0003J\t\u00106\u001a\u00020\u0003H\u00c6\u0003J\t\u00107\u001a\u00020\u0003H\u00c6\u0003J\u000f\u00108\u001a\b\u0012\u0004\u0012\u00020\u00030\u0011H\u00c6\u0003J\t\u00109\u001a\u00020\u0003H\u00c6\u0003J\t\u0010:\u001a\u00020\u0003H\u00c6\u0003J\t\u0010;\u001a\u00020\u0003H\u00c6\u0003J\t\u0010<\u001a\u00020\u0003H\u00c6\u0003J\t\u0010=\u001a\u00020\u0003H\u00c6\u0003J\t\u0010>\u001a\u00020\u0003H\u00c6\u0003J\t\u0010?\u001a\u00020\u0003H\u00c6\u0003J\t\u0010@\u001a\u00020\u0005H\u00c6\u0003J\t\u0010A\u001a\u00020\u0003H\u00c6\u0003J\t\u0010B\u001a\u00020\u0007H\u00c6\u0003J\t\u0010C\u001a\u00020\u0007H\u00c6\u0003J\t\u0010D\u001a\u00020\u0005H\u00c6\u0003J\t\u0010E\u001a\u00020\u0005H\u00c6\u0003J\t\u0010F\u001a\u00020\u0005H\u00c6\u0003J\t\u0010G\u001a\u00020\u0007H\u00c6\u0003J\t\u0010H\u001a\u00020\u0003H\u00c6\u0003J\t\u0010I\u001a\u00020\u0003H\u00c6\u0003J\u00e1\u0001\u0010J\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00052\b\b\u0002\u0010\t\u001a\u00020\u00052\b\b\u0002\u0010\n\u001a\u00020\u00052\b\b\u0002\u0010\u000b\u001a\u00020\u00072\b\b\u0002\u0010\f\u001a\u00020\u00032\b\b\u0002\u0010\r\u001a\u00020\u00032\b\b\u0002\u0010\u000e\u001a\u00020\u00032\b\b\u0002\u0010\u000f\u001a\u00020\u00032\u000e\b\u0002\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00030\u00112\b\b\u0002\u0010\u0012\u001a\u00020\u00032\b\b\u0002\u0010\u0013\u001a\u00020\u00032\b\b\u0002\u0010\u0014\u001a\u00020\u00032\b\b\u0002\u0010\u0015\u001a\u00020\u00032\b\b\u0002\u0010\u0016\u001a\u00020\u00032\b\b\u0002\u0010\u0017\u001a\u00020\u00032\b\b\u0002\u0010\u0018\u001a\u00020\u00032\b\b\u0002\u0010\u0019\u001a\u00020\u00032\b\b\u0002\u0010\u001a\u001a\u00020\u0007H\u00c6\u0001J\u0013\u0010K\u001a\u00020L2\b\u0010M\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010N\u001a\u00020\u0005H\u00d6\u0001J\t\u0010O\u001a\u00020\u0007H\u00d6\u0001R\u0011\u0010\u0014\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u001dR\u0011\u0010\u0015\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u001dR\u0011\u0010\u0016\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010\u001dR\u0011\u0010\u0017\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u001dR\u0011\u0010\u0018\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\u001dR\u0011\u0010\u0019\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010\u001dR\u0017\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00030\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b#\u0010$R\u0011\u0010\r\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b%\u0010\u001dR\u0011\u0010\u0012\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b&\u0010\u001dR\u0011\u0010\u000b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\'\u0010(R\u0011\u0010\u001a\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b)\u0010(R\u0011\u0010\u000e\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b*\u0010\u001dR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b+\u0010(R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b,\u0010-R\u0011\u0010\f\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b.\u0010\u001dR\u0011\u0010\t\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b/\u0010-R\u0011\u0010\u0013\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b0\u0010\u001dR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b1\u0010\u001dR\u0011\u0010\u000f\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b2\u0010\u001dR\u0011\u0010\b\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b3\u0010-R\u0011\u0010\n\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b4\u0010-\u00a8\u0006P"}, d2 = {"Lcom/svcmonitor/app/StatusParser$SvcEvent;", "", "seq", "", "nr", "", "name", "", "tgid", "pid", "uid", "comm", "pc", "caller", "fp", "sp", "bt", "", "cloneFn", "ret", "a0", "a1", "a2", "a3", "a4", "a5", "desc", "(JILjava/lang/String;IIILjava/lang/String;JJJJLjava/util/List;JJJJJJJJLjava/lang/String;)V", "getA0", "()J", "getA1", "getA2", "getA3", "getA4", "getA5", "getBt", "()Ljava/util/List;", "getCaller", "getCloneFn", "getComm", "()Ljava/lang/String;", "getDesc", "getFp", "getName", "getNr", "()I", "getPc", "getPid", "getRet", "getSeq", "getSp", "getTgid", "getUid", "component1", "component10", "component11", "component12", "component13", "component14", "component15", "component16", "component17", "component18", "component19", "component2", "component20", "component21", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "equals", "", "other", "hashCode", "toString", "app_debug"})
    public static final class SvcEvent {
        private final long seq = 0L;
        private final int nr = 0;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String name = null;
        private final int tgid = 0;
        private final int pid = 0;
        private final int uid = 0;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String comm = null;
        private final long pc = 0L;
        private final long caller = 0L;
        private final long fp = 0L;
        private final long sp = 0L;
        @org.jetbrains.annotations.NotNull
        private final java.util.List<java.lang.Long> bt = null;
        private final long cloneFn = 0L;
        private final long ret = 0L;
        private final long a0 = 0L;
        private final long a1 = 0L;
        private final long a2 = 0L;
        private final long a3 = 0L;
        private final long a4 = 0L;
        private final long a5 = 0L;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String desc = null;
        
        public SvcEvent(long seq, int nr, @org.jetbrains.annotations.NotNull
        java.lang.String name, int tgid, int pid, int uid, @org.jetbrains.annotations.NotNull
        java.lang.String comm, long pc, long caller, long fp, long sp, @org.jetbrains.annotations.NotNull
        java.util.List<java.lang.Long> bt, long cloneFn, long ret, long a0, long a1, long a2, long a3, long a4, long a5, @org.jetbrains.annotations.NotNull
        java.lang.String desc) {
            super();
        }
        
        public final long getSeq() {
            return 0L;
        }
        
        public final int getNr() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getName() {
            return null;
        }
        
        public final int getTgid() {
            return 0;
        }
        
        public final int getPid() {
            return 0;
        }
        
        public final int getUid() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getComm() {
            return null;
        }
        
        public final long getPc() {
            return 0L;
        }
        
        public final long getCaller() {
            return 0L;
        }
        
        public final long getFp() {
            return 0L;
        }
        
        public final long getSp() {
            return 0L;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<java.lang.Long> getBt() {
            return null;
        }
        
        public final long getCloneFn() {
            return 0L;
        }
        
        public final long getRet() {
            return 0L;
        }
        
        public final long getA0() {
            return 0L;
        }
        
        public final long getA1() {
            return 0L;
        }
        
        public final long getA2() {
            return 0L;
        }
        
        public final long getA3() {
            return 0L;
        }
        
        public final long getA4() {
            return 0L;
        }
        
        public final long getA5() {
            return 0L;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getDesc() {
            return null;
        }
        
        public final long component1() {
            return 0L;
        }
        
        public final long component10() {
            return 0L;
        }
        
        public final long component11() {
            return 0L;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<java.lang.Long> component12() {
            return null;
        }
        
        public final long component13() {
            return 0L;
        }
        
        public final long component14() {
            return 0L;
        }
        
        public final long component15() {
            return 0L;
        }
        
        public final long component16() {
            return 0L;
        }
        
        public final long component17() {
            return 0L;
        }
        
        public final long component18() {
            return 0L;
        }
        
        public final long component19() {
            return 0L;
        }
        
        public final int component2() {
            return 0;
        }
        
        public final long component20() {
            return 0L;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String component21() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String component3() {
            return null;
        }
        
        public final int component4() {
            return 0;
        }
        
        public final int component5() {
            return 0;
        }
        
        public final int component6() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String component7() {
            return null;
        }
        
        public final long component8() {
            return 0L;
        }
        
        public final long component9() {
            return 0L;
        }
        
        @org.jetbrains.annotations.NotNull
        public final com.svcmonitor.app.StatusParser.SvcEvent copy(long seq, int nr, @org.jetbrains.annotations.NotNull
        java.lang.String name, int tgid, int pid, int uid, @org.jetbrains.annotations.NotNull
        java.lang.String comm, long pc, long caller, long fp, long sp, @org.jetbrains.annotations.NotNull
        java.util.List<java.lang.Long> bt, long cloneFn, long ret, long a0, long a1, long a2, long a3, long a4, long a5, @org.jetbrains.annotations.NotNull
        java.lang.String desc) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B#\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000e\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u00c6\u0003J-\u0010\u0011\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\u000e\b\u0002\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u00c6\u0001J\u0013\u0010\u0012\u001a\u00020\u00132\b\u0010\u0014\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0015\u001a\u00020\u0016H\u00d6\u0001J\t\u0010\u0017\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\nR\u0017\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\r\u00a8\u0006\u0018"}, d2 = {"Lcom/svcmonitor/app/StatusParser$SyscallCategory;", "", "name", "", "icon", "syscalls", "", "Lcom/svcmonitor/app/StatusParser$SyscallEntry;", "(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)V", "getIcon", "()Ljava/lang/String;", "getName", "getSyscalls", "()Ljava/util/List;", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    public static final class SyscallCategory {
        @org.jetbrains.annotations.NotNull
        private final java.lang.String name = null;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String icon = null;
        @org.jetbrains.annotations.NotNull
        private final java.util.List<com.svcmonitor.app.StatusParser.SyscallEntry> syscalls = null;
        
        public SyscallCategory(@org.jetbrains.annotations.NotNull
        java.lang.String name, @org.jetbrains.annotations.NotNull
        java.lang.String icon, @org.jetbrains.annotations.NotNull
        java.util.List<com.svcmonitor.app.StatusParser.SyscallEntry> syscalls) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getName() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getIcon() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<com.svcmonitor.app.StatusParser.SyscallEntry> getSyscalls() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<com.svcmonitor.app.StatusParser.SyscallEntry> component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final com.svcmonitor.app.StatusParser.SyscallCategory copy(@org.jetbrains.annotations.NotNull
        java.lang.String name, @org.jetbrains.annotations.NotNull
        java.lang.String icon, @org.jetbrains.annotations.NotNull
        java.util.List<com.svcmonitor.app.StatusParser.SyscallEntry> syscalls) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0005H\u00c6\u0003J\'\u0010\u0010\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0014\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0015\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u0016"}, d2 = {"Lcom/svcmonitor/app/StatusParser$SyscallEntry;", "", "nr", "", "name", "", "description", "(ILjava/lang/String;Ljava/lang/String;)V", "getDescription", "()Ljava/lang/String;", "getName", "getNr", "()I", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "toString", "app_debug"})
    public static final class SyscallEntry {
        private final int nr = 0;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String name = null;
        @org.jetbrains.annotations.NotNull
        private final java.lang.String description = null;
        
        public SyscallEntry(int nr, @org.jetbrains.annotations.NotNull
        java.lang.String name, @org.jetbrains.annotations.NotNull
        java.lang.String description) {
            super();
        }
        
        public final int getNr() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getName() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.lang.String getDescription() {
            return null;
        }
        
        public final int component1() {
            return 0;
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
        public final com.svcmonitor.app.StatusParser.SyscallEntry copy(int nr, @org.jetbrains.annotations.NotNull
        java.lang.String name, @org.jetbrains.annotations.NotNull
        java.lang.String description) {
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
}