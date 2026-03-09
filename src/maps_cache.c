/* ============================================================================
 * maps_cache.c - 进程地址映射缓存 (修复版)
 * ============================================================================
 * 版本: 3.1.0
 * 修复: 1. 移除 maps_cache_refresh 中的文件 I/O (不安全)
 *       2. 改为通过 CTL0 接口由用户空间传入 maps 数据
 *       3. maps_cache_update_from_string 解析用户空间传入的文本
 * ============================================================================ */

#include <compiler.h>
#include <kpmodule.h>
#include <linux/printk.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/kernel.h>
#include <kpmalloc.h>
#include "svc_tracer.h"

/* --------------------------------------------------------------------------
 * 全局缓存
 * -------------------------------------------------------------------------- */
static struct maps_proc_cache g_cache[MAX_MAPS_CACHE_PROCS];
static unsigned long long g_access_counter = 0;
static spinlock_t g_maps_lock;

/* --------------------------------------------------------------------------
 * 辅助函数
 * -------------------------------------------------------------------------- */
static unsigned long parse_hex(const char *s, const char **endp)
{
    unsigned long val = 0;
    while (*s) {
        char c = *s;
        if (c >= '0' && c <= '9')
            val = (val << 4) | (c - '0');
        else if (c >= 'a' && c <= 'f')
            val = (val << 4) | (c - 'a' + 10);
        else if (c >= 'A' && c <= 'F')
            val = (val << 4) | (c - 'A' + 10);
        else
            break;
        s++;
    }
    if (endp) *endp = s;
    return val;
}

static const char *skip_to_space(const char *s)
{
    while (*s && *s != ' ' && *s != '\t')
        s++;
    return s;
}

static const char *skip_whitespace(const char *s)
{
    while (*s == ' ' || *s == '\t')
        s++;
    return s;
}

static const char *skip_to_newline(const char *s)
{
    while (*s && *s != '\n')
        s++;
    return s;
}

/* --------------------------------------------------------------------------
 * parse_maps_line - 解析 /proc/pid/maps 的一行
 * -------------------------------------------------------------------------- */
static int parse_maps_line(const char *line, struct maps_entry *entry)
{
    const char *p = line;
    const char *endp;

    /* start */
    entry->start = parse_hex(p, &endp);
    if (*endp != '-') return -1;
    p = endp + 1;

    /* end */
    entry->end = parse_hex(p, &endp);
    p = endp;

    /* perms */
    p = skip_whitespace(p);
    p = skip_to_space(p);

    /* offset */
    p = skip_whitespace(p);
    entry->offset = parse_hex(p, &endp);
    p = endp;

    /* dev */
    p = skip_whitespace(p);
    p = skip_to_space(p);

    /* inode */
    p = skip_whitespace(p);
    p = skip_to_space(p);

    /* pathname */
    p = skip_whitespace(p);
    entry->name[0] = '\0';

    if (*p == '/' || *p == '[') {
        int i = 0;
        while (*p && *p != '\n' && i < MAX_MODULE_NAME_LEN - 1) {
            entry->name[i++] = *p++;
        }
        entry->name[i] = '\0';
    }

    return (entry->start < entry->end) ? 0 : -1;
}

/* --------------------------------------------------------------------------
 * find_cache_slot
 * -------------------------------------------------------------------------- */
static struct maps_proc_cache *find_cache_slot(int tgid)
{
    int i;
    for (i = 0; i < MAX_MAPS_CACHE_PROCS; i++) {
        if (g_cache[i].tgid == tgid)
            return &g_cache[i];
    }
    return NULL;
}

/* --------------------------------------------------------------------------
 * maps_cache_evict_lru
 * -------------------------------------------------------------------------- */
static struct maps_proc_cache *maps_cache_evict_lru(void)
{
    int i;
    int lru_idx = 0;
    unsigned long long min_access = g_cache[0].access_counter;

    for (i = 0; i < MAX_MAPS_CACHE_PROCS; i++) {
        if (g_cache[i].tgid == 0)
            return &g_cache[i];
    }

    for (i = 1; i < MAX_MAPS_CACHE_PROCS; i++) {
        if (g_cache[i].access_counter < min_access) {
            min_access = g_cache[i].access_counter;
            lru_idx = i;
        }
    }

    g_cache[lru_idx].tgid = 0;
    g_cache[lru_idx].count = 0;
    return &g_cache[lru_idx];
}

/* ============================================================================
 * 公共接口
 * ============================================================================ */

int maps_cache_init(void)
{
    spin_lock_init(&g_maps_lock);
    memset(g_cache, 0, sizeof(g_cache));
    g_access_counter = 0;
    pr_info("[svc-tracer] maps_cache: initialized, %d proc slots, "
            "%d entries per proc\n",
            MAX_MAPS_CACHE_PROCS, MAX_MAPS_ENTRIES);
    return 0;
}

void maps_cache_destroy(void)
{
    unsigned long flags;
    flags = spin_lock_irqsave(&g_maps_lock);
    memset(g_cache, 0, sizeof(g_cache));
    spin_unlock_irqrestore(&g_maps_lock, flags);
    pr_info("[svc-tracer] maps_cache: destroyed\n");
}

int maps_cache_lookup(int tgid, unsigned long addr,
                       char *name_out, unsigned long *offset_out)
{
    unsigned long flags;
    struct maps_proc_cache *pc;
    int i;

    if (name_out) name_out[0] = '\0';
    if (offset_out) *offset_out = 0;

    flags = spin_lock_irqsave(&g_maps_lock);

    pc = find_cache_slot(tgid);
    if (!pc || pc->count == 0) {
        spin_unlock_irqrestore(&g_maps_lock, flags);
        return -1;
    }

    pc->access_counter = ++g_access_counter;

    for (i = 0; i < pc->count; i++) {
        struct maps_entry *e = &pc->entries[i];
        if (addr >= e->start && addr < e->end) {
            if (name_out) {
                strncpy(name_out, e->name, MAX_MODULE_NAME_LEN - 1);
                name_out[MAX_MODULE_NAME_LEN - 1] = '\0';
            }
            if (offset_out)
                *offset_out = (addr - e->start) + e->offset;
            spin_unlock_irqrestore(&g_maps_lock, flags);
            return 0;
        }
    }

    spin_unlock_irqrestore(&g_maps_lock, flags);
    return -1;
}

/* ============================================================================
 * maps_cache_update_from_string - 从用户空间传入的文本更新缓存
 * ============================================================================
 * 修复: 替代原来不安全的 maps_cache_refresh (在内核中读取 /proc 文件)
 * 用户空间通过 CTL0 接口将 /proc/pid/maps 内容传入
 * ============================================================================ */
int maps_cache_update_from_string(int tgid, const char *maps_data, int data_len)
{
    unsigned long flags;
    struct maps_proc_cache *pc;
    const char *p;
    const char *end;
    int count = 0;

    if (!maps_data || data_len <= 0 || tgid <= 0)
        return -1;

    end = maps_data + data_len;

    flags = spin_lock_irqsave(&g_maps_lock);

    pc = find_cache_slot(tgid);
    if (!pc)
        pc = maps_cache_evict_lru();

    pc->tgid = tgid;
    pc->count = 0;
    pc->access_counter = ++g_access_counter;

    /* 逐行解析 */
    p = maps_data;
    while (p < end && count < MAX_MAPS_ENTRIES) {
        struct maps_entry entry;

        if (*p == '\n' || *p == '\0') {
            p++;
            continue;
        }

        if (parse_maps_line(p, &entry) == 0) {
            /* 只缓存有名字的映射 (库文件等) */
            if (entry.name[0] != '\0') {
                memcpy(&pc->entries[count], &entry, sizeof(struct maps_entry));
                count++;
            }
        }

        /* 跳到下一行 */
        p = skip_to_newline(p);
        if (*p == '\n') p++;
    }

    pc->count = count;

    spin_unlock_irqrestore(&g_maps_lock, flags);

    pr_info("[svc-tracer] maps_cache: updated pid %d, %d entries\n", tgid, count);
    return count;
}

void maps_cache_invalidate(int tgid)
{
    unsigned long flags;
    int i;

    flags = spin_lock_irqsave(&g_maps_lock);
    for (i = 0; i < MAX_MAPS_CACHE_PROCS; i++) {
        if (g_cache[i].tgid == tgid) {
            g_cache[i].tgid = 0;
            g_cache[i].count = 0;
            break;
        }
    }
    spin_unlock_irqrestore(&g_maps_lock, flags);
}

void maps_cache_clear(void)
{
    unsigned long flags;

    flags = spin_lock_irqsave(&g_maps_lock);
    memset(g_cache, 0, sizeof(g_cache));
    spin_unlock_irqrestore(&g_maps_lock, flags);

    pr_info("[svc-tracer] maps_cache: all caches cleared\n");
}
