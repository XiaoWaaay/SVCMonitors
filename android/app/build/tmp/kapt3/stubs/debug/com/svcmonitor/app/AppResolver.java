package com.svcmonitor.app;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J(\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00050\u00042\u0006\u0010\u0007\u001a\u00020\b2\b\b\u0002\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000b\u001a\u00020\nJ\u0006\u0010\f\u001a\u00020\rJ\u0016\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00050\u00042\u0006\u0010\u0007\u001a\u00020\bH\u0002J\u001c\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00050\u00042\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\u0010\u001a\u00020\u0011J0\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00050\u00042\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\u0010\u001a\u00020\u00112\b\b\u0002\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000b\u001a\u00020\nR\u0016\u0010\u0003\u001a\n\u0012\u0004\u0012\u00020\u0005\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0012"}, d2 = {"Lcom/svcmonitor/app/AppResolver;", "", "()V", "cachedAllApps", "", "Lcom/svcmonitor/app/AppInfo;", "getAllApps", "ctx", "Landroid/content/Context;", "hideSystemApps", "", "onlyLaunchableApps", "invalidateCache", "", "loadAllApps", "searchApps", "query", "", "app_debug"})
public final class AppResolver {
    @org.jetbrains.annotations.Nullable
    private static java.util.List<com.svcmonitor.app.AppInfo> cachedAllApps;
    @org.jetbrains.annotations.NotNull
    public static final com.svcmonitor.app.AppResolver INSTANCE = null;
    
    private AppResolver() {
        super();
    }
    
    private final java.util.List<com.svcmonitor.app.AppInfo> loadAllApps(android.content.Context ctx) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.util.List<com.svcmonitor.app.AppInfo> getAllApps(@org.jetbrains.annotations.NotNull
    android.content.Context ctx, boolean hideSystemApps, boolean onlyLaunchableApps) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.util.List<com.svcmonitor.app.AppInfo> searchApps(@org.jetbrains.annotations.NotNull
    android.content.Context ctx, @org.jetbrains.annotations.NotNull
    java.lang.String query) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.util.List<com.svcmonitor.app.AppInfo> searchApps(@org.jetbrains.annotations.NotNull
    android.content.Context ctx, @org.jetbrains.annotations.NotNull
    java.lang.String query, boolean hideSystemApps, boolean onlyLaunchableApps) {
        return null;
    }
    
    public final void invalidateCache() {
    }
}