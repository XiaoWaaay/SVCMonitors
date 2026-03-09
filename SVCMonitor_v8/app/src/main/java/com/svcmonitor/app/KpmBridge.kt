package com.svcmonitor.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * KpmBridge v8.0.1 — 3-phase communication with KPM module.
 *
 * Phase 0: rm -f /data/local/tmp/svc_out.json
 * Phase 1: kpatch <superkey> kpm ctl0 svc_monitor <command>
 * Phase 2: cat /data/local/tmp/svc_out.json
 *
 * FIX: Use sh -c with properly escaped shell command string
 * to avoid argument splitting issues with Runtime.exec(String[]).
 *
 * When using Runtime.exec(arrayOf("su","-c","...")), the third element
 * is passed to su which hands it to sh -c. So we need a single properly
 * quoted shell command string.
 */
object KpmBridge {

    private const val TAG = "KpmBridge"
    private const val KPATCH = "/data/adb/ap/bin/kpatch"
    private const val MODULE = "svc_monitor"
    private const val OUT_FILE = "/data/local/tmp/svc_out.json"
    private var superKey = "XiaoLu0129"

    data class KpmResult(
        val success: Boolean,
        val output: String,
        val error: String = ""
    )

    fun getSuperKey(): String = superKey
    fun setSuperKey(key: String) { superKey = key.trim() }

    /**
     * Execute a shell command via su and return stdout+stderr.
     */
    private fun shellExec(cmd: String): Pair<Int, String> {
        return try {
            Log.d(TAG, "shellExec: $cmd")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            val combined = if (stdout.isNotEmpty()) stdout else stderr
            Log.d(TAG, "shellExec exit=$exitCode out=${combined.take(200)}")
            Pair(exitCode, combined)
        } catch (e: Exception) {
            Log.e(TAG, "shellExec error", e)
            Pair(-1, e.message ?: "exec error")
        }
    }

    /**
     * Core 3-phase execution.
     *
     * The command string (e.g. "uid 10234") is passed to kpatch as a single
     * argument. Since kpatch ctl0 concatenates all remaining args after the
     * module name into one string, we do NOT need to quote it specially —
     * just pass it directly as part of the shell command.
     *
     * Shell command: /path/kpatch SUPERKEY kpm ctl0 svc_monitor uid 10234
     * kpatch will receive args: [SUPERKEY, kpm, ctl0, svc_monitor, uid, 10234]
     * and internally join "uid 10234" as the ctl0 args string.
     *
     * IMPORTANT: If kpatch does NOT join remaining args (i.e. only takes
     * exactly one arg after module name), we need to quote:
     *   /path/kpatch SUPERKEY kpm ctl0 svc_monitor 'uid 10234'
     * We use single quotes to be safe.
     */
    private suspend fun execute(command: String): KpmResult = withContext(Dispatchers.IO) {
        try {
            // Phase 0: remove old output
            shellExec("rm -f $OUT_FILE")

            // Phase 1: send command via kpatch ctl0
            // Use single quotes around the command to preserve it as one argument
            val shellCmd = "$KPATCH $superKey kpm ctl0 $MODULE '$command'"
            val (exitCode, directOutput) = shellExec(shellCmd)

            // Brief delay for kernel file write to complete
            delay(150)

            // Phase 2: read output file
            val (_, fileOutput) = shellExec("cat $OUT_FILE 2>/dev/null")

            // Prefer file output (larger, complete JSON), fall back to direct output
            val output = when {
                fileOutput.isNotEmpty() && fileOutput.startsWith("{") -> fileOutput
                directOutput.isNotEmpty() && directOutput.startsWith("{") -> directOutput
                fileOutput.isNotEmpty() -> fileOutput
                directOutput.isNotEmpty() -> directOutput
                else -> ""
            }

            if (output.isNotEmpty()) {
                Log.d(TAG, "execute($command) OK: ${output.take(200)}")
                KpmResult(true, output)
            } else {
                val errMsg = "exit=$exitCode, no output"
                Log.w(TAG, "execute($command) FAIL: $errMsg")
                KpmResult(false, "", errMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "execute($command) exception", e)
            KpmResult(false, "", e.message ?: "Unknown error")
        }
    }

    // ===== Monitoring control =====

    /** Start monitoring (enable callbacks) */
    suspend fun enable() = execute("enable")

    /** Stop monitoring (disable callbacks) */
    suspend fun disable() = execute("disable")

    /** Get module status */
    suspend fun status() = execute("status")

    // ===== Filter control =====

    /** Set target UID (-1 for all) */
    suspend fun setUid(uid: Int) = execute("uid $uid")

    /** Enable logging for a specific NR */
    suspend fun enableNr(nr: Int) = execute("enable_nr $nr")

    /** Disable logging for a specific NR */
    suspend fun disableNr(nr: Int) = execute("disable_nr $nr")

    /** Batch set NRs (replaces all) */
    suspend fun setNrs(nrs: List<Int>): KpmResult {
        return if (nrs.isEmpty()) {
            execute("disable_all")
        } else {
            execute("set_nrs ${nrs.joinToString(",")}")
        }
    }

    /** Enable all hooked NRs */
    suspend fun enableAll() = execute("enable_all")

    /** Disable all NRs */
    suspend fun disableAll() = execute("disable_all")

    /** Apply a preset */
    suspend fun preset(name: String) = execute("preset $name")

    // ===== Tier2 =====
    suspend fun tier2(on: Boolean) = execute("tier2 ${if (on) "on" else "off"}")

    // ===== Events =====
    suspend fun drain(max: Int = 100) = execute("drain $max")
    suspend fun events() = execute("events")
    suspend fun clear() = execute("clear")
}
