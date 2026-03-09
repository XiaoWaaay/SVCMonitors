/* ============================================================================
 * main.c - SVCModule KPM 入口文件 (修复版)
 * ============================================================================
 * 版本: 3.1.0
 * 修复: 修正错误处理 fallthrough label
 *       完善 kpm_exit 清理顺序
 *       增加 maps/pkg 的 CTL0 命令支持
 * ============================================================================ */

#include <compiler.h>
#include <kpmodule.h>
#include <linux/printk.h>
#include <linux/string.h>
#include <linux/kernel.h>
#include <kpmalloc.h>
#include "svc_tracer.h"

KPM_NAME("svc-tracer");
KPM_VERSION(SVC_TRACER_VERSION);
KPM_LICENSE("GPL v2");
KPM_AUTHOR("svc-tracer team");
KPM_DESCRIPTION("ARM64 syscall tracer with anti-debug detection for KernelPatch");

/* ---- 辅助宏 ---- */
#define OUT_MSG(fmt, ...) \
    do { \
        if (out_msg && outlen > 0) { \
            snprintf(out_msg, outlen, fmt, ##__VA_ARGS__); \
        } \
    } while (0)

static int str_starts_with(const char *str, const char *prefix)
{
    int len = strlen(prefix);
    return (strncmp(str, prefix, len) == 0);
}

static const char *skip_spaces_local(const char *s)
{
    while (*s == ' ' || *s == '\t')
        s++;
    return s;
}

static int simple_atoi(const char *s)
{
    int val = 0, neg = 0;
    if (*s == '-') { neg = 1; s++; }
    while (*s >= '0' && *s <= '9') {
        val = val * 10 + (*s - '0');
        s++;
    }
    return neg ? -val : val;
}

/* 初始化状态标记, 用于安全清理 */
static int g_init_stage = 0;

/* ============================================================================
 * kpm_init - 模块初始化入口 (修复版)
 * ============================================================================ */
static long kpm_init(const char *args, const char *__user reserved, void *__user outbuf)
{
    int ret;
    (void)reserved;
    (void)outbuf;

    pr_info("[svc-tracer] initializing v%s\n", SVC_TRACER_VERSION);
    g_init_stage = 0;

    /* 1. 初始化符号解析器 */
    ret = symbol_resolver_init();
    if (ret < 0) {
        pr_err("[svc-tracer] symbol_resolver_init failed: %d\n", ret);
        return ret;
    }
    g_init_stage = 1;

    /* 2. 初始化事件日志缓冲区 */
    ret = event_logger_init();
    if (ret < 0) {
        pr_err("[svc-tracer] event_logger_init failed: %d\n", ret);
        return ret;
    }
    g_init_stage = 2;

    /* 3. 初始化调用者解析器 */
    ret = caller_resolver_init();
    if (ret < 0) {
        pr_err("[svc-tracer] caller_resolver_init failed: %d\n", ret);
        goto fail_stage2;
    }
    g_init_stage = 3;

    /* 4. 初始化 maps 缓存 */
    ret = maps_cache_init();
    if (ret < 0) {
        pr_err("[svc-tracer] maps_cache_init failed: %d\n", ret);
        goto fail_stage2;
    }
    g_init_stage = 4;

    /* 5. 初始化包名解析器 */
    ret = pkg_resolver_init();
    if (ret < 0) {
        pr_err("[svc-tracer] pkg_resolver_init failed: %d\n", ret);
        goto fail_stage4;
    }
    g_init_stage = 5;

    /* 6. 初始化 syscall 监控器 */
    ret = syscall_monitor_init();
    if (ret < 0) {
        pr_err("[svc-tracer] syscall_monitor_init failed: %d\n", ret);
        goto fail_stage4;
    }
    g_init_stage = 6;

    /* 7. 初始化 hook 引擎 (不安装 hook) */
    ret = hook_engine_init();
    if (ret < 0) {
        pr_err("[svc-tracer] hook_engine_init failed: %d\n", ret);
        goto fail_stage4;
    }
    g_init_stage = 7;

    /* 8. 初始化文件日志 (非致命) */
    ret = file_logger_init();
    if (ret < 0) {
        pr_info("[svc-tracer] file_logger_init failed (non-fatal): %d\n", ret);
    } else {
        g_init_stage = 8;
    }

    if (args && strlen(args) > 0) {
        pr_info("[svc-tracer] init args: %s\n", args);
    }

    pr_info("[svc-tracer] initialized successfully\n");
    return 0;

fail_stage4:
    maps_cache_destroy();
fail_stage2:
    event_logger_destroy();
    g_init_stage = 0;
    return ret;
}

/* ============================================================================
 * kpm_control0 - 模块控制接口 (修复版, 增加 pid/maps/pkg 命令)
 * ============================================================================ */
static long kpm_control0(const char *args, char *__user out_msg, int outlen)
{
    const char *cmd;
    if (!args || strlen(args) == 0) {
        OUT_MSG("{\"error\":\"empty command\"}");
        return -1;
    }

    cmd = skip_spaces_local(args);

    /* status */
    if (strcmp(cmd, "status") == 0) {
        int pending = event_logger_pending();
        OUT_MSG("{\"status\":\"ok\",\"version\":\"%s\","
                "\"running\":%d,\"pid_count\":%d,"
                "\"filter_uid\":%d,\"filter_pkg\":\"%s\","
                "\"filter_comm\":\"%s\","
                "\"category_mask\":%d,\"syscall_filter_count\":%d,"
                "\"pending_events\":%d,\"hooks\":%d,"
                "\"capture_args\":%d,\"capture_caller\":%d,"
                "\"capture_bt\":%d,\"capture_retval\":%d,"
                "\"detect_antidebug\":%d,"
                "\"file_log\":%d}",
                SVC_TRACER_VERSION,
                g_config.running, g_config.pid_count,
                g_config.filter_uid, g_config.filter_pkg,
                g_config.filter_comm,
                g_config.category_mask, g_config.filtered_syscall_count,
                pending, hook_get_count(),
                g_config.capture_args, g_config.capture_caller,
                g_config.capture_backtrace, g_config.capture_retval,
                g_config.detect_antidebug,
                g_config.file_log_enabled);
        return 0;
    }

    /* start */
    if (strcmp(cmd, "start") == 0) {
        if (hook_get_count() == 0) {
            OUT_MSG("{\"error\":\"no hooks installed, run 'hooks slim' first\"}");
            return -1;
        }
        g_config.running = 1;
        OUT_MSG("{\"status\":\"ok\",\"message\":\"monitor started\"}");
        return 0;
    }

    /* stop */
    if (strcmp(cmd, "stop") == 0) {
        g_config.running = 0;
        OUT_MSG("{\"status\":\"ok\",\"message\":\"monitor stopped\"}");
        return 0;
    }

    /* clear */
    if (strcmp(cmd, "clear") == 0) {
        event_logger_clear();
        OUT_MSG("{\"status\":\"ok\",\"message\":\"events cleared\"}");
        return 0;
    }

    /* stats */
    if (strcmp(cmd, "stats") == 0) {
        OUT_MSG("{\"status\":\"ok\",\"total\":%llu,\"dropped\":%llu,"
                "\"filtered\":%llu,\"antidebug\":%llu,"
                "\"file_writes\":%llu,\"file_errors\":%llu,"
                "\"hooks\":%llu}",
                g_stats.total_events, g_stats.dropped_events,
                g_stats.filtered_events, g_stats.antidebug_events,
                g_stats.file_log_writes, g_stats.file_log_errors,
                g_stats.hook_count);
        return 0;
    }

    /* hooks */
    if (str_starts_with(cmd, "hooks")) {
        cmd = skip_spaces_local(cmd + 5);
        if (strcmp(cmd, "slim") == 0) {
            int count = hook_install_slim();
            OUT_MSG("{\"status\":\"ok\",\"hooks_installed\":%d}", count);
            return 0;
        }
        if (strcmp(cmd, "all") == 0) {
            int count = hook_install_all();
            OUT_MSG("{\"status\":\"ok\",\"hooks_installed\":%d}", count);
            return 0;
        }
        if (str_starts_with(cmd, "range")) {
            cmd = skip_spaces_local(cmd + 5);
            int max = simple_atoi(cmd);
            int count = hook_install_range(max);
            OUT_MSG("{\"status\":\"ok\",\"hooks_installed\":%d}", count);
            return 0;
        }
        OUT_MSG("{\"error\":\"usage: hooks slim|all|range N\"}");
        return -1;
    }

    /* pid add/del/clear/list - PID 管理命令 */
    if (str_starts_with(cmd, "pid")) {
        cmd = skip_spaces_local(cmd + 3);

        if (str_starts_with(cmd, "add")) {
            cmd = skip_spaces_local(cmd + 3);
            int pid = simple_atoi(cmd);
            if (pid <= 0) {
                OUT_MSG("{\"error\":\"invalid pid\"}");
                return -1;
            }
            if (g_config.pid_count >= MAX_MONITORED_PIDS) {
                OUT_MSG("{\"error\":\"pid list full (max %d)\"}", MAX_MONITORED_PIDS);
                return -1;
            }
            g_config.monitored_pids[g_config.pid_count++] = pid;
            OUT_MSG("{\"status\":\"ok\",\"pid\":%d,\"count\":%d}", pid, g_config.pid_count);
            return 0;
        }

        if (str_starts_with(cmd, "del")) {
            cmd = skip_spaces_local(cmd + 3);
            int pid = simple_atoi(cmd);
            int i, found = 0;
            for (i = 0; i < g_config.pid_count; i++) {
                if (g_config.monitored_pids[i] == pid) {
                    g_config.monitored_pids[i] = g_config.monitored_pids[g_config.pid_count - 1];
                    g_config.pid_count--;
                    found = 1;
                    break;
                }
            }
            OUT_MSG("{\"status\":\"ok\",\"removed\":%d,\"count\":%d}", found, g_config.pid_count);
            return 0;
        }

        if (strcmp(cmd, "clear") == 0) {
            g_config.pid_count = 0;
            OUT_MSG("{\"status\":\"ok\",\"message\":\"pid list cleared\"}");
            return 0;
        }

        if (strcmp(cmd, "list") == 0) {
            int pos = 0, i;
            pos += snprintf(out_msg + pos, outlen - pos, "{\"status\":\"ok\",\"pids\":[");
            for (i = 0; i < g_config.pid_count && pos < outlen - 32; i++) {
                pos += snprintf(out_msg + pos, outlen - pos, "%s%d",
                    i > 0 ? "," : "", g_config.monitored_pids[i]);
            }
            snprintf(out_msg + pos, outlen - pos, "]}");
            return 0;
        }

        OUT_MSG("{\"error\":\"usage: pid add|del|clear|list\"}");
        return -1;
    }

    /* pkg_add uid pkg_name - 手动添加包名映射 */
    if (str_starts_with(cmd, "pkg_add")) {
        cmd = skip_spaces_local(cmd + 7);
        int uid = simple_atoi(cmd);
        while (*cmd >= '0' && *cmd <= '9') cmd++;
        cmd = skip_spaces_local(cmd);
        if (*cmd && uid > 0) {
            pkg_resolver_add_entry((unsigned int)uid, cmd);
            OUT_MSG("{\"status\":\"ok\",\"uid\":%d,\"pkg\":\"%s\"}", uid, cmd);
            return 0;
        }
        OUT_MSG("{\"error\":\"usage: pkg_add <uid> <pkg_name>\"}");
        return -1;
    }

    /* config */
    if (str_starts_with(cmd, "config")) {
        cmd = skip_spaces_local(cmd + 6);

        if (str_starts_with(cmd, "uid")) {
            cmd = skip_spaces_local(cmd + 3);
            g_config.filter_uid = simple_atoi(cmd);
            OUT_MSG("{\"status\":\"ok\",\"uid\":%d}", g_config.filter_uid);
            return 0;
        }

        if (str_starts_with(cmd, "pkg ")) {
            cmd = skip_spaces_local(cmd + 3);
            strncpy(g_config.filter_pkg, cmd, MAX_PKG_LEN - 1);
            g_config.filter_pkg[MAX_PKG_LEN - 1] = '\0';
            OUT_MSG("{\"status\":\"ok\",\"pkg\":\"%s\"}", g_config.filter_pkg);
            return 0;
        }

        if (str_starts_with(cmd, "comm ")) {
            cmd = skip_spaces_local(cmd + 4);
            strncpy(g_config.filter_comm, cmd, MAX_COMM_LEN - 1);
            g_config.filter_comm[MAX_COMM_LEN - 1] = '\0';
            OUT_MSG("{\"status\":\"ok\",\"comm\":\"%s\"}", g_config.filter_comm);
            return 0;
        }

        if (str_starts_with(cmd, "cat")) {
            cmd = skip_spaces_local(cmd + 3);
            g_config.category_mask = (unsigned char)simple_atoi(cmd);
            OUT_MSG("{\"status\":\"ok\",\"category_mask\":%d}", g_config.category_mask);
            return 0;
        }

        if (str_starts_with(cmd, "capture_args")) {
            cmd = skip_spaces_local(cmd + 12);
            g_config.capture_args = simple_atoi(cmd) ? 1 : 0;
            OUT_MSG("{\"status\":\"ok\",\"capture_args\":%d}", g_config.capture_args);
            return 0;
        }

        if (str_starts_with(cmd, "capture_caller")) {
            cmd = skip_spaces_local(cmd + 14);
            g_config.capture_caller = simple_atoi(cmd) ? 1 : 0;
            OUT_MSG("{\"status\":\"ok\",\"capture_caller\":%d}", g_config.capture_caller);
            return 0;
        }

        if (str_starts_with(cmd, "capture_bt")) {
            cmd = skip_spaces_local(cmd + 10);
            g_config.capture_backtrace = simple_atoi(cmd) ? 1 : 0;
            OUT_MSG("{\"status\":\"ok\",\"capture_bt\":%d}", g_config.capture_backtrace);
            return 0;
        }

        if (str_starts_with(cmd, "capture_retval")) {
            cmd = skip_spaces_local(cmd + 14);
            g_config.capture_retval = simple_atoi(cmd) ? 1 : 0;
            OUT_MSG("{\"status\":\"ok\",\"capture_retval\":%d}", g_config.capture_retval);
            return 0;
        }

        if (str_starts_with(cmd, "antidebug")) {
            cmd = skip_spaces_local(cmd + 9);
            g_config.detect_antidebug = simple_atoi(cmd) ? 1 : 0;
            OUT_MSG("{\"status\":\"ok\",\"antidebug\":%d}", g_config.detect_antidebug);
            return 0;
        }

        if (str_starts_with(cmd, "file_log ")) {
            cmd = skip_spaces_local(cmd + 8);
            g_config.file_log_enabled = simple_atoi(cmd) ? 1 : 0;
            if (g_config.file_log_enabled)
                file_logger_enable();
            else
                file_logger_disable();
            OUT_MSG("{\"status\":\"ok\",\"file_log\":%d}", g_config.file_log_enabled);
            return 0;
        }

        if (str_starts_with(cmd, "file_path")) {
            cmd = skip_spaces_local(cmd + 9);
            file_logger_set_path(cmd);
            OUT_MSG("{\"status\":\"ok\",\"file_path\":\"%s\"}", cmd);
            return 0;
        }

        OUT_MSG("{\"error\":\"invalid config command\"}");
        return -1;
    }

    /* read */
    if (str_starts_with(cmd, "read")) {
        int max_count = 1;
        struct svc_event *events;
        int count, i;

        cmd = skip_spaces_local(cmd + 4);
        if (*cmd) {
            max_count = simple_atoi(cmd);
            if (max_count <= 0 || max_count > 100)
                max_count = 1;
        }

        events = (struct svc_event *)kp_malloc(sizeof(struct svc_event) * max_count);
        if (!events) {
            OUT_MSG("{\"error\":\"alloc failed\"}");
            return -1;
        }

        count = event_logger_read_batch(events, max_count);
        if (count <= 0) {
            kp_free(events);
            OUT_MSG("{\"status\":\"ok\",\"events\":[]}");
            return 0;
        }

        {
            int pos = 0;
            pos += snprintf(out_msg + pos, outlen - pos, "{\"status\":\"ok\",\"events\":[");
            for (i = 0; i < count; i++) {
                struct svc_event *e = &events[i];
                pos += snprintf(out_msg + pos, outlen - pos,
                    "{\"ts\":%llu,\"pid\":%d,\"tid\":%d,\"uid\":%u,"
                    "\"comm\":\"%s\",\"nr\":%d,\"name\":\"%s\","
                    "\"ret\":%ld,\"cat\":%d,\"antidebug\":%d,"
                    "\"pc\":\"0x%lx\",\"lr\":\"0x%lx\","
                    "\"module\":\"%s\",\"offset\":\"0x%lx\","
                    "\"detail\":\"%s\"}%s",
                    e->timestamp_ns, e->pid, e->tid, e->uid,
                    e->comm, e->syscall_nr, get_syscall_name(e->syscall_nr),
                    e->retval, e->category, e->is_antidebug,
                    e->caller_pc, e->caller_lr,
                    e->caller_module, e->caller_offset,
                    e->detail,
                    (i == count - 1) ? "" : ",");
                if (pos >= outlen - 128)
                    break;
            }
            snprintf(out_msg + pos, outlen - pos, "]}");
        }

        kp_free(events);
        return 0;
    }

    /* help */
    if (strcmp(cmd, "help") == 0) {
        OUT_MSG("{\"commands\":["
            "\"status\",\"start\",\"stop\",\"clear\",\"stats\","
            "\"hooks slim|all|range N\","
            "\"pid add|del|clear|list\","
            "\"pkg_add <uid> <pkg_name>\","
            "\"config uid|pkg|comm|cat|capture_args|capture_caller|capture_bt|capture_retval|antidebug|file_log|file_path\","
            "\"read [count]\",\"help\""
            "]}");
        return 0;
    }

    OUT_MSG("{\"error\":\"unknown command, use 'help' for list\"}");
    return -1;
}

/* ============================================================================
 * kpm_exit - 模块卸载 (修复版, 按初始化反序清理)
 * ============================================================================ */
static long kpm_exit(void *reserved)
{
    (void)reserved;

    /* 先停止监控 */
    g_config.running = 0;

    /* 按初始化反序清理 */
    if (g_init_stage >= 8) file_logger_close();
    if (g_init_stage >= 7) hook_engine_destroy();
    /* stage 6: syscall_monitor 无需特殊清理 */
    /* stage 5: pkg_resolver 无需特殊清理 */
    if (g_init_stage >= 4) maps_cache_destroy();
    /* stage 3: caller_resolver 无需特殊清理 */
    if (g_init_stage >= 2) event_logger_destroy();
    /* stage 1: symbol_resolver 无需特殊清理 */

    g_init_stage = 0;
    pr_info("[svc-tracer] exited\n");
    return 0;
}

KPM_INIT(kpm_init);
KPM_CTL0(kpm_control0);
KPM_EXIT(kpm_exit);
