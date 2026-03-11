package com.svcmonitor.app;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0002\b\u0005\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001\rB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\tJ\u0018\u0010\n\u001a\u00020\u00042\u0006\u0010\u000b\u001a\u00020\t2\u0006\u0010\f\u001a\u00020\u0004H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2 = {"Lcom/svcmonitor/app/BinEventParser;", "", "()V", "MAGIC", "", "MAX_RECORD", "parse", "Lcom/svcmonitor/app/BinEventParser$ParseResult;", "buf", "", "u32le", "b", "o", "ParseResult", "app_debug"})
public final class BinEventParser {
    private static final int MAGIC = 1162040915;
    private static final int MAX_RECORD = 2048;
    @org.jetbrains.annotations.NotNull
    public static final com.svcmonitor.app.BinEventParser INSTANCE = null;
    
    private BinEventParser() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public final com.svcmonitor.app.BinEventParser.ParseResult parse(@org.jetbrains.annotations.NotNull
    byte[] buf) {
        return null;
    }
    
    private final int u32le(byte[] b, int o) {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u001b\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u000f\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0003J\t\u0010\r\u001a\u00020\u0006H\u00c6\u0003J#\u0010\u000e\u001a\u00020\u00002\u000e\b\u0002\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u00c6\u0001J\u0013\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0012\u001a\u00020\u0006H\u00d6\u0001J\t\u0010\u0013\u001a\u00020\u0014H\u00d6\u0001R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0015"}, d2 = {"Lcom/svcmonitor/app/BinEventParser$ParseResult;", "", "events", "", "Lcom/svcmonitor/app/StatusParser$SvcEvent;", "consumedBytes", "", "(Ljava/util/List;I)V", "getConsumedBytes", "()I", "getEvents", "()Ljava/util/List;", "component1", "component2", "copy", "equals", "", "other", "hashCode", "toString", "", "app_debug"})
    public static final class ParseResult {
        @org.jetbrains.annotations.NotNull
        private final java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> events = null;
        private final int consumedBytes = 0;
        
        public ParseResult(@org.jetbrains.annotations.NotNull
        java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> events, int consumedBytes) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> getEvents() {
            return null;
        }
        
        public final int getConsumedBytes() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull
        public final java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> component1() {
            return null;
        }
        
        public final int component2() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull
        public final com.svcmonitor.app.BinEventParser.ParseResult copy(@org.jetbrains.annotations.NotNull
        java.util.List<com.svcmonitor.app.StatusParser.SvcEvent> events, int consumedBytes) {
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