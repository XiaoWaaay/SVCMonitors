/* ============================================================================
 * file_logger.c - 文件日志实现 (修复版)
 * ============================================================================
 * 版本: 3.1.0
 * 修复: 1. 在 spinlock 外完成格式化和文件 I/O
 *       2. 使用 spinlock 仅保护状态检查和位置更新
 *       3. rotate_if_needed 不在持锁期间执行
 *       4. 格式化缓冲区使用静态分配避免栈溢出
 * ============================================================================ */

#include <compiler.h>
#include <kpmodule.h>
#include <linux/printk.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/kernel.h>
#include "svc_tracer.h"

/* --------------------------------------------------------------------------
 * 内部状态
 * -------------------------------------------------------------------------- */
static void *g_filp = NULL;
static long long g_file_pos = 0;
static long long g_file_size = 0;
static int g_enabled = 0;
static char g_path[MAX_PATH_LEN] = "/sdcard/Download/svc_tracer.log";
static spinlock_t g_flock;

/*
 * 静态格式化缓冲区, 避免在栈上分配大数组
 * 注意: 这意味着 file_logger_write_event 不是重入安全的,
 * 但由于 syscall_monitor_on_syscall 已有递归保护, 这是可接受的
 */
static char g_line_buf[1024];

/* --------------------------------------------------------------------------
 * open_log_file
 * -------------------------------------------------------------------------- */
static int open_log_file(void)
{
    if (!kfunc_filp_open)
        return -1;

    if (g_filp)
        return 0;

    g_filp = kfunc_filp_open(g_path, 0x441, 0644); /* O_WRONLY|O_CREAT|O_APPEND */
    if (!g_filp || (unsigned long)g_filp >= (unsigned long)(-4096)) {
        pr_err("[svc-tracer] file_logger: failed to open %s\n", g_path);
        g_filp = NULL;
        return -1;
    }

    g_file_pos = 0;
    g_file_size = 0;
    return 0;
}

/* --------------------------------------------------------------------------
 * close_log_file
 * -------------------------------------------------------------------------- */
static void close_log_file(void)
{
    if (g_filp && kfunc_filp_close) {
        kfunc_filp_close(g_filp, NULL);
        g_filp = NULL;
    }
    g_file_pos = 0;
    g_file_size = 0;
}

/* ============================================================================
 * 公共接口
 * ============================================================================ */

int file_logger_init(void)
{
    spin_lock_init(&g_flock);
    g_filp = NULL;
    g_file_pos = 0;
    g_file_size = 0;
    g_enabled = 0;

    pr_info("[svc-tracer] file_logger: initialized, path=%s\n", g_path);
    return 0;
}

void file_logger_close(void)
{
    unsigned long flags;

    flags = spin_lock_irqsave(&g_flock);
    g_enabled = 0;
    spin_unlock_irqrestore(&g_flock, flags);

    /* 修复: 在 spinlock 外执行文件 I/O */
    close_log_file();

    pr_info("[svc-tracer] file_logger: closed\n");
}

void file_logger_enable(void)
{
    unsigned long flags;
    int need_open = 0;

    flags = spin_lock_irqsave(&g_flock);
    if (!g_filp)
        need_open = 1;
    spin_unlock_irqrestore(&g_flock, flags);

    /* 修复: 在 spinlock 外执行文件打开 */
    if (need_open) {
        if (open_log_file() != 0) {
            pr_err("[svc-tracer] file_logger: enable failed\n");
            return;
        }
    }

    flags = spin_lock_irqsave(&g_flock);
    g_enabled = 1;
    spin_unlock_irqrestore(&g_flock, flags);

    pr_info("[svc-tracer] file_logger: enabled\n");
}

void file_logger_disable(void)
{
    unsigned long flags;

    flags = spin_lock_irqsave(&g_flock);
    g_enabled = 0;
    spin_unlock_irqrestore(&g_flock, flags);

    pr_info("[svc-tracer] file_logger: disabled\n");
}

/* ============================================================================
 * file_logger_write_event - 写入事件 (修复版)
 * ============================================================================
 * 关键修复: 不在 spinlock 持锁期间执行文件 I/O
 *
 * 策略:
 * 1. 先在锁外格式化字符串
 * 2. 用 spinlock 检查状态并获取文件指针和位置
 * 3. 在锁外执行实际的 kernel_write
 * 4. 用 spinlock 更新位置
 *
 * 注意: 由于 syscall_monitor 有递归保护 (g_in_hook),
 * kernel_write 不会触发我们的 hook 回调, 所以是安全的。
 * ============================================================================ */
int file_logger_write_event(const struct svc_event *event)
{
    unsigned long flags;
    int len;
    long written;
    void *filp_copy;
    long long pos_copy;
    int need_rotate = 0;

    if (!event || !kfunc_kernel_write)
        return -1;

    /* 1. 检查启用状态 (快速路径) */
    if (!g_enabled || !g_filp)
        return -1;

    /* 2. 在锁外格式化 (使用静态缓冲区) */
    len = snprintf(g_line_buf, sizeof(g_line_buf),
        "{\"ts\":%llu,\"pid\":%d,\"tid\":%d,\"uid\":%u,"
        "\"comm\":\"%s\",\"nr\":%d,\"name\":\"%s\","
        "\"ret\":%ld,\"cat\":%d,\"antidebug\":%d,"
        "\"pc\":\"0x%lx\",\"lr\":\"0x%lx\","
        "\"module\":\"%s\",\"offset\":\"0x%lx\","
        "\"detail\":\"%s\"}\n",
        event->timestamp_ns, event->pid, event->tid, event->uid,
        event->comm, event->syscall_nr,
        get_syscall_name(event->syscall_nr),
        event->retval, event->category, event->is_antidebug,
        event->caller_pc, event->caller_lr,
        event->caller_module, event->caller_offset,
        event->detail);

    if (len <= 0 || len >= (int)sizeof(g_line_buf))
        return -1;

    /* 3. 获取文件状态 */
    flags = spin_lock_irqsave(&g_flock);
    if (!g_filp || !g_enabled) {
        spin_unlock_irqrestore(&g_flock, flags);
        return -1;
    }

    /* 检查是否需要轮转 */
    if (g_file_size >= FILE_LOG_MAX_SIZE) {
        need_rotate = 1;
    }

    filp_copy = g_filp;
    pos_copy = g_file_pos;
    spin_unlock_irqrestore(&g_flock, flags);

    /* 4. 文件轮转 (在锁外执行) */
    if (need_rotate) {
        close_log_file();
        if (kfunc_filp_open) {
            g_filp = kfunc_filp_open(g_path, 0x241, 0644); /* O_WRONLY|O_CREAT|O_TRUNC */
            if (!g_filp || (unsigned long)g_filp >= (unsigned long)(-4096)) {
                g_filp = NULL;
                pr_warn("[svc-tracer] file_logger: rotate failed\n");
                return -1;
            }
        }

        flags = spin_lock_irqsave(&g_flock);
        g_file_pos = 0;
        g_file_size = 0;
        filp_copy = g_filp;
        pos_copy = g_file_pos;
        spin_unlock_irqrestore(&g_flock, flags);

        pr_info("[svc-tracer] file_logger: rotated\n");
    }

    if (!filp_copy)
        return -1;

    /* 5. 在锁外执行写入 (kernel_write 可能睡眠) */
    written = kfunc_kernel_write(filp_copy, g_line_buf, len, &pos_copy);

    /* 6. 更新位置 */
    if (written > 0) {
        flags = spin_lock_irqsave(&g_flock);
        g_file_pos = pos_copy;
        g_file_size += written;
        spin_unlock_irqrestore(&g_flock, flags);
    }

    return (written > 0) ? 0 : -1;
}

int file_logger_set_path(const char *path)
{
    unsigned long flags;

    if (!path || strlen(path) == 0 || strlen(path) >= MAX_PATH_LEN)
        return -1;

    /* 先关闭旧文件 (在锁外) */
    close_log_file();

    flags = spin_lock_irqsave(&g_flock);
    memset(g_path, 0, MAX_PATH_LEN);
    strncpy(g_path, path, MAX_PATH_LEN - 1);
    spin_unlock_irqrestore(&g_flock, flags);

    /* 如果已启用, 重新打开 (在锁外) */
    if (g_enabled) {
        open_log_file();
    }

    pr_info("[svc-tracer] file_logger: path set to %s\n", g_path);
    return 0;
}

int file_logger_truncate(void)
{
    /* 在锁外执行文件操作 */
    close_log_file();

    if (kfunc_filp_open) {
        g_filp = kfunc_filp_open(g_path, 0x241, 0644);
        if (!g_filp || (unsigned long)g_filp >= (unsigned long)(-4096)) {
            g_filp = NULL;
            return -1;
        }
    }

    {
        unsigned long flags;
        flags = spin_lock_irqsave(&g_flock);
        g_file_pos = 0;
        g_file_size = 0;
        spin_unlock_irqrestore(&g_flock, flags);
    }

    pr_info("[svc-tracer] file_logger: truncated\n");
    return 0;
}

void file_logger_flush(void)
{
    /* 预留 */
}
