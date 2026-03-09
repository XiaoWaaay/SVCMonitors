/* ============================================================================
 * hook_engine.c - Hook 引擎实现 (修复版)
 * ============================================================================
 * 版本: 3.1.0
 * 修复: hook_install_all 限制最大 hook 数量
 *       添加已 hook 去重检查
 * ============================================================================ */

#include <compiler.h>
#include <kpmodule.h>
#include <linux/printk.h>
#include <linux/string.h>
#include <syscall.h>
#include "svc_tracer.h"

/* --------------------------------------------------------------------------
 * 系统调用号定义 (ARM64)
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

/* --------------------------------------------------------------------------
 * g_hooks - 预定义 hook 表
 * -------------------------------------------------------------------------- */
static struct syscall_hook_def g_hooks[] = {
    /* 文件操作 */
    { __NR_openat,      4, "openat",      SC_CAT_FILE },
    { __NR_close,       1, "close",       SC_CAT_FILE },
    { __NR_read,        3, "read",        SC_CAT_FILE },
    { __NR_write,       3, "write",       SC_CAT_FILE },
    { __NR_readv,       3, "readv",       SC_CAT_FILE },
    { __NR_writev,      3, "writev",      SC_CAT_FILE },
    { __NR_lseek,       3, "lseek",       SC_CAT_FILE },
    { __NR_fstat,       2, "fstat",       SC_CAT_FILE },
    { __NR_ioctl,       3, "ioctl",       SC_CAT_FILE },
    { __NR_dup,         1, "dup",         SC_CAT_FILE },
    { __NR_dup3,        3, "dup3",        SC_CAT_FILE },
    { __NR_pipe2,       2, "pipe2",       SC_CAT_FILE },
    { __NR_readlinkat,  4, "readlinkat",  SC_CAT_FILE },
    { __NR_faccessat,   3, "faccessat",   SC_CAT_FILE },
    { __NR_unlinkat,    3, "unlinkat",    SC_CAT_FILE },
    { __NR_mkdirat,     3, "mkdirat",     SC_CAT_FILE },
    { __NR_renameat2,   5, "renameat2",   SC_CAT_FILE },

    /* 进程操作 */
    { __NR_execve,      3, "execve",      SC_CAT_PROC },
    { __NR_clone,       5, "clone",       SC_CAT_PROC },
    { __NR_wait4,       4, "wait4",       SC_CAT_PROC },
    { __NR_exit_group,  1, "exit_group",  SC_CAT_PROC },
    { __NR_getpid,      0, "getpid",      SC_CAT_PROC },
    { __NR_getuid,      0, "getuid",      SC_CAT_PROC },
    { __NR_prctl,       5, "prctl",       SC_CAT_PROC },
    { __NR_setpgid,     2, "setpgid",     SC_CAT_PROC },

    /* 内存操作 */
    { __NR_mmap,        6, "mmap",        SC_CAT_MEM },
    { __NR_munmap,      2, "munmap",      SC_CAT_MEM },
    { __NR_mprotect,    3, "mprotect",    SC_CAT_MEM },
    { __NR_madvise,     3, "madvise",     SC_CAT_MEM },
    { __NR_mremap,      5, "mremap",      SC_CAT_MEM },

    /* 网络操作 */
    { __NR_socket,      3, "socket",      SC_CAT_NET },
    { __NR_connect,     3, "connect",     SC_CAT_NET },
    { __NR_bind,        3, "bind",        SC_CAT_NET },
    { __NR_listen,      2, "listen",      SC_CAT_NET },
    { __NR_accept4,     4, "accept4",     SC_CAT_NET },
    { __NR_sendto,      6, "sendto",      SC_CAT_NET },
    { __NR_recvfrom,    6, "recvfrom",    SC_CAT_NET },
    { __NR_setsockopt,  5, "setsockopt",  SC_CAT_NET },
    { __NR_getsockname, 3, "getsockname", SC_CAT_NET },

    /* 信号/调试 */
    { __NR_ptrace,      4, "ptrace",      SC_CAT_ANTIDEBUG },
    { __NR_rt_sigaction, 4, "rt_sigaction", SC_CAT_SIGNAL },
    { __NR_kill,        2, "kill",        SC_CAT_SIGNAL },
    { __NR_tgkill,      3, "tgkill",      SC_CAT_SIGNAL },

    /* IPC */
    { __NR_epoll_create1, 1, "epoll_create1", SC_CAT_IPC },
    { __NR_epoll_ctl,     4, "epoll_ctl",     SC_CAT_IPC },
    { __NR_eventfd2,      2, "eventfd2",      SC_CAT_IPC },
};

#define NUM_SLIM_HOOKS (sizeof(g_hooks) / sizeof(g_hooks[0]))

/* --------------------------------------------------------------------------
 * hook 用户数据
 * -------------------------------------------------------------------------- */
struct hook_udata {
    int nr;
    int narg;
};

#define MAX_HOOK_SLOTS 256  /* 修复: 从 512 减到 256 */
static struct hook_udata g_udata[MAX_HOOK_SLOTS];
static int g_hooked_nrs[MAX_HOOK_SLOTS]; /* 已 hook 的 syscall 号, 用于去重 */
static int g_hook_count = 0;
static int g_initialized = 0;

/* --------------------------------------------------------------------------
 * after 回调函数
 * -------------------------------------------------------------------------- */

static void after0(hook_fargs0_t *args, void *udata)
{
    struct hook_udata *ud = (struct hook_udata *)udata;
    unsigned long sysargs[6] = {0};
    syscall_monitor_on_syscall(ud->nr, sysargs, args->ret, ud->narg);
}

static void after1(hook_fargs1_t *args, void *udata)
{
    struct hook_udata *ud = (struct hook_udata *)udata;
    unsigned long sysargs[6] = {args->arg0, 0, 0, 0, 0, 0};
    syscall_monitor_on_syscall(ud->nr, sysargs, args->ret, ud->narg);
}

static void after2(hook_fargs2_t *args, void *udata)
{
    struct hook_udata *ud = (struct hook_udata *)udata;
    unsigned long sysargs[6] = {args->arg0, args->arg1, 0, 0, 0, 0};
    syscall_monitor_on_syscall(ud->nr, sysargs, args->ret, ud->narg);
}

static void after3(hook_fargs3_t *args, void *udata)
{
    struct hook_udata *ud = (struct hook_udata *)udata;
    unsigned long sysargs[6] = {args->arg0, args->arg1, args->arg2, 0, 0, 0};
    syscall_monitor_on_syscall(ud->nr, sysargs, args->ret, ud->narg);
}

static void after4(hook_fargs4_t *args, void *udata)
{
    struct hook_udata *ud = (struct hook_udata *)udata;
    unsigned long sysargs[6] = {
        args->arg0, args->arg1, args->arg2, args->arg3, 0, 0
    };
    syscall_monitor_on_syscall(ud->nr, sysargs, args->ret, ud->narg);
}

static void after5(hook_fargs5_t *args, void *udata)
{
    struct hook_udata *ud = (struct hook_udata *)udata;
    unsigned long sysargs[6] = {
        args->arg0, args->arg1, args->arg2,
        args->arg3, args->arg4, 0
    };
    syscall_monitor_on_syscall(ud->nr, sysargs, args->ret, ud->narg);
}

static void after6(hook_fargs6_t *args, void *udata)
{
    struct hook_udata *ud = (struct hook_udata *)udata;
    unsigned long sysargs[6] = {
        args->arg0, args->arg1, args->arg2,
        args->arg3, args->arg4, args->arg5
    };
    syscall_monitor_on_syscall(ud->nr, sysargs, args->ret, ud->narg);
}

/* --------------------------------------------------------------------------
 * is_already_hooked - 去重检查
 * -------------------------------------------------------------------------- */
static int is_already_hooked(int nr)
{
    int i;
    for (i = 0; i < g_hook_count; i++) {
        if (g_hooked_nrs[i] == nr)
            return 1;
    }
    return 0;
}

/* --------------------------------------------------------------------------
 * install_single_hook
 * -------------------------------------------------------------------------- */
static int install_single_hook(int nr, int narg)
{
    int ret = -1;
    struct hook_udata *ud;

    if (g_hook_count >= MAX_HOOK_SLOTS) {
        pr_warn("[svc-tracer] hook slots full (%d)\n", MAX_HOOK_SLOTS);
        return -1;
    }

    /* 去重: 不重复 hook 同一个 syscall */
    if (is_already_hooked(nr))
        return 0;

    /* 参数数量合法性检查 */
    if (narg < 0 || narg > 6) {
        pr_warn("[svc-tracer] invalid narg %d for nr %d\n", narg, nr);
        return -1;
    }

    ud = &g_udata[g_hook_count];
    ud->nr = nr;
    ud->narg = narg;

    switch (narg) {
    case 0: ret = fp_hook_syscalln(nr, 0, NULL, (void *)after0, (void *)ud); break;
    case 1: ret = fp_hook_syscalln(nr, 1, NULL, (void *)after1, (void *)ud); break;
    case 2: ret = fp_hook_syscalln(nr, 2, NULL, (void *)after2, (void *)ud); break;
    case 3: ret = fp_hook_syscalln(nr, 3, NULL, (void *)after3, (void *)ud); break;
    case 4: ret = fp_hook_syscalln(nr, 4, NULL, (void *)after4, (void *)ud); break;
    case 5: ret = fp_hook_syscalln(nr, 5, NULL, (void *)after5, (void *)ud); break;
    case 6: ret = fp_hook_syscalln(nr, 6, NULL, (void *)after6, (void *)ud); break;
    }

    if (ret == 0) {
        g_hooked_nrs[g_hook_count] = nr;
        g_hook_count++;
        g_stats.hook_count = g_hook_count;
    } else {
        /* hook 失败不是致命错误, 某些 syscall 号可能不存在 */
        pr_info("[svc-tracer] skip hook syscall %d: ret=%d\n", nr, ret);
    }

    return ret;
}

/* ============================================================================
 * 公共接口
 * ============================================================================ */

int hook_engine_init(void)
{
    g_hook_count = 0;
    g_initialized = 1;
    memset(g_udata, 0, sizeof(g_udata));
    memset(g_hooked_nrs, 0, sizeof(g_hooked_nrs));
    pr_info("[svc-tracer] hook engine initialized, %lu slim hooks defined\n",
            (unsigned long)NUM_SLIM_HOOKS);
    return 0;
}

void hook_engine_destroy(void)
{
    /*
     * KernelPatch 在模块卸载时自动移除所有 hook
     */
    g_hook_count = 0;
    g_stats.hook_count = 0;
    g_initialized = 0;
    pr_info("[svc-tracer] hook engine destroyed\n");
}

int hook_install_slim(void)
{
    int i, count = 0;

    if (!g_initialized) return -1;

    for (i = 0; i < (int)NUM_SLIM_HOOKS; i++) {
        if (install_single_hook(g_hooks[i].nr, g_hooks[i].narg) == 0)
            count++;
    }

    pr_info("[svc-tracer] slim hooks installed: %d/%lu\n",
            count, (unsigned long)NUM_SLIM_HOOKS);
    return count;
}

/*
 * 修复: hook_install_all 只 hook 有意义的 syscall 号范围
 * ARM64 Linux 最大 syscall 号约 ~450, 限制到 300 以内
 * 且只使用 slim hook 表中已知的参数数量
 */
int hook_install_all(void)
{
    int nr, count = 0;
    int max_nr = 300; /* 修复: 限制范围 */

    if (!g_initialized) return -1;

    for (nr = 0; nr < max_nr; nr++) {
        int narg = -1;
        int i;

        /* 查找精确参数数量 */
        for (i = 0; i < (int)NUM_SLIM_HOOKS; i++) {
            if (g_hooks[i].nr == nr) {
                narg = g_hooks[i].narg;
                break;
            }
        }

        /* 只 hook 有已知参数数量的 syscall, 或使用 6 参数默认值 */
        if (narg < 0)
            narg = 6;

        if (install_single_hook(nr, narg) == 0)
            count++;
    }

    pr_info("[svc-tracer] all hooks installed: %d\n", count);
    return count;
}

int hook_install_range(int max_nr)
{
    int nr, count = 0;

    if (!g_initialized) return -1;
    if (max_nr <= 0 || max_nr > 300) max_nr = 300; /* 修复: 限制范围 */

    for (nr = 0; nr < max_nr; nr++) {
        int narg = 6;
        int i;

        for (i = 0; i < (int)NUM_SLIM_HOOKS; i++) {
            if (g_hooks[i].nr == nr) {
                narg = g_hooks[i].narg;
                break;
            }
        }

        if (install_single_hook(nr, narg) == 0)
            count++;
    }

    pr_info("[svc-tracer] range hooks (0-%d) installed: %d\n", max_nr, count);
    return count;
}

int hook_get_count(void)
{
    return g_hook_count;
}
