/* ============================================================================
 * syscall_monitor.c - Syscall 监控核心实现 (修复版)
 * ============================================================================
 * 版本: 3.1.0
 * 修复: 1. 使用 safe_get_xxx() 替代未定义的 task_struct_offset
 *       2. 使用 safe_get_task_comm() 替代未定义的 get_task_comm()
 *       3. 使用 kp_malloc 分配 svc_event 避免栈溢出
 *       4. 添加递归 hook 保护 (防止 hook 回调中的 syscall 被再次 hook)
 *       5. copy_from_user 前增加空指针检查
 * ============================================================================ */

#include <compiler.h>
#include <ktypes.h>
#include <stdint.h>
#include <kpmodule.h>
#include <linux/printk.h>
#include <linux/string.h>
#include <linux/kernel.h>
#include <kpmalloc.h>
#include "svc_tracer.h"

/* --------------------------------------------------------------------------
 * 全局配置与统计
 * -------------------------------------------------------------------------- */
struct tracer_config g_config = {
    .running             = 0,
    .pid_count           = 0,
    .filter_uid          = -1,
    .filter_pkg          = {0},
    .filter_comm         = {0},
    .category_mask       = SC_CAT_ALL,
    .filtered_syscall_count = 0,
    .capture_args        = 1,
    .capture_caller      = 1,
    .capture_backtrace   = 0,   /* 默认关闭回溯, 避免性能问题 */
    .capture_retval      = 1,
    .detect_antidebug    = 1,
    .json_output         = 1,
    .file_log_enabled    = 0,
    .file_log_path       = "/sdcard/Download/svc_tracer.log",
};

struct tracer_stats g_stats = {0};

/* --------------------------------------------------------------------------
 * 递归保护: 防止 hook 回调中的操作触发其他被 hook 的 syscall
 * 使用一个简单的 per-thread 标记来防止递归
 * -------------------------------------------------------------------------- */
static int g_in_hook = 0;  /* 简化版: 全局递归标记 */

/* --------------------------------------------------------------------------
 * syscall 号定义
 * -------------------------------------------------------------------------- */
#define __NR_eventfd2       19
#define __NR_epoll_create1  20
#define __NR_epoll_ctl      21
#define __NR_dup            23
#define __NR_dup3           24
#define __NR_ioctl          29
#define __NR_mkdirat        34
#define __NR_unlinkat       35
#define __NR_faccessat      48
#define __NR_openat         56
#define __NR_close          57
#define __NR_pipe2          59
#define __NR_lseek          62
#define __NR_read           63
#define __NR_write          64
#define __NR_readv          65
#define __NR_writev         66
#define __NR_readlinkat     78
#define __NR_fstat          80
#define __NR_exit_group     94
#define __NR_ptrace         117
#define __NR_kill           129
#define __NR_tgkill         131
#define __NR_rt_sigaction   134
#define __NR_setpgid        154
#define __NR_prctl          167
#define __NR_getpid         172
#define __NR_getuid         174
#define __NR_socket         198
#define __NR_bind           200
#define __NR_listen         201
#define __NR_connect        203
#define __NR_getsockname    204
#define __NR_sendto         206
#define __NR_recvfrom       207
#define __NR_setsockopt     208
#define __NR_munmap         215
#define __NR_mremap         216
#define __NR_clone          220
#define __NR_execve         221
#define __NR_mmap           222
#define __NR_mprotect       226
#define __NR_madvise        233
#define __NR_accept4        242
#define __NR_wait4          260
#define __NR_renameat2      276

#define PR_SET_DUMPABLE     4
#define SIGTRAP             5

/* --------------------------------------------------------------------------
 * get_syscall_name
 * -------------------------------------------------------------------------- */
const char *get_syscall_name(int nr)
{
    switch (nr) {
    case __NR_openat:       return "openat";
    case __NR_close:        return "close";
    case __NR_read:         return "read";
    case __NR_write:        return "write";
    case __NR_readv:        return "readv";
    case __NR_writev:       return "writev";
    case __NR_lseek:        return "lseek";
    case __NR_fstat:        return "fstat";
    case __NR_ioctl:        return "ioctl";
    case __NR_dup:          return "dup";
    case __NR_dup3:         return "dup3";
    case __NR_pipe2:        return "pipe2";
    case __NR_readlinkat:   return "readlinkat";
    case __NR_faccessat:    return "faccessat";
    case __NR_unlinkat:     return "unlinkat";
    case __NR_mkdirat:      return "mkdirat";
    case __NR_renameat2:    return "renameat2";
    case __NR_execve:       return "execve";
    case __NR_clone:        return "clone";
    case __NR_wait4:        return "wait4";
    case __NR_exit_group:   return "exit_group";
    case __NR_getpid:       return "getpid";
    case __NR_getuid:       return "getuid";
    case __NR_prctl:        return "prctl";
    case __NR_setpgid:      return "setpgid";
    case __NR_mmap:         return "mmap";
    case __NR_munmap:       return "munmap";
    case __NR_mprotect:     return "mprotect";
    case __NR_madvise:      return "madvise";
    case __NR_mremap:       return "mremap";
    case __NR_socket:       return "socket";
    case __NR_connect:      return "connect";
    case __NR_bind:         return "bind";
    case __NR_listen:       return "listen";
    case __NR_accept4:      return "accept4";
    case __NR_sendto:       return "sendto";
    case __NR_recvfrom:     return "recvfrom";
    case __NR_setsockopt:   return "setsockopt";
    case __NR_getsockname:  return "getsockname";
    case __NR_ptrace:       return "ptrace";
    case __NR_rt_sigaction: return "rt_sigaction";
    case __NR_kill:         return "kill";
    case __NR_tgkill:       return "tgkill";
    case __NR_epoll_create1: return "epoll_create1";
    case __NR_epoll_ctl:    return "epoll_ctl";
    case __NR_eventfd2:     return "eventfd2";
    default:                return "unknown";
    }
}

unsigned char get_syscall_category(int nr)
{
    switch (nr) {
    case __NR_openat: case __NR_close: case __NR_read: case __NR_write:
    case __NR_readv: case __NR_writev: case __NR_lseek: case __NR_fstat:
    case __NR_ioctl: case __NR_dup: case __NR_dup3: case __NR_pipe2:
    case __NR_readlinkat: case __NR_faccessat: case __NR_unlinkat:
    case __NR_mkdirat: case __NR_renameat2:
        return SC_CAT_FILE;
    case __NR_execve: case __NR_clone: case __NR_wait4:
    case __NR_exit_group: case __NR_getpid: case __NR_getuid:
    case __NR_prctl: case __NR_setpgid:
        return SC_CAT_PROC;
    case __NR_mmap: case __NR_munmap: case __NR_mprotect:
    case __NR_madvise: case __NR_mremap:
        return SC_CAT_MEM;
    case __NR_socket: case __NR_connect: case __NR_bind:
    case __NR_listen: case __NR_accept4: case __NR_sendto:
    case __NR_recvfrom: case __NR_setsockopt: case __NR_getsockname:
        return SC_CAT_NET;
    case __NR_rt_sigaction: case __NR_kill: case __NR_tgkill:
        return SC_CAT_SIGNAL;
    case __NR_ptrace:
        return SC_CAT_ANTIDEBUG;
    case __NR_epoll_create1: case __NR_epoll_ctl: case __NR_eventfd2:
        return SC_CAT_IPC;
    default:
        return 0;
    }
}

/* --------------------------------------------------------------------------
 * should_monitor - 多级过滤
 * 修复: 包名过滤仅使用缓存, 不在 hook 上下文中执行文件 I/O
 * -------------------------------------------------------------------------- */
static int should_monitor(int tgid, unsigned int uid, const char *comm)
{
    int i;

    /* 1. PID 列表过滤 */
    if (g_config.pid_count > 0) {
        int found = 0;
        for (i = 0; i < g_config.pid_count; i++) {
            if (g_config.monitored_pids[i] == tgid) {
                found = 1;
                break;
            }
        }
        if (!found)
            return 0;
    }

    /* 2. UID 过滤 */
    if (g_config.filter_uid >= 0) {
        if ((int)uid != g_config.filter_uid)
            return 0;
    }

    /* 3. comm 过滤 */
    if (g_config.filter_comm[0] != '\0') {
        if (strncmp(comm, g_config.filter_comm, MAX_COMM_LEN) != 0)
            return 0;
    }

    /* 4. 包名过滤 (仅查缓存, 不执行文件 I/O) */
    if (g_config.filter_pkg[0] != '\0' && g_config.filter_uid < 0) {
        char pkg_buf[MAX_PKG_LEN];
        /*
         * 修复: pkg_resolve_uid_to_pkg 现在只查内存缓存,
         * 不会在 hook 上下文中执行文件 I/O。
         * 包名映射由用户空间通过 CTL0 的 pkg_add 命令预先注入。
         */
        if (pkg_resolve_uid_to_pkg(uid, pkg_buf, MAX_PKG_LEN) == 0) {
            if (strncmp(pkg_buf, g_config.filter_pkg, MAX_PKG_LEN) != 0)
                return 0;
        }
        /* 缓存未命中时不过滤, 允许通过 (宁可多记录, 不要漏记录) */
    }

    return 1;
}

/* --------------------------------------------------------------------------
 * is_antidebug_behavior - 反调试行为检测
 * 修复: 增加 kfunc_copy_from_user 空指针检查
 * -------------------------------------------------------------------------- */
static int is_antidebug_behavior(int nr, unsigned long *args)
{
    if (nr == __NR_ptrace)
        return 1;

    if (nr == __NR_prctl) {
        if (args[0] == PR_SET_DUMPABLE && args[1] == 0)
            return 1;
    }

    if (nr == __NR_rt_sigaction) {
        if ((int)args[0] == SIGTRAP)
            return 1;
    }

    if (nr == __NR_openat || nr == __NR_faccessat || nr == __NR_readlinkat) {
        char path_buf[256];
        unsigned long path_addr = args[1];

        if (path_addr == 0)
            return 0;

        /* 修复: 必须检查 kfunc_copy_from_user 非 NULL */
        if (!kfunc_copy_from_user)
            return 0;

        memset(path_buf, 0, sizeof(path_buf));
        if (kfunc_copy_from_user(path_buf, (const void __user *)path_addr, 255) != 0)
            return 0;
        path_buf[255] = '\0';

        if (strstr(path_buf, "/proc/self/status") ||
            strstr(path_buf, "/proc/self/maps") ||
            strstr(path_buf, "/proc/self/mem") ||
            strstr(path_buf, "/proc/self/wchan") ||
            strstr(path_buf, "/proc/self/task") ||
            strstr(path_buf, "/proc/self/fd"))
            return 1;

        if (strstr(path_buf, "frida") ||
            strstr(path_buf, "linjector") ||
            strstr(path_buf, "gadget"))
            return 1;

        if (strstr(path_buf, "magisk") ||
            strstr(path_buf, "/su") ||
            strstr(path_buf, "/sbin/su") ||
            strstr(path_buf, "supersu"))
            return 1;
    }

    return 0;
}

/* --------------------------------------------------------------------------
 * safe_strncpy_from_user
 * -------------------------------------------------------------------------- */
static int safe_strncpy_from_user(char *dst, unsigned long user_addr, int maxlen)
{
    if (user_addr == 0 || maxlen <= 0 || !kfunc_copy_from_user) {
        dst[0] = '\0';
        return 0;
    }

    memset(dst, 0, maxlen);
    if (kfunc_copy_from_user(dst, (const void __user *)user_addr, maxlen - 1) != 0) {
        dst[0] = '\0';
        return -1;
    }
    dst[maxlen - 1] = '\0';
    return strlen(dst);
}

/* --------------------------------------------------------------------------
 * parse_args - 参数解析 (精简版, 只解析最常用的 syscall)
 * -------------------------------------------------------------------------- */
static void parse_args(int nr, unsigned long *args, long retval,
                        char *detail, int detail_len)
{
    char path_buf[128];
    detail[0] = '\0';

    switch (nr) {
    case __NR_openat:
        safe_strncpy_from_user(path_buf, args[1], sizeof(path_buf));
        snprintf(detail, detail_len,
                 "dirfd=%ld path=\"%s\" flags=0x%lx",
                 (long)args[0], path_buf, args[2]);
        break;

    case __NR_close:
        snprintf(detail, detail_len, "fd=%ld", (long)args[0]);
        break;

    case __NR_read:
        snprintf(detail, detail_len, "fd=%ld count=%lu",
                 (long)args[0], args[2]);
        break;

    case __NR_write:
        snprintf(detail, detail_len, "fd=%ld count=%lu",
                 (long)args[0], args[2]);
        break;

    case __NR_ioctl:
        snprintf(detail, detail_len, "fd=%ld cmd=0x%lx",
                 (long)args[0], args[1]);
        break;

    case __NR_execve:
        safe_strncpy_from_user(path_buf, args[0], sizeof(path_buf));
        snprintf(detail, detail_len, "filename=\"%s\"", path_buf);
        break;

    case __NR_clone:
        snprintf(detail, detail_len, "flags=0x%lx", args[0]);
        break;

    case __NR_mmap:
        snprintf(detail, detail_len,
                 "addr=0x%lx len=%lu prot=0x%lx flags=0x%lx fd=%ld",
                 args[0], args[1], args[2], args[3], (long)args[4]);
        break;

    case __NR_mprotect:
        snprintf(detail, detail_len, "addr=0x%lx len=%lu prot=0x%lx",
                 args[0], args[1], args[2]);
        break;

    case __NR_socket:
        snprintf(detail, detail_len, "domain=%ld type=%ld",
                 (long)args[0], (long)args[1]);
        break;

    case __NR_connect:
        snprintf(detail, detail_len, "sockfd=%ld addr=0x%lx",
                 (long)args[0], args[1]);
        break;

    case __NR_ptrace:
        snprintf(detail, detail_len, "request=%ld pid=%ld",
                 (long)args[0], (long)args[1]);
        break;

    case __NR_kill:
        snprintf(detail, detail_len, "pid=%ld sig=%ld",
                 (long)args[0], (long)args[1]);
        break;

    case __NR_prctl:
        snprintf(detail, detail_len, "option=%ld arg2=0x%lx",
                 (long)args[0], args[1]);
        break;

    case __NR_readlinkat:
        safe_strncpy_from_user(path_buf, args[1], sizeof(path_buf));
        snprintf(detail, detail_len, "path=\"%s\"", path_buf);
        break;

    case __NR_faccessat:
        safe_strncpy_from_user(path_buf, args[1], sizeof(path_buf));
        snprintf(detail, detail_len, "path=\"%s\" mode=%ld",
                 path_buf, (long)args[2]);
        break;

    default:
        snprintf(detail, detail_len, "arg0=0x%lx arg1=0x%lx",
                 args[0], args[1]);
        break;
    }
}

/* ============================================================================
 * syscall_monitor_init
 * ============================================================================ */
int syscall_monitor_init(void)
{
    memset(&g_stats, 0, sizeof(g_stats));
    g_in_hook = 0;
    pr_info("[svc-tracer] syscall monitor initialized\n");
    return 0;
}

/* ============================================================================
 * syscall_monitor_on_syscall - 主入口 (修复版)
 * ============================================================================
 * 关键修复:
 * 1. 使用 safe_get_xxx() 获取进程信息, 不依赖未定义的偏移量
 * 2. 使用堆分配 svc_event, 避免栈溢出
 * 3. 添加递归保护
 * ============================================================================ */
void syscall_monitor_on_syscall(int nr, unsigned long *args,
                                 long retval, int narg)
{
    struct svc_event *event;
    int tgid, tid;
    unsigned int uid;
    unsigned char cat;
    const char *comm;

    /* 检查运行状态 */
    if (!g_config.running)
        return;

    /* 递归保护: 防止 hook 回调中的操作再次触发 hook */
    if (g_in_hook)
        return;
    g_in_hook = 1;

    /* 修复: 使用安全的内联函数获取进程信息 */
    tgid = safe_get_tgid();
    tid  = safe_get_tid();
    uid  = safe_get_uid();
    comm = safe_get_task_comm();

    /* 类别过滤 */
    cat = get_syscall_category(nr);
    if (cat != 0 && !(cat & g_config.category_mask)) {
        g_stats.filtered_events++;
        g_in_hook = 0;
        return;
    }

    /* Syscall 号过滤 */
    if (g_config.filtered_syscall_count > 0) {
        int i, found = 0;
        for (i = 0; i < g_config.filtered_syscall_count; i++) {
            if (g_config.filtered_syscalls[i] == nr) {
                found = 1;
                break;
            }
        }
        if (!found) {
            g_stats.filtered_events++;
            g_in_hook = 0;
            return;
        }
    }

    /* 多级进程过滤 */
    if (!should_monitor(tgid, uid, comm)) {
        g_stats.filtered_events++;
        g_in_hook = 0;
        return;
    }

    /* 修复: 使用 kp_malloc 分配事件, 避免约 ~450 字节的栈分配 */
    event = (struct svc_event *)kp_malloc(sizeof(struct svc_event));
    if (!event) {
        g_stats.dropped_events++;
        g_in_hook = 0;
        return;
    }
    memset(event, 0, sizeof(struct svc_event));

    /* 时间戳 */
    if (kfunc_ktime_get_ns)
        event->timestamp_ns = kfunc_ktime_get_ns();

    /* 进程信息 */
    event->pid = tgid;
    event->tid = tid;
    event->uid = uid;
    strncpy(event->comm, comm, MAX_COMM_LEN - 1);

    /* 系统调用信息 */
    event->syscall_nr = nr;
    if (args) {
        int i;
        int copy_count = (narg < 6) ? narg : 6;
        for (i = 0; i < copy_count; i++)
            event->args[i] = args[i];
    }
    event->category = cat;

    /* 返回值 */
    if (g_config.capture_retval)
        event->retval = retval;

    /* 反调试检测 */
    if (g_config.detect_antidebug && args) {
        event->is_antidebug = is_antidebug_behavior(nr, args);
        if (event->is_antidebug)
            g_stats.antidebug_events++;
    }

    /* 调用者信息 */
    if (g_config.capture_caller) {
        caller_resolve(&event->caller_pc, &event->caller_lr,
                        event->caller_module, &event->caller_offset);

        if (event->caller_module[0] == '\0' && event->caller_pc != 0) {
            maps_cache_lookup(tgid, event->caller_pc,
                              event->caller_module, &event->caller_offset);
        }
    }

    /* 回溯栈 (默认关闭, 性能敏感) */
    if (g_config.capture_backtrace) {
        event->backtrace_depth = caller_backtrace(
            event->backtrace, MAX_BACKTRACE_DEPTH);
    }

    /* 参数解析 */
    if (g_config.capture_args && args) {
        parse_args(nr, args, retval, event->detail, MAX_DETAIL_LEN);
    }

    /* 写入环形缓冲区 */
    g_stats.total_events++;
    if (event_logger_write(event) < 0) {
        g_stats.dropped_events++;
    }

    /* 文件日志 */
    if (g_config.file_log_enabled) {
        if (file_logger_write_event(event) == 0) {
            g_stats.file_log_writes++;
        } else {
            g_stats.file_log_errors++;
        }
    }

    kp_free(event);
    g_in_hook = 0;
}
