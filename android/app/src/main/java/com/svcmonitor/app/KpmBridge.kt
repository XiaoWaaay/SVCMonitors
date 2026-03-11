package com.svcmonitor.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

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
    private const val OUT_FILE = "/data/local/tmp/svc_out.json"
    private const val EVENT_FILE = "/data/local/tmp/svc_events.bin"
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
        val r = try {
            PersistentSuShell.exec(cmd)
        } catch (e: Exception) {
            Log.e(TAG, "shellExec persistent error", e)
            Pair(-1, e.message ?: "exec error")
        }
        if (r.first >= 0) return r

        return try {
            Log.d(TAG, "shellExec(fallback): $cmd")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            val combined = if (stdout.isNotEmpty()) stdout else stderr
            Log.d(TAG, "shellExec(fallback) exit=$exitCode out=${combined.take(200)}")
            Pair(exitCode, combined)
        } catch (e: Exception) {
            Log.e(TAG, "shellExec fallback error", e)
            Pair(-1, e.message ?: "exec error")
        }
    }

    private fun shellExecBytes(cmd: String): Pair<Int, ByteArray> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val data = process.inputStream.readBytes()
            val err = process.errorStream.readBytes()
            val exitCode = process.waitFor()
            if (exitCode != 0 && data.isEmpty() && err.isNotEmpty()) {
                Pair(exitCode, err)
            } else {
                Pair(exitCode, data)
            }
        } catch (e: Exception) {
            Pair(-1, (e.message ?: "exec error").toByteArray())
        }
    }

    private object PersistentSuShell {
        private var proc: Process? = null
        private var reader: BufferedReader? = null
        private var writer: BufferedWriter? = null

        private fun ensure(): Boolean {
            val p = proc
            if (p != null && p.isAlive && reader != null && writer != null) return true
            try {
                val np = ProcessBuilder("su").redirectErrorStream(true).start()
                proc = np
                reader = BufferedReader(InputStreamReader(np.inputStream))
                writer = BufferedWriter(OutputStreamWriter(np.outputStream))
                return true
            } catch (e: Exception) {
                proc = null
                reader = null
                writer = null
                return false
            }
        }

        fun exec(cmd: String, timeoutMs: Long = 8_000L): Pair<Int, String> {
            if (!ensure()) return Pair(-1, "su shell start failed")

            val nano = System.nanoTime()
            val endMarker = "__END_${nano}__"
            val rcMarker = "__RC_${nano}__"

            val w = writer ?: return Pair(-1, "su shell writer missing")
            val r = reader ?: return Pair(-1, "su shell reader missing")

            try {
                Log.d(TAG, "shellExec(persistent): $cmd")
                w.write("$cmd; echo $rcMarker$?; echo $endMarker\n")
                w.flush()

                val outLines = ArrayList<String>(32)
                var rc: Int? = null
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start <= timeoutMs) {
                    if (r.ready()) {
                        val line = r.readLine() ?: break
                        if (line == endMarker) {
                            val output = outLines.joinToString("\n").trim()
                            val code = rc ?: 0
                            Log.d(TAG, "shellExec(persistent) exit=$code out=${output.take(200)}")
                            return Pair(code, output)
                        }
                        if (line.startsWith(rcMarker)) {
                            rc = line.substring(rcMarker.length).trim().toIntOrNull() ?: 0
                        } else {
                            outLines.add(line)
                        }
                    } else {
                        Thread.sleep(10)
                    }
                }

                reset()
                return Pair(-1, "su shell timeout")
            } catch (e: Exception) {
                reset()
                return Pair(-1, e.message ?: "su shell error")
            }
        }

        private fun reset() {
            try { writer?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            try { proc?.destroy() } catch (_: Exception) {}
            proc = null
            reader = null
            writer = null
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
    suspend fun drain(max: Int = 1024): KpmResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val shellCmd = "$KPATCH $superKey kpm ctl0 $MODULE 'drain $max'"
                val (exitCode, directOutput) = shellExec(shellCmd)

                delay(80)

                val (_, fileOutput) = shellExec("cat $OUT_FILE 2>/dev/null")
                val output = if (fileOutput.isNotEmpty()) fileOutput else directOutput

                if (output.isNotEmpty()) {
                    val simple = StatusParser.parseSimple(output)
                    if (simple.ok) {
                        Log.d(TAG, "drain($max) OK: ${output.take(200)}")
                        KpmResult(true, output)
                    } else {
                        Log.w(TAG, "drain($max) FAIL: ${simple.error}")
                        KpmResult(false, output, simple.error)
                    }
                } else {
                    val errMsg = "exit=$exitCode, no output"
                    Log.w(TAG, "drain($max) FAIL: $errMsg")
                    KpmResult(false, "", errMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "drain($max) exception", e)
                KpmResult(false, "", e.message ?: "Unknown error")
            }
        }
    }
    suspend fun events() = execute("events")
    suspend fun clear() = execute("clear")

    suspend fun clearEventFile(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val (rc, _) = shellExecBytes(": > $EVENT_FILE")
            rc == 0
        }
    }

    suspend fun readEventFileChunk(offset: Long, maxBytes: Int): ByteArray = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (offset < 0 || maxBytes <= 0) return@withContext ByteArray(0)
            val cmd = "dd if=$EVENT_FILE bs=1 skip=$offset count=$maxBytes 2>/dev/null"
            val (rc, out) = shellExecBytes(cmd)
            if (rc == 0) out else ByteArray(0)
        }
    }

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

    suspend fun readProcMemQwords(pid: Int, addr: Long, qwords: Int = 2): List<Long> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (pid <= 0 || addr <= 0L || qwords <= 0) return@withContext emptyList()
            val count = qwords * 8
            val cmd = "dd if=/proc/$pid/mem bs=1 skip=$addr count=$count 2>/dev/null | od -An -t x8 2>/dev/null"
            val (_, out) = shellExec(cmd)
            if (out.isBlank()) return@withContext emptyList()
            val toks = out.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val res = ArrayList<Long>(qwords)
            for (t in toks) {
                val v = t.toLongOrNull(16) ?: continue
                res.add(v)
                if (res.size >= qwords) break
            }
            res
        }
    }
}
