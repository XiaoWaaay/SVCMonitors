/* ============================================================================
 * svc_tracer.h - SVCModule 主头文件 (修复版)
 * ============================================================================
 * 版本: 3.1.0
 * 修复: 添加 task_struct/cred 偏移量结构体定义
 *       添加 get_task_comm 内联函数
 *       减小 svc_event 结构体以避免栈溢出
 * ============================================================================ */

#ifndef _SVC_TRACER_H_
#define _SVC_TRACER_H_

#include <compiler.h>
#include <kpmodule.h>
#include <linux/printk.h>
#include <linux/string.h>

/* --------------------------------------------------------------------------
 * 版本信息
 * -------------------------------------------------------------------------- */
#define SVC_TRACER_VERSION "3.1.0"

/* --------------------------------------------------------------------------
 * 容量与限制常量
 * -------------------------------------------------------------------------- */
#define EVENT_BUFFER_CAPACITY   2048    /* 环形事件缓冲区容量 (减半节省内存) */
#define MAX_MONITORED_PIDS      32      /* 最大监控 PID 数量 */
#define MAX_BACKTRACE_DEPTH     8       /* 最大回溯深度 (从16减到8) */
#define MAX_FILTERED_SYSCALLS   128     /* 最大过滤 syscall 数量 */
#define MAX_MAPS_CACHE_PROCS    8       /* maps 缓存最大进程数 */
#define MAX_MAPS_ENTRIES        256     /* 单进程 maps 最大条目数 (减半) */
#define MAX_PKG_CACHE           64      /* 包名缓存最大条目数 */
#define PKG_CACHE_TTL_NS        (60ULL * 1000000000ULL) /* 包名缓存 TTL: 60秒 */

/* --------------------------------------------------------------------------
 * 字符串缓冲区大小
 * -------------------------------------------------------------------------- */
#define MAX_DETAIL_LEN          256     /* 事件详情字符串最大长度 (从512减到256) */
#define MAX_COMM_LEN            16      /* 进程 comm 最大长度 */
#define MAX_PKG_LEN             128     /* 包名最大长度 */
#define MAX_PATH_LEN            256     /* 路径最大长度 */
#define MAX_MODULE_NAME_LEN     64      /* 模块名最大长度 (从128减到64) */

/* --------------------------------------------------------------------------
 * Syscall 类别掩码
 * -------------------------------------------------------------------------- */
#define SC_CAT_FILE     0x01
#define SC_CAT_NET      0x02
#define SC_CAT_MEM      0x04
#define SC_CAT_PROC     0x08
#define SC_CAT_IPC      0x10
#define SC_CAT_SIGNAL   0x20
#define SC_CAT_ANTIDEBUG 0x40
#define SC_CAT_ALL      0xFF

/* --------------------------------------------------------------------------
 * 文件日志常量
 * -------------------------------------------------------------------------- */
#define FILE_LOG_MAX_SIZE       (10 * 1024 * 1024)  /* 10MB */

/* --------------------------------------------------------------------------
 * svc_event - 系统调用事件结构体 (优化大小: ~450字节)
 * -------------------------------------------------------------------------- */
struct svc_event {
    unsigned long long timestamp_ns;
    int pid;
    int tid;
    unsigned int uid;
    char comm[MAX_COMM_LEN];
    int syscall_nr;
    unsigned long args[6];
    long retval;
    unsigned char category;
    unsigned char is_antidebug;
    unsigned long caller_pc;
    unsigned long caller_lr;
    char caller_module[MAX_MODULE_NAME_LEN];
    unsigned long caller_offset;
    unsigned long backtrace[MAX_BACKTRACE_DEPTH];
    int backtrace_depth;
    char detail[MAX_DETAIL_LEN];
};

/* --------------------------------------------------------------------------
 * tracer_config - 全局配置结构体
 * -------------------------------------------------------------------------- */
struct tracer_config {
    int running;
    int monitored_pids[MAX_MONITORED_PIDS];
    int pid_count;
    int filter_uid;
    char filter_pkg[MAX_PKG_LEN];
    char filter_comm[MAX_COMM_LEN];
    unsigned char category_mask;
    int filtered_syscalls[MAX_FILTERED_SYSCALLS];
    int filtered_syscall_count;
    int capture_args;
    int capture_caller;
    int capture_backtrace;
    int capture_retval;
    int detect_antidebug;
    int json_output;
    int file_log_enabled;
    char file_log_path[MAX_PATH_LEN];
};

/* --------------------------------------------------------------------------
 * tracer_stats - 运行时统计结构体
 * -------------------------------------------------------------------------- */
struct tracer_stats {
    unsigned long long total_events;
    unsigned long long dropped_events;
    unsigned long long antidebug_events;
    unsigned long long filtered_events;
    unsigned long long file_log_writes;
    unsigned long long file_log_errors;
    unsigned long long hook_count;
};

/* --------------------------------------------------------------------------
 * syscall_hook_def - hook 定义结构体
 * -------------------------------------------------------------------------- */
struct syscall_hook_def {
    int nr;
    int narg;
    const char *name;
    unsigned char category;
};

/* --------------------------------------------------------------------------
 * maps_entry / maps_proc_cache
 * -------------------------------------------------------------------------- */
struct maps_entry {
    unsigned long start;
    unsigned long end;
    unsigned long offset;
    char name[MAX_MODULE_NAME_LEN];
};

struct maps_proc_cache {
    int tgid;
    int count;
    unsigned long long access_counter;
    struct maps_entry entries[MAX_MAPS_ENTRIES];
};

/* --------------------------------------------------------------------------
 * pkg_cache_entry
 * -------------------------------------------------------------------------- */
struct pkg_cache_entry {
    unsigned int uid;
    char pkg_name[MAX_PKG_LEN];
    unsigned long long timestamp_ns;
    int valid;
};

/* --------------------------------------------------------------------------
 * 内核符号函数指针 (由 symbol_resolver 初始化)
 * -------------------------------------------------------------------------- */
extern unsigned long long (*kfunc_ktime_get_ns)(void);
extern unsigned long (*kfunc_copy_from_user)(void *to, const void __user *from,
                                              unsigned long n);
extern void *(*kfunc_filp_open)(const char *filename, int flags, unsigned short mode);
extern int (*kfunc_filp_close)(void *filp, void *id);
extern long (*kfunc_kernel_write)(void *filp, const void *buf,
                                   unsigned long count, long long *pos);

/* --------------------------------------------------------------------------
 * 模块接口声明
 * -------------------------------------------------------------------------- */

/* symbol_resolver */
int symbol_resolver_init(void);

/* event_logger */
int event_logger_init(void);
void event_logger_destroy(void);
int event_logger_write(const struct svc_event *event);
int event_logger_read(struct svc_event *out);
int event_logger_read_batch(struct svc_event *out, int max_count);
void event_logger_clear(void);
int event_logger_pending(void);
unsigned long long event_logger_dropped(void);
void event_logger_get_stats(int *pending, unsigned long long *total,
                            unsigned long long *dropped);

/* caller_resolver */
int caller_resolver_init(void);
void caller_resolve(unsigned long *pc_out, unsigned long *lr_out,
                     char *module_out, unsigned long *offset_out);
int caller_backtrace(unsigned long *bt_out, int max_depth);

/* maps_cache */
int maps_cache_init(void);
void maps_cache_destroy(void);
int maps_cache_lookup(int tgid, unsigned long addr,
                       char *name_out, unsigned long *offset_out);
int maps_cache_update_from_string(int tgid, const char *maps_data, int data_len);
void maps_cache_invalidate(int tgid);
void maps_cache_clear(void);

/* pkg_resolver */
int pkg_resolver_init(void);
int pkg_resolve_uid_to_pkg(unsigned int uid, char *pkg_out, int pkg_len);
int pkg_resolve_pkg_to_uid(const char *pkg_name);
int pkg_resolver_add_entry(unsigned int uid, const char *pkg_name);

/* syscall_monitor */
int syscall_monitor_init(void);
void syscall_monitor_on_syscall(int nr, unsigned long *args,
                                long retval, int narg);
const char *get_syscall_name(int nr);
unsigned char get_syscall_category(int nr);

/* hook_engine */
int hook_engine_init(void);
void hook_engine_destroy(void);
int hook_install_slim(void);
int hook_install_all(void);
int hook_install_range(int max_nr);
int hook_get_count(void);

/* file_logger */
int file_logger_init(void);
void file_logger_close(void);
void file_logger_enable(void);
void file_logger_disable(void);
int file_logger_write_event(const struct svc_event *event);
int file_logger_set_path(const char *path);
int file_logger_truncate(void);
void file_logger_flush(void);

/* --------------------------------------------------------------------------
 * 全局变量
 * -------------------------------------------------------------------------- */
extern struct tracer_config g_config;
extern struct tracer_stats g_stats;

#include <asm/current.h>
#include <ktypes.h>
#include <stdint.h>
#include <linux/cred.h>
#include <linux/sched.h>

/* 安全获取 current 的 comm 字段 */
static inline const char *safe_get_task_comm(void)
{
    if (!current)
        return "unknown";
    return get_task_comm(current);
}

/* 安全获取 tgid (即用户空间看到的 PID) */
static inline int safe_get_tgid(void)
{
    if (!current)
        return 0;
    return *(int *)((uintptr_t)current + task_struct_offset.tgid_offset);
}

/* 安全获取 tid (即内核 pid) */
static inline int safe_get_tid(void)
{
    if (!current)
        return 0;
    return *(int *)((uintptr_t)current + task_struct_offset.pid_offset);
}

/* 安全获取 uid */
static inline unsigned int safe_get_uid(void)
{
    const struct cred *cred;
    const kuid_t *uid;
    if (!current)
        return 0;
    cred = *(const struct cred **)((uintptr_t)current + task_struct_offset.cred_offset);
    if (!cred)
        return 0;
    uid = (const kuid_t *)((uintptr_t)cred + cred_offset.uid_offset);
    return uid->val;
}

#endif /* _SVC_TRACER_H_ */
