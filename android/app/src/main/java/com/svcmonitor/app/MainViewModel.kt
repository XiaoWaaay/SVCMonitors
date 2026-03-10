package com.svcmonitor.app

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MainViewModel v8.0.1 — Filter control + monitoring on/off + status polling.
 *
 * Key design:
 *   - Module loads with g_enabled=0 (paused)
 *   - APP sends "enable" to start monitoring, "disable" to stop
 *   - APP controls filter via uid/set_nrs/preset commands
 *   - Polling paused during command execution to avoid race
 *
 * FIX: More robust error handling in startMonitoring flow.
 *   - Don't abort the entire flow on non-critical step failure
 *   - Log all command results for debugging
 */
class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "SVCMon"
    }

    // ===== Status =====
    private val _status = MutableLiveData<StatusParser.ModuleStatus>()
    val status: LiveData<StatusParser.ModuleStatus> = _status

    // ===== Events =====
    private val _events = MutableLiveData<List<StatusParser.SvcEvent>>(emptyList())
    val events: LiveData<List<StatusParser.SvcEvent>> = _events
    private val eventBuffer = mutableListOf<StatusParser.SvcEvent>()
    private val maxEvents = 2000
    private var eventOffsetBytes: Long = 0L
    private var eventCarry: String = ""
    private val eventRotateThresholdBytes: Long = 4L * 1024L * 1024L
    var doFilpOpenEnabled: Boolean = true

    private val _eventCount = MutableLiveData(0)
    val eventCount: LiveData<Int> = _eventCount

    // ===== Message =====
    private val _toast = MutableLiveData<String?>()
    val toast: LiveData<String?> = _toast

    // ===== Monitoring state (local) =====
    private val _monitoring = MutableLiveData(false)
    val monitoring: LiveData<Boolean> = _monitoring

    var selectedApp: AppInfo? = null
    var selectedPreset: String = "re_basic"

    // ===== Polling =====
    private var pollingJob: Job? = null
    private var pollingPaused = false

    private val nrSet = mutableSetOf<Int>()

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                if (!pollingPaused) {
                    pollOnce()
                }
                delay(3000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun pollOnce() {
        try {
            // Poll status
            val statusResult = KpmBridge.status()
            if (statusResult.success && statusResult.output.isNotEmpty()) {
                val s = StatusParser.parseStatus(statusResult.output)
                _status.postValue(s)
                if (s.ok) {
                    _monitoring.postValue(s.enabled)
                    synchronized(nrSet) {
                        nrSet.clear()
                        nrSet.addAll(s.nrList)
                    }
                }
            }

            // Poll events only when monitoring is active
            if (_monitoring.value == true) {
                pollEventFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "pollOnce error", e)
        }
    }

    private suspend fun pollEventFile() {
        val size = KpmBridge.eventFileSize()
        if (size <= 0L) return

        if (size < eventOffsetBytes) {
            eventOffsetBytes = 0L
            eventCarry = ""
        }

        if (size > eventRotateThresholdBytes) {
            val rotated = KpmBridge.rotateEventFile()
            if (rotated) {
                eventOffsetBytes = 0L
                eventCarry = ""
                return
            }
        }

        val chunk = KpmBridge.readEventFile(eventOffsetBytes)
        if (chunk.isEmpty()) return
        eventOffsetBytes += chunk.toByteArray(Charsets.UTF_8).size.toLong()

        val merged = if (eventCarry.isNotEmpty()) eventCarry + chunk else chunk
        val lines = merged.split('\n')
        val complete = if (merged.endsWith("\n")) lines else lines.dropLast(1)
        eventCarry = if (merged.endsWith("\n")) "" else lines.lastOrNull().orEmpty()

        val parsed = ArrayList<StatusParser.SvcEvent>()
        for (raw in complete) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            parsed.addAll(StatusParser.parseEventLines(line))
        }
        if (parsed.isEmpty()) return

        synchronized(eventBuffer) {
            eventBuffer.addAll(parsed)
            while (eventBuffer.size > maxEvents) {
                eventBuffer.removeAt(0)
            }
            _events.postValue(ArrayList(eventBuffer))
            _eventCount.postValue(eventBuffer.size)
        }
    }

    // ===== One-click monitoring =====

    /**
     * One-click start: set UID + apply preset + enable monitoring.
     * All 3 steps are sent sequentially. If a step fails, we log it
     * but continue — the critical step is "enable".
     */
    fun startMonitoring(uid: Int) {
        viewModelScope.launch {
            pollingPaused = true
            try {
                eventOffsetBytes = 0L
                eventCarry = ""
                KpmBridge.truncateEventFile()
                synchronized(eventBuffer) {
                    eventBuffer.clear()
                    _events.postValue(emptyList())
                    _eventCount.postValue(0)
                }

                // Step 1: Set target UID
                Log.i(TAG, "startMonitoring: uid=$uid")
                val r1 = KpmBridge.setUid(uid)
                Log.i(TAG, "setUid result: success=${r1.success} output=${r1.output.take(200)} error=${r1.error}")
                if (!r1.success) {
                    // Non-fatal: continue anyway, maybe module will use default
                    Log.w(TAG, "setUid failed but continuing: ${r1.error}")
                }

                val rFilp = KpmBridge.setDoFilpOpen(doFilpOpenEnabled)
                Log.i(TAG, "do_filp_open result: success=${rFilp.success} output=${rFilp.output.take(200)} error=${rFilp.error}")

                // Step 2: Enable monitoring — this is the critical step
                val r3 = KpmBridge.enable()
                Log.i(TAG, "enable result: success=${r3.success} output=${r3.output.take(200)} error=${r3.error}")
                if (r3.success) {
                    _monitoring.postValue(true)
                    _toast.postValue("监控已启动" +
                        if (uid >= 0) " (UID: $uid)" else " (全部 APP)")
                } else {
                    _toast.postValue("启动监控失败: ${r3.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "startMonitoring exception", e)
                _toast.postValue("启动异常: ${e.message}")
            } finally {
                pollingPaused = false
                pollOnce()
            }
        }
    }

    /** Start with custom NRs instead of preset */
    fun startMonitoringWithNrs(uid: Int, nrs: List<Int>) {
        viewModelScope.launch {
            pollingPaused = true
            try {
                eventOffsetBytes = 0L
                eventCarry = ""
                KpmBridge.truncateEventFile()
                synchronized(eventBuffer) {
                    eventBuffer.clear()
                    _events.postValue(emptyList())
                    _eventCount.postValue(0)
                }

                Log.i(TAG, "startMonitoringWithNrs: uid=$uid nrs=${nrs.size}")
                val r1 = KpmBridge.setUid(uid)
                Log.i(TAG, "setUid result: ${r1.success} ${r1.output.take(100)}")

                val r2 = KpmBridge.setNrs(nrs)
                Log.i(TAG, "setNrs result: ${r2.success} ${r2.output.take(100)}")

                val rFilp = KpmBridge.setDoFilpOpen(doFilpOpenEnabled)
                Log.i(TAG, "do_filp_open result: success=${rFilp.success} output=${rFilp.output.take(200)} error=${rFilp.error}")

                val r3 = KpmBridge.enable()
                Log.i(TAG, "enable result: ${r3.success} ${r3.output.take(100)}")
                if (r3.success) {
                    _monitoring.postValue(true)
                    _toast.postValue("监控已启动 (${nrs.size} 个系统调用)")
                } else {
                    _toast.postValue("启动失败: ${r3.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "startMonitoringWithNrs exception", e)
                _toast.postValue("启动异常: ${e.message}")
            } finally {
                pollingPaused = false
                pollOnce()
            }
        }
    }

    /** Stop monitoring */
    fun stopMonitoring() {
        viewModelScope.launch {
            pollingPaused = true
            try {
                val r = KpmBridge.disable()
                if (r.success) {
                    _monitoring.postValue(false)
                    _toast.postValue("监控已停止")
                } else {
                    _toast.postValue("停止失败: ${r.error}")
                }
            } catch (e: Exception) {
                _toast.postValue("停止异常: ${e.message}")
            } finally {
                pollingPaused = false
                pollOnce()
            }
        }
    }

    fun setDoFilpOpen(enabled: Boolean) {
        doFilpOpenEnabled = enabled
        viewModelScope.launch {
            pollingPaused = true
            try {
                val r = KpmBridge.setDoFilpOpen(enabled)
                if (r.success) {
                    _toast.postValue(if (enabled) "do_filp_open 已开启" else "do_filp_open 已关闭")
                } else {
                    _toast.postValue("设置 do_filp_open 失败: ${r.error}")
                }
            } finally {
                pollingPaused = false
                pollOnce()
            }
        }
    }

    // ===== Individual filter controls =====

    fun setTargetUid(uid: Int) {
        viewModelScope.launch {
            pollingPaused = true
            try {
                val r = KpmBridge.setUid(uid)
                if (r.success) {
                    _toast.postValue(if (uid < 0) "已切换到监控所有 APP" else "目标 UID: $uid")
                } else {
                    _toast.postValue("设置失败: ${r.error}")
                }
            } finally {
                pollingPaused = false
                pollOnce()
            }
        }
    }

    fun applyPreset(presetName: String) {
        viewModelScope.launch {
            pollingPaused = true
            try {
                val r = KpmBridge.preset(presetName)
                if (r.success) {
                    _toast.postValue("预设已应用: $presetName")
                } else {
                    _toast.postValue("应用失败: ${r.error}")
                }
            } finally {
                pollingPaused = false
                pollOnce()
            }
        }
    }

    fun setNrs(nrs: List<Int>) {
        viewModelScope.launch {
            pollingPaused = true
            try {
                val r = KpmBridge.setNrs(nrs)
                if (r.success) {
                    _toast.postValue("已设置 ${nrs.size} 个系统调用")
                } else {
                    _toast.postValue("设置失败: ${r.error}")
                }
            } finally {
                pollingPaused = false
                pollOnce()
            }
        }
    }

    fun enableAll() {
        viewModelScope.launch {
            pollingPaused = true
            try { KpmBridge.enableAll() } finally { pollingPaused = false; pollOnce() }
        }
    }

    fun disableAll() {
        viewModelScope.launch {
            pollingPaused = true
            try { KpmBridge.disableAll() } finally { pollingPaused = false; pollOnce() }
        }
    }

    fun tier2(on: Boolean) {
        viewModelScope.launch {
            pollingPaused = true
            try {
                val r = KpmBridge.tier2(on)
                if (r.success) _toast.postValue(if (on) "Tier2 已加载" else "Tier2 已卸载")
            } finally {
                pollingPaused = false
                pollOnce()
            }
        }
    }

    fun clearEvents() {
        viewModelScope.launch {
            pollingPaused = true
            try {
                KpmBridge.clear()
                synchronized(eventBuffer) {
                    eventBuffer.clear()
                    _events.postValue(emptyList())
                    _eventCount.postValue(0)
                }
                _toast.postValue("事件已清空")
            } finally {
                pollingPaused = false
                pollOnce()
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { pollOnce() }
    }

    fun toastConsumed() {
        _toast.postValue(null)
    }

    fun addNr(nr: Int) {
        viewModelScope.launch {
            val already = synchronized(nrSet) { nrSet.contains(nr) }
            if (already) {
                _toast.postValue("NR $nr 已在列表中")
                return@launch
            }
            pollingPaused = true
            try {
                val r = KpmBridge.enableNr(nr)
                if (r.success) {
                    synchronized(nrSet) { nrSet.add(nr) }
                    _toast.postValue("已添加 NR $nr")
                } else {
                    _toast.postValue("添加失败: ${r.error}")
                }
            } finally {
                pollingPaused = false
                pollOnce()
            }
        }
    }

    fun removeNr(nr: Int) {
        viewModelScope.launch {
            val existed = synchronized(nrSet) { nrSet.contains(nr) }
            if (!existed) {
                _toast.postValue("NR $nr 不在列表中")
                return@launch
            }
            pollingPaused = true
            try {
                val r = KpmBridge.disableNr(nr)
                if (r.success) {
                    synchronized(nrSet) { nrSet.remove(nr) }
                    _toast.postValue("已移除 NR $nr")
                } else {
                    _toast.postValue("移除失败: ${r.error}")
                }
            } finally {
                pollingPaused = false
                pollOnce()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
