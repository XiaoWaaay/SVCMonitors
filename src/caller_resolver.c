/* ============================================================================
 * caller_resolver.c - ARM64 调用者解析 (修复版)
 * ============================================================================
 * 版本: 3.1.0
 * 修复: 增加 task_pt_regs 安全性检查
 *       回溯默认关闭, 减少在 hook 上下文的风险
 * ============================================================================ */

#include <compiler.h>
#include <kpmodule.h>
#include <linux/printk.h>
#include <linux/string.h>
#include <asm/current.h>
#include <asm/ptrace.h>
#include <asm/processor.h>
#include <linux/sched.h>
#include "svc_tracer.h"

/* ARM64 PAC 掩码 */
#define PAC_MASK 0x0000FFFFFFFFFFFFul

static inline unsigned long strip_pac(unsigned long addr)
{
    return addr & PAC_MASK;
}

#define USER_ADDR_MAX  0x0000FFFFFFFFFFFFul

static inline int is_user_addr(unsigned long addr)
{
    return (addr > 0x1000 && addr <= USER_ADDR_MAX);
}

static int safe_read_ulong(unsigned long user_addr, unsigned long *out)
{
    if (!is_user_addr(user_addr) || !out)
        return -1;
    if (!kfunc_copy_from_user)
        return -1;
    if (kfunc_copy_from_user(out, (const void __user *)user_addr,
                              sizeof(unsigned long)) != 0)
        return -1;
    return 0;
}

int caller_resolver_init(void)
{
    pr_info("[svc-tracer] caller_resolver: initialized\n");
    return 0;
}

void caller_resolve(unsigned long *pc_out, unsigned long *lr_out,
                     char *module_out, unsigned long *offset_out)
{
    struct pt_regs *regs;
    unsigned long pc = 0, lr = 0;

    if (pc_out) *pc_out = 0;
    if (lr_out) *lr_out = 0;
    if (module_out) module_out[0] = '\0';
    if (offset_out) *offset_out = 0;

    if (!current)
        return;

    regs = task_pt_regs(current);
    if (!regs)
        return;

    /* 安全性检查: 验证 regs 指针范围合理 */
    if ((unsigned long)regs < 0xFFFF000000000000ul)
        return;  /* regs 应该在内核地址空间 */

    pc = strip_pac(regs->pc);
    lr = strip_pac(regs->regs[30]);

    /* 验证获取的 PC/LR 是用户空间地址 */
    if (is_user_addr(pc)) {
        if (pc_out) *pc_out = pc;
    }
    if (is_user_addr(lr)) {
        if (lr_out) *lr_out = lr;
    }
}

int caller_backtrace(unsigned long *bt_out, int max_depth)
{
    struct pt_regs *regs;
    unsigned long fp, prev_fp;
    unsigned long frame[2];
    int depth = 0;

    if (!bt_out || max_depth <= 0)
        return 0;

    if (max_depth > MAX_BACKTRACE_DEPTH)
        max_depth = MAX_BACKTRACE_DEPTH;

    if (!current)
        return 0;

    regs = task_pt_regs(current);
    if (!regs)
        return 0;

    /* 安全性检查 */
    if ((unsigned long)regs < 0xFFFF000000000000ul)
        return 0;

    /* 第一层: PC */
    {
        unsigned long pc = strip_pac(regs->pc);
        if (is_user_addr(pc))
            bt_out[depth++] = pc;
    }
    if (depth >= max_depth)
        return depth;

    /* 第二层: LR */
    {
        unsigned long lr = strip_pac(regs->regs[30]);
        if (is_user_addr(lr))
            bt_out[depth++] = lr;
    }
    if (depth >= max_depth)
        return depth;

    /* FP chain 回溯 - 需要 copy_from_user */
    if (!kfunc_copy_from_user)
        return depth;

    fp = strip_pac(regs->regs[29]);
    prev_fp = 0;

    while (depth < max_depth && is_user_addr(fp)) {
        unsigned long ret_addr;

        if (fp & 0xF) break;           /* 对齐检查 */
        if (prev_fp != 0 && fp <= prev_fp) break; /* 防循环 */

        if (safe_read_ulong(fp, &frame[0]) != 0) break;
        if (safe_read_ulong(fp + 8, &frame[1]) != 0) break;

        ret_addr = strip_pac(frame[1]);
        if (!is_user_addr(ret_addr)) break;

        bt_out[depth++] = ret_addr;
        prev_fp = fp;
        fp = strip_pac(frame[0]);
    }

    return depth;
}
