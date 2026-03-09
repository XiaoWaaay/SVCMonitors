/* ============================================================================
 * pkg_resolver.c - Android 包名解析 (修复版)
 * ============================================================================
 * 版本: 3.1.0
 * 修复: 1. 移除在 hook 上下文中执行文件 I/O 的逻辑
 *       2. 改为纯缓存模式, 由用户空间通过 CTL0 预先注入映射
 *       3. pkg_resolver_add_entry 支持手动添加 UID→包名映射
 * ============================================================================ */

#include <compiler.h>
#include <kpmodule.h>
#include <linux/printk.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <kpmalloc.h>
#include "svc_tracer.h"

/* --------------------------------------------------------------------------
 * 包名缓存 (纯内存, 不做文件 I/O)
 * -------------------------------------------------------------------------- */
static struct pkg_cache_entry g_pkg_cache[MAX_PKG_CACHE];
static spinlock_t g_pkg_lock;

/* --------------------------------------------------------------------------
 * cache_find_by_uid
 * -------------------------------------------------------------------------- */
static struct pkg_cache_entry *cache_find_by_uid(unsigned int uid)
{
    int i;
    unsigned long long now = 0;

    if (kfunc_ktime_get_ns)
        now = kfunc_ktime_get_ns();

    for (i = 0; i < MAX_PKG_CACHE; i++) {
        if (g_pkg_cache[i].valid && g_pkg_cache[i].uid == uid) {
            /* 检查 TTL */
            if (now > 0 && g_pkg_cache[i].timestamp_ns > 0 &&
                (now - g_pkg_cache[i].timestamp_ns) > PKG_CACHE_TTL_NS) {
                g_pkg_cache[i].valid = 0;
                return NULL;
            }
            return &g_pkg_cache[i];
        }
    }
    return NULL;
}

/* --------------------------------------------------------------------------
 * cache_find_by_pkg
 * -------------------------------------------------------------------------- */
static struct pkg_cache_entry *cache_find_by_pkg(const char *pkg)
{
    int i;
    unsigned long long now = 0;

    if (kfunc_ktime_get_ns)
        now = kfunc_ktime_get_ns();

    for (i = 0; i < MAX_PKG_CACHE; i++) {
        if (g_pkg_cache[i].valid &&
            strncmp(g_pkg_cache[i].pkg_name, pkg, MAX_PKG_LEN) == 0) {
            if (now > 0 && g_pkg_cache[i].timestamp_ns > 0 &&
                (now - g_pkg_cache[i].timestamp_ns) > PKG_CACHE_TTL_NS) {
                g_pkg_cache[i].valid = 0;
                return NULL;
            }
            return &g_pkg_cache[i];
        }
    }
    return NULL;
}

/* --------------------------------------------------------------------------
 * cache_insert
 * -------------------------------------------------------------------------- */
static void cache_insert(unsigned int uid, const char *pkg)
{
    int i;
    int oldest_idx = 0;
    unsigned long long oldest_ts = 0xFFFFFFFFFFFFFFFFull;

    /* 先检查是否已存在, 如存在则更新 */
    for (i = 0; i < MAX_PKG_CACHE; i++) {
        if (g_pkg_cache[i].valid && g_pkg_cache[i].uid == uid) {
            strncpy(g_pkg_cache[i].pkg_name, pkg, MAX_PKG_LEN - 1);
            g_pkg_cache[i].pkg_name[MAX_PKG_LEN - 1] = '\0';
            if (kfunc_ktime_get_ns)
                g_pkg_cache[i].timestamp_ns = kfunc_ktime_get_ns();
            return;
        }
    }

    /* 查找空闲或最旧的条目 */
    for (i = 0; i < MAX_PKG_CACHE; i++) {
        if (!g_pkg_cache[i].valid) {
            oldest_idx = i;
            break;
        }
        if (g_pkg_cache[i].timestamp_ns < oldest_ts) {
            oldest_ts = g_pkg_cache[i].timestamp_ns;
            oldest_idx = i;
        }
    }

    g_pkg_cache[oldest_idx].uid = uid;
    strncpy(g_pkg_cache[oldest_idx].pkg_name, pkg, MAX_PKG_LEN - 1);
    g_pkg_cache[oldest_idx].pkg_name[MAX_PKG_LEN - 1] = '\0';
    g_pkg_cache[oldest_idx].valid = 1;

    if (kfunc_ktime_get_ns)
        g_pkg_cache[oldest_idx].timestamp_ns = kfunc_ktime_get_ns();
}

/* ============================================================================
 * 公共接口
 * ============================================================================ */

int pkg_resolver_init(void)
{
    spin_lock_init(&g_pkg_lock);
    memset(g_pkg_cache, 0, sizeof(g_pkg_cache));
    pr_info("[svc-tracer] pkg_resolver: initialized (cache-only mode), "
            "cache=%d, ttl=%llus\n",
            MAX_PKG_CACHE, PKG_CACHE_TTL_NS / 1000000000ULL);
    return 0;
}

/* 修复: 只查缓存, 不做文件 I/O */
int pkg_resolve_uid_to_pkg(unsigned int uid, char *pkg_out, int pkg_len)
{
    unsigned long flags;
    struct pkg_cache_entry *entry;

    if (!pkg_out || pkg_len <= 0)
        return -1;

    pkg_out[0] = '\0';

    flags = spin_lock_irqsave(&g_pkg_lock);

    entry = cache_find_by_uid(uid);
    if (entry) {
        strncpy(pkg_out, entry->pkg_name, pkg_len - 1);
        pkg_out[pkg_len - 1] = '\0';
        spin_unlock_irqrestore(&g_pkg_lock, flags);
        return 0;
    }

    spin_unlock_irqrestore(&g_pkg_lock, flags);
    return -1; /* 缓存未命中, 不执行文件读取 */
}

/* 修复: 只查缓存, 不做文件 I/O */
int pkg_resolve_pkg_to_uid(const char *pkg_name)
{
    unsigned long flags;
    struct pkg_cache_entry *entry;
    int uid;

    if (!pkg_name || strlen(pkg_name) == 0)
        return -1;

    flags = spin_lock_irqsave(&g_pkg_lock);

    entry = cache_find_by_pkg(pkg_name);
    if (entry) {
        uid = entry->uid;
        spin_unlock_irqrestore(&g_pkg_lock, flags);
        return uid;
    }

    spin_unlock_irqrestore(&g_pkg_lock, flags);
    return -1;
}

/* 新增: 手动添加 UID→包名映射 (由 CTL0 接口调用) */
int pkg_resolver_add_entry(unsigned int uid, const char *pkg_name)
{
    unsigned long flags;

    if (!pkg_name || strlen(pkg_name) == 0)
        return -1;

    flags = spin_lock_irqsave(&g_pkg_lock);
    cache_insert(uid, pkg_name);
    spin_unlock_irqrestore(&g_pkg_lock, flags);

    pr_info("[svc-tracer] pkg_resolver: added uid=%u pkg=%s\n", uid, pkg_name);
    return 0;
}
