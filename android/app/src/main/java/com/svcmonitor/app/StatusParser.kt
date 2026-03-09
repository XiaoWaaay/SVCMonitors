package com.svcmonitor.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * StatusParser v8.0 — Parse JSON responses from KPM module.
 */
object StatusParser {

    // ===== Data classes =====

    data class ModuleStatus(
        val ok: Boolean,
        val version: String = "",
        val enabled: Boolean = false,
        val targetUid: Int = -1,
        val hooksInstalled: Int = 0,
        val nrsLogging: Int = 0,
        val eventsTotal: Int = 0,
        val eventsBuffered: Int = 0,
        val tier2: Boolean = false,
        val loggingNrs: List<Int> = emptyList(),
        val nrCount: Int = 0,
        val nrList: List<Int> = emptyList(),
        val hooks: List<HookInfo> = emptyList(),
        val error: String = ""
    )

    data class HookInfo(
        val nr: Int,
        val name: String,
        val method: String
    )

    data class SvcEvent(
        val nr: Int,
        val name: String,
        val pid: Int,
        val uid: Int,
        val comm: String,
        val a0: Long, val a1: Long, val a2: Long,
        val a3: Long, val a4: Long, val a5: Long,
        val desc: String
    )

    data class DrainResult(
        val ok: Boolean,
        val count: Int = 0,
        val total: Int = 0,
        val events: List<SvcEvent> = emptyList(),
        val error: String = ""
    )

    data class SimpleResult(
        val ok: Boolean,
        val error: String = ""
    )

    // ===== Parsers =====

    fun parseStatus(json: String): ModuleStatus {
        return try {
            val j = JSONObject(json)
            if (!j.optBoolean("ok", false)) {
                return ModuleStatus(false, error = j.optString("error", "unknown"))
            }

            val loggingNrs = mutableListOf<Int>()
            val nrsArr = j.optJSONArray("logging_nrs")
            if (nrsArr != null) {
                for (i in 0 until nrsArr.length()) {
                    loggingNrs.add(nrsArr.getInt(i))
                }
            }

            val hooks = mutableListOf<HookInfo>()
            val hooksArr = j.optJSONArray("hooks")
            if (hooksArr != null) {
                for (i in 0 until hooksArr.length()) {
                    val h = hooksArr.getJSONObject(i)
                    hooks.add(HookInfo(
                        nr = h.optInt("nr"),
                        name = h.optString("name", ""),
                        method = h.optString("method", "")
                    ))
                }
            }

            ModuleStatus(
                ok = true,
                version = j.optString("version", ""),
                enabled = j.optBoolean("enabled", false),
                targetUid = j.optInt("target_uid", -1),
                hooksInstalled = j.optInt("hooks_installed", 0),
                nrsLogging = j.optInt("nrs_logging", 0),
                eventsTotal = j.optInt("events_total", 0),
                eventsBuffered = j.optInt("events_buffered", 0),
                tier2 = j.optBoolean("tier2", false),
                loggingNrs = loggingNrs,
                nrCount = loggingNrs.size,
                nrList = loggingNrs,
                hooks = hooks
            )
        } catch (e: Exception) {
            ModuleStatus(false, error = "Parse error: ${e.message}")
        }
    }

    fun parseDrain(json: String): DrainResult {
        return try {
            val j = JSONObject(json)
            if (!j.optBoolean("ok", false)) {
                return DrainResult(false, error = j.optString("error", ""))
            }

            val events = mutableListOf<SvcEvent>()
            val arr = j.optJSONArray("events")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    events.add(SvcEvent(
                        nr = e.optInt("nr"),
                        name = e.optString("name", ""),
                        pid = e.optInt("pid"),
                        uid = e.optInt("uid"),
                        comm = e.optString("comm", ""),
                        a0 = e.optLong("a0"), a1 = e.optLong("a1"),
                        a2 = e.optLong("a2"), a3 = e.optLong("a3"),
                        a4 = e.optLong("a4"), a5 = e.optLong("a5"),
                        desc = e.optString("desc", "")
                    ))
                }
            }

            DrainResult(
                ok = true,
                count = j.optInt("count", 0),
                total = j.optInt("total", 0),
                events = events
            )
        } catch (e: Exception) {
            DrainResult(false, error = "Parse error: ${e.message}")
        }
    }

    fun parseSimple(json: String): SimpleResult {
        return try {
            val j = JSONObject(json)
            SimpleResult(
                ok = j.optBoolean("ok", false),
                error = j.optString("error", "")
            )
        } catch (e: Exception) {
            SimpleResult(false, error = "Parse error: ${e.message}")
        }
    }

    // ===== Syscall categories for UI =====

    data class SyscallEntry(val nr: Int, val name: String, val description: String)
    data class SyscallCategory(val name: String, val icon: String, val syscalls: List<SyscallEntry>)

    data class Preset(val id: String, val name: String, val description: String)

    val presets = listOf(
        Preset("re_basic", "re_basic", "逆向基础"),
        Preset("re_full", "re_full", "逆向完整"),
        Preset("file", "file", "文件监控"),
        Preset("net", "net", "网络监控"),
        Preset("proc", "proc", "进程监控"),
        Preset("mem", "mem", "内存监控"),
        Preset("security", "security", "安全审计"),
        Preset("all", "all", "全部启用")
    )

    private val nrNameMap: Map<Int, String> by lazy {
        val m = HashMap<Int, String>()
        for (cat in categories) {
            for (s in cat.syscalls) {
                m[s.nr] = s.name
            }
        }
        m
    }

    fun nrToName(nr: Int): String = nrNameMap[nr] ?: "nr$nr"

    private val nrCategoryMap: Map<Int, String> by lazy {
        val m = HashMap<Int, String>()
        for (cat in categories) {
            for (s in cat.syscalls) {
                m[s.nr] = cat.name
            }
        }
        m
    }

    fun syscallCategory(nr: Int): String = nrCategoryMap[nr] ?: "-"

    val categories = listOf(
        SyscallCategory("文件操作", "📁", listOf(
            SyscallEntry(56, "openat", "打开文件"),
            SyscallEntry(57, "close", "关闭文件描述符"),
            SyscallEntry(48, "faccessat", "检查文件访问权限"),
            SyscallEntry(35, "unlinkat", "删除文件"),
            SyscallEntry(78, "readlinkat", "读取符号链接"),
            SyscallEntry(61, "getdents64", "读取目录"),
            SyscallEntry(63, "read", "读取数据"),
            SyscallEntry(64, "write", "写入数据"),
            SyscallEntry(79, "newfstatat", "获取文件状态"),
            SyscallEntry(291, "statx", "扩展文件状态"),
            SyscallEntry(276, "renameat2", "重命名文件"),
            SyscallEntry(34, "mkdirat", "创建目录")
        )),
        SyscallCategory("进程管理", "⚙", listOf(
            SyscallEntry(220, "clone", "创建进程/线程"),
            SyscallEntry(221, "execve", "执行程序"),
            SyscallEntry(281, "execveat", "执行程序(扩展)"),
            SyscallEntry(93, "exit", "退出进程"),
            SyscallEntry(94, "exit_group", "退出线程组"),
            SyscallEntry(260, "wait4", "等待子进程"),
            SyscallEntry(167, "prctl", "进程控制"),
            SyscallEntry(117, "ptrace", "进程追踪")
        )),
        SyscallCategory("内存管理", "🧠", listOf(
            SyscallEntry(222, "mmap", "内存映射"),
            SyscallEntry(226, "mprotect", "修改内存保护"),
            SyscallEntry(215, "munmap", "释放内存映射"),
            SyscallEntry(214, "brk", "调整堆大小"),
            SyscallEntry(233, "madvise", "内存使用建议"),
            SyscallEntry(279, "memfd_create", "创建匿名文件"),
            SyscallEntry(270, "process_vm_readv", "读取进程内存"),
            SyscallEntry(271, "process_vm_writev", "写入进程内存")
        )),
        SyscallCategory("网络通信", "🌐", listOf(
            SyscallEntry(198, "socket", "创建套接字"),
            SyscallEntry(200, "bind", "绑定地址"),
            SyscallEntry(201, "listen", "监听连接"),
            SyscallEntry(203, "connect", "发起连接"),
            SyscallEntry(202, "accept", "接受连接"),
            SyscallEntry(242, "accept4", "接受连接(扩展)"),
            SyscallEntry(206, "sendto", "发送数据"),
            SyscallEntry(207, "recvfrom", "接收数据")
        )),
        SyscallCategory("信号处理", "📡", listOf(
            SyscallEntry(129, "kill", "发送信号"),
            SyscallEntry(131, "tgkill", "发送线程信号"),
            SyscallEntry(134, "rt_sigaction", "设置信号处理")
        )),
        SyscallCategory("安全相关", "🔒", listOf(
            SyscallEntry(277, "seccomp", "安全计算模式"),
            SyscallEntry(268, "setns", "切换命名空间"),
            SyscallEntry(97, "unshare", "取消共享"),
            SyscallEntry(280, "bpf", "BPF操作")
        )),
        SyscallCategory("Tier2 扩展", "➕", listOf(
            SyscallEntry(29, "ioctl", "设备控制"),
            SyscallEntry(62, "lseek", "文件定位"),
            SyscallEntry(65, "readv", "分散读"),
            SyscallEntry(66, "writev", "聚集写"),
            SyscallEntry(25, "fcntl", "文件控制"),
            SyscallEntry(71, "sendfile", "文件间传输"),
            SyscallEntry(211, "sendmsg", "发送消息"),
            SyscallEntry(212, "recvmsg", "接收消息"),
            SyscallEntry(208, "setsockopt", "设置套接字选项"),
            SyscallEntry(209, "getsockopt", "获取套接字选项"),
            SyscallEntry(40, "mount", "挂载文件系统"),
            SyscallEntry(39, "umount2", "卸载文件系统"),
            SyscallEntry(261, "prlimit64", "资源限制"),
            SyscallEntry(90, "capget", "获取能力"),
            SyscallEntry(91, "capset", "设置能力"),
            SyscallEntry(146, "setuid", "设置用户ID"),
            SyscallEntry(144, "setgid", "设置组ID"),
            SyscallEntry(273, "finit_module", "加载内核模块(fd)"),
            SyscallEntry(105, "init_module", "加载内核模块"),
            SyscallEntry(106, "delete_module", "卸载内核模块")
        ))
    )
}
