package com.svcmonitor.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * AppResolver v8.0 — Resolve installed APP list for UID selection.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val uid: Int
)

object AppResolver {

    private var cachedApps: List<AppInfo>? = null

    fun getAllApps(ctx: Context): List<AppInfo> {
        cachedApps?.let { return it }

        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { ai ->
                AppInfo(
                    label = ai.loadLabel(pm).toString(),
                    packageName = ai.packageName,
                    uid = ai.uid
                )
            }
            .distinctBy { it.uid }
            .sortedBy { it.label.lowercase() }

        cachedApps = apps
        return apps
    }

    fun searchApps(ctx: Context, query: String): List<AppInfo> {
        val q = query.lowercase()
        return getAllApps(ctx).filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    fun invalidateCache() {
        cachedApps = null
    }
}
