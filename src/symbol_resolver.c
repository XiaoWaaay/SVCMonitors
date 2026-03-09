/* ============================================================================
 * symbol_resolver.c - 内核符号运行时解析 (修复版)
 * ============================================================================
 * 版本: 3.1.0
 * 说明: 逻辑基本正确, 增强错误提示
 * ============================================================================ */

#include <compiler.h>
#include <kpmodule.h>
#include <linux/printk.h>
#include <linux/string.h>
#include "svc_tracer.h"

/* 全局函数指针 */
unsigned long long (*kfunc_ktime_get_ns)(void) = NULL;

unsigned long (*kfunc_copy_from_user)(void *to, const void __user *from,
                                      unsigned long n) = NULL;

void *(*kfunc_filp_open)(const char *filename, int flags,
                          unsigned short mode) = NULL;

int (*kfunc_filp_close)(void *filp, void *id) = NULL;

long (*kfunc_kernel_write)(void *filp, const void *buf,
                            unsigned long count, long long *pos) = NULL;

int symbol_resolver_init(void)
{
    int critical_ok = 1;
    int resolved = 0;

    pr_info("[svc-tracer] symbol_resolver: resolving kernel symbols...\n");

    /* ktime_get_ns */
    kfunc_ktime_get_ns = (typeof(kfunc_ktime_get_ns))
        kallsyms_lookup_name("ktime_get_ns");
    if (kfunc_ktime_get_ns) {
        pr_info("[svc-tracer]   ktime_get_ns = %px\n",
                (void *)kfunc_ktime_get_ns);
        resolved++;
    } else {
        pr_warn("[svc-tracer]   ktime_get_ns: NOT FOUND (non-critical)\n");
    }

    /* __arch_copy_from_user / _copy_from_user / raw_copy_from_user */
    kfunc_copy_from_user = (typeof(kfunc_copy_from_user))
        kallsyms_lookup_name("__arch_copy_from_user");
    if (!kfunc_copy_from_user) {
        kfunc_copy_from_user = (typeof(kfunc_copy_from_user))
            kallsyms_lookup_name("_copy_from_user");
    }
    if (!kfunc_copy_from_user) {
        kfunc_copy_from_user = (typeof(kfunc_copy_from_user))
            kallsyms_lookup_name("raw_copy_from_user");
    }
    if (kfunc_copy_from_user) {
        pr_info("[svc-tracer]   copy_from_user = %px\n",
                (void *)kfunc_copy_from_user);
        resolved++;
    } else {
        pr_err("[svc-tracer]   copy_from_user: NOT FOUND (CRITICAL)\n");
        pr_err("[svc-tracer]   tried: __arch_copy_from_user, _copy_from_user, raw_copy_from_user\n");
        critical_ok = 0;
    }

    /* filp_open */
    kfunc_filp_open = (typeof(kfunc_filp_open))
        kallsyms_lookup_name("filp_open");
    if (kfunc_filp_open) {
        pr_info("[svc-tracer]   filp_open = %px\n",
                (void *)kfunc_filp_open);
        resolved++;
    } else {
        pr_warn("[svc-tracer]   filp_open: NOT FOUND (file logging disabled)\n");
    }

    /* filp_close */
    kfunc_filp_close = (typeof(kfunc_filp_close))
        kallsyms_lookup_name("filp_close");
    if (kfunc_filp_close) {
        pr_info("[svc-tracer]   filp_close = %px\n",
                (void *)kfunc_filp_close);
        resolved++;
    } else {
        pr_warn("[svc-tracer]   filp_close: NOT FOUND\n");
    }

    /* kernel_write / __kernel_write */
    kfunc_kernel_write = (typeof(kfunc_kernel_write))
        kallsyms_lookup_name("kernel_write");
    if (!kfunc_kernel_write) {
        kfunc_kernel_write = (typeof(kfunc_kernel_write))
            kallsyms_lookup_name("__kernel_write");
    }
    if (kfunc_kernel_write) {
        pr_info("[svc-tracer]   kernel_write = %px\n",
                (void *)kfunc_kernel_write);
        resolved++;
    } else {
        pr_warn("[svc-tracer]   kernel_write: NOT FOUND (file logging disabled)\n");
    }

    pr_info("[svc-tracer] symbol_resolver: done, resolved=%d/5, critical=%s\n",
            resolved, critical_ok ? "OK" : "MISSING");

    return critical_ok ? 0 : -1;
}
