package com.svcmonitor.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader

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
object KpmBridge {

    private const val TAG = "KpmBridge"
    private const val KPATCH = "/data/adb/ap/bin/kpatch"
    private const val MODULE = "svc_monitor"
    private const val EVENT_FILE = "/data/local/tmp/svc_events.jsonl"
    private var superKey = "XiaoLu0129"
    private val mutex = Mutex()

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
     * Core execution.
     *
     * IMPORTANT: If kpatch does NOT join remaining args (i.e. only takes exactly
     * one arg after module name), we need to quote:
     *   /path/kpatch SUPERKEY kpm ctl0 svc_monitor 'uid 10234'
     * We use single quotes to be safe.
     */
    private suspend fun execute(command: String): KpmResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val shellCmd = "$KPATCH $superKey kpm ctl0 $MODULE '$command'"
                val (exitCode, directOutput) = shellExec(shellCmd)
                val output = directOutput

                if (output.isNotEmpty()) {
                    val simple = StatusParser.parseSimple(output)
                    if (simple.ok) {
                        Log.d(TAG, "execute($command) OK: ${output.take(200)}")
                        KpmResult(true, output)
                    } else {
                        Log.w(TAG, "execute($command) FAIL: ${simple.error}")
                        KpmResult(false, output, simple.error)
                    }
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

    suspend fun setDoFilpOpen(enabled: Boolean) = execute(if (enabled) "filp_open on" else "filp_open off")

    fun getEventFilePath(): String = EVENT_FILE

    suspend fun eventFileSize(): Long = mutex.withLock {
        withContext(Dispatchers.IO) {
            val (_, out) = shellExec("wc -c < $EVENT_FILE 2>/dev/null")
            out.trim().toLongOrNull() ?: 0L
        }
    }

    suspend fun readEventFile(offset: Long, maxBytes: Int = 131072): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (offset < 0) return@withContext ""
            val count = if (maxBytes <= 0) 0 else maxBytes
            if (count == 0) return@withContext ""
            val (_, out) = shellExec("dd if=$EVENT_FILE bs=1 skip=$offset count=$count 2>/dev/null")
            out
        }
    }

    suspend fun truncateEventFile(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val (code, _) = shellExec(": > $EVENT_FILE 2>/dev/null")
            code == 0
        }
    }

    suspend fun rotateEventFile(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val ts = System.currentTimeMillis()
            val (code, _) = shellExec("cp $EVENT_FILE $EVENT_FILE.$ts 2>/dev/null && : > $EVENT_FILE 2>/dev/null")
            code == 0
        }
    }

    suspend fun readProcMaps(pid: Int): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val (_, out) = shellExec("cat /proc/$pid/maps 2>/dev/null")
            out
        }
    }

    suspend fun readProcFdLink(pid: Int, fd: Long): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val (_, out) = shellExec("readlink /proc/$pid/fd/$fd 2>/dev/null")
            out
        }
    }
}
