/* ============================================================================
 * event_logger.c - 环形事件缓冲区实现 (修复版)
 * ============================================================================
 * 版本: 3.1.0
 * 说明: 此文件逻辑基本正确, 仅做少量防御性增强
 * ============================================================================ */

#include <compiler.h>
#include <kpmodule.h>
#include <linux/printk.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <kpmalloc.h>
#include "svc_tracer.h"

static struct svc_event *g_buffer = NULL;
static int g_head = 0;
static int g_tail = 0;
static int g_count = 0;
static unsigned long long g_total = 0;
static unsigned long long g_dropped = 0;
static spinlock_t g_lock;

int event_logger_init(void)
{
    unsigned long alloc_size = sizeof(struct svc_event) * EVENT_BUFFER_CAPACITY;

    g_buffer = (struct svc_event *)kp_malloc(alloc_size);
    if (!g_buffer) {
        pr_err("[svc-tracer] event_logger: failed to allocate %lu bytes\n",
               alloc_size);
        return -1;
    }

    memset(g_buffer, 0, alloc_size);
    spin_lock_init(&g_lock);
    g_head = 0;
    g_tail = 0;
    g_count = 0;
    g_total = 0;
    g_dropped = 0;

    pr_info("[svc-tracer] event_logger: initialized, capacity=%d, "
            "event_size=%lu, total=%lu bytes\n",
            EVENT_BUFFER_CAPACITY,
            (unsigned long)sizeof(struct svc_event), alloc_size);
    return 0;
}

void event_logger_destroy(void)
{
    unsigned long flags;

    flags = spin_lock_irqsave(&g_lock);
    if (g_buffer) {
        kp_free(g_buffer);
        g_buffer = NULL;
    }
    g_head = 0;
    g_tail = 0;
    g_count = 0;
    spin_unlock_irqrestore(&g_lock, flags);

    pr_info("[svc-tracer] event_logger: destroyed, total=%llu, dropped=%llu\n",
            g_total, g_dropped);
}

int event_logger_write(const struct svc_event *event)
{
    unsigned long flags;

    if (!g_buffer || !event)
        return -1;

    flags = spin_lock_irqsave(&g_lock);

    memcpy(&g_buffer[g_head], event, sizeof(struct svc_event));
    g_head = (g_head + 1) % EVENT_BUFFER_CAPACITY;

    if (g_count < EVENT_BUFFER_CAPACITY) {
        g_count++;
    } else {
        g_tail = (g_tail + 1) % EVENT_BUFFER_CAPACITY;
        g_dropped++;
    }
    g_total++;

    spin_unlock_irqrestore(&g_lock, flags);
    return 0;
}

int event_logger_read(struct svc_event *out)
{
    unsigned long flags;

    if (!g_buffer || !out)
        return -1;

    flags = spin_lock_irqsave(&g_lock);
    if (g_count == 0) {
        spin_unlock_irqrestore(&g_lock, flags);
        return -1;
    }

    memcpy(out, &g_buffer[g_tail], sizeof(struct svc_event));
    g_tail = (g_tail + 1) % EVENT_BUFFER_CAPACITY;
    g_count--;

    spin_unlock_irqrestore(&g_lock, flags);
    return 0;
}

int event_logger_read_batch(struct svc_event *out, int max_count)
{
    unsigned long flags;
    int read_count = 0;

    if (!g_buffer || !out || max_count <= 0)
        return 0;

    flags = spin_lock_irqsave(&g_lock);
    while (read_count < max_count && g_count > 0) {
        memcpy(&out[read_count], &g_buffer[g_tail], sizeof(struct svc_event));
        g_tail = (g_tail + 1) % EVENT_BUFFER_CAPACITY;
        g_count--;
        read_count++;
    }
    spin_unlock_irqrestore(&g_lock, flags);
    return read_count;
}

void event_logger_clear(void)
{
    unsigned long flags;

    flags = spin_lock_irqsave(&g_lock);
    g_head = 0;
    g_tail = 0;
    g_count = 0;
    spin_unlock_irqrestore(&g_lock, flags);

    pr_info("[svc-tracer] event_logger: cleared\n");
}

int event_logger_pending(void)
{
    return g_count;
}

unsigned long long event_logger_dropped(void)
{
    return g_dropped;
}

void event_logger_get_stats(int *pending, unsigned long long *total,
                            unsigned long long *dropped)
{
    unsigned long flags;

    flags = spin_lock_irqsave(&g_lock);
    if (pending)  *pending  = g_count;
    if (total)    *total    = g_total;
    if (dropped)  *dropped  = g_dropped;
    spin_unlock_irqrestore(&g_lock, flags);
}
