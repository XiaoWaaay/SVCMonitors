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
    val uid: Int,
    val isSystem: Boolean,
    val isLaunchable: Boolean
)

object AppResolver {

    private var cachedAllApps: List<AppInfo>? = null

    private fun loadAllApps(ctx: Context): List<AppInfo> {
        cachedAllApps?.let { return it }

        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(PackageManager.MATCH_ALL)
            .map { ai ->
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val isLaunchable = pm.getLaunchIntentForPackage(ai.packageName) != null
                AppInfo(
                    label = ai.loadLabel(pm).toString(),
                    packageName = ai.packageName,
                    uid = ai.uid,
                    isSystem = isSystem,
                    isLaunchable = isLaunchable
                )
            }
            .distinctBy { it.uid }
            .sortedBy { it.label.lowercase() }

        cachedAllApps = apps
        return apps
    }

    fun getAllApps(
        ctx: Context,
        hideSystemApps: Boolean = false,
        onlyLaunchableApps: Boolean = false
    ): List<AppInfo> {
        return loadAllApps(ctx).filter { ai ->
            (!hideSystemApps || !ai.isSystem) &&
                (!onlyLaunchableApps || ai.isLaunchable)
        }
    }

    fun searchApps(ctx: Context, query: String): List<AppInfo> {
        val q = query.lowercase()
        return loadAllApps(ctx).filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    fun searchApps(
        ctx: Context,
        query: String,
        hideSystemApps: Boolean = false,
        onlyLaunchableApps: Boolean = false
    ): List<AppInfo> {
        val q = query.lowercase()
        return loadAllApps(ctx).filter { ai ->
            (!hideSystemApps || !ai.isSystem) &&
                (!onlyLaunchableApps || ai.isLaunchable) &&
                (ai.label.lowercase().contains(q) || ai.packageName.lowercase().contains(q))
        }
    }

    fun invalidateCache() {
        cachedAllApps = null
    }
}
