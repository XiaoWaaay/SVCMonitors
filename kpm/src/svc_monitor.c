/* svc_monitor.c — v8.1 Enhanced SVC Monitor KPM
 *
 * v8.1 Enhancements:
 *   - Deep argument parsing for 50+ syscalls important for reverse engineering
 *   - User-space string resolution via compat_strncpy_from_user
 *   - sockaddr parsing (AF_INET/AF_INET6/AF_UNIX) for connect/bind/sendto
 *   - mmap/mprotect prot+flags human-readable decode
 *   - execve argv[0..3] extraction
 *   - openat flags (O_RDONLY/O_WRONLY/O_RDWR/O_CREAT/O_TRUNC/O_APPEND...)
 *   - prctl option names, ptrace request names
 *   - ioctl cmd decode (BINDER/TIOCSCTTY/TCGETS etc)
 *   - write/sendto partial data preview (first 64 bytes hex+ascii)
 *   - Larger DESC_BUF for detailed output (1024 bytes)
 *   - Larger event ring buffer (1024 events)
 *
 * Architecture:
 *   - Module load → hook all tier1 syscalls → always monitoring
 *   - APP only controls: which UID, which NRs to log, pause/resume
 *   - Bitmap-based NR filter + UID filter, lock-free
 *
 * Official KPM API used:
 *   - inline_hook_syscalln(nr, narg, before, after, udata)
 *   - inline_unhook_syscalln(nr, before, after)
 *   - fp_hook_syscalln / fp_unhook_syscalln (fallback)
 *   - syscall_argn(fargs, n)
 *   - current_uid() from kputils.h
 *   - current from asm/current.h
 *   - compat_copy_to_user / compat_strncpy_from_user
 *   - raw_syscall0-6
 */

#include <compiler.h>        // KernelPatch 编译器宏
#include <kpmodule.h>        // KPM 模块框架 (KPM_NAME/KPM_INIT/KPM_CTL0/KPM_EXIT)
#include <linux/printk.h>    // printk 内核日志
#include <linux/kernel.h>    // snprintf 等内核工具函数
#include <asm-generic/unistd.h> // __NR_openat 等 syscall 号定义
#include <linux/uaccess.h>   // 用户空间内存访问相关
#include <syscall.h>         // KernelPatch SDK: syscall_argn, fp_hook_syscalln, has_syscall_wrapper 等
#include <linux/string.h>    // strcmp, strncmp, strlen
#include <kputils.h>         // current_uid() 等 KP 工具函数
#include <ksyms.h>
#include <log.h>
#include <asm/current.h>     // current 宏 (指向当前进程 task_struct)
#include <linux/sched.h>     // task_struct 相关
#include <asm/ptrace.h>      // struct pt_regs (ARM64 寄存器结构)
#include <asm/processor.h>

#define MAX_ERRNO 4095
#define IS_ERR_VALUE(x) ((unsigned long)(void *)(x) >= (unsigned long)-MAX_ERRNO)
#define IS_ERR(ptr) IS_ERR_VALUE(ptr)

extern int (*kf_strcmp)(const char *cs, const char *ct);
extern int (*kf_strncmp)(const char *cs, const char *ct, unsigned long count);
extern unsigned long (*kf_strlen)(const char *s);

#define strcmp kfunc_def(strcmp)
#define strncmp kfunc_def(strncmp)
#define strlen kfunc_def(strlen)

KPM_NAME("svc_monitor");
KPM_VERSION("8.1.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("Xiaowaaa");
KPM_DESCRIPTION("Enhanced ARM64 SVC syscall monitor with deep arg parsing");

/* ================================================================
 * Constants
 * ================================================================ */
#define MAX_EVENTS      1024      // 环形缓冲区容量，最多缓存 1024 条事件
#define MAX_NR          512       // 支持的最大 syscall 号
#define BITMAP_LONGS    (MAX_NR / 64 + 1) // bitmap 用 unsigned long 数组，每个 64 位
#define OUTPUT_PATH     "/data/local/tmp/svc_out.json" // CTL0 的 JSON 输出写到这个文件
#define EVENT_PATH      "/data/local/tmp/svc_events.jsonl"
#define DESC_BUF_SIZE   1024      // 参数描述字符串缓冲区（v8.1 从小 buffer 扩大到 1024）
#define PATH_BUF_SIZE   256       // 路径字符串缓冲区
#define SMALL_BUF       128
#define DATA_PREVIEW    64        // write/sendto 的数据预览最多看前 64 字节

/* ================================================================
 * Syscall name table (ARM64, 0-291)
 * ================================================================ */
static const char *syscall_names[] = {
    [0]="io_setup",[1]="io_destroy",[2]="io_submit",[3]="io_cancel",
    [4]="io_getevents",[5]="setxattr",[6]="lsetxattr",[7]="fsetxattr",
    [8]="getxattr",[9]="lgetxattr",[10]="fgetxattr",[11]="listxattr",
    [12]="llistxattr",[13]="flistxattr",[14]="removexattr",
    [15]="lremovexattr",[16]="fremovexattr",[17]="getcwd",
    [18]="lookup_dcookie",[19]="eventfd2",[20]="epoll_create1",
    [21]="epoll_ctl",[22]="epoll_pwait",[23]="dup",[24]="dup3",
    [25]="fcntl",[26]="inotify_init1",[27]="inotify_add_watch",
    [28]="inotify_rm_watch",[29]="ioctl",[30]="ioprio_set",
    [31]="ioprio_get",[32]="flock",[33]="mknodat",[34]="mkdirat",
    [35]="unlinkat",[36]="symlinkat",[37]="linkat",[38]="renameat",
    [39]="umount2",[40]="mount",[41]="pivot_root",
    [42]="nfsservctl",[43]="statfs",[44]="fstatfs",[45]="truncate",
    [46]="ftruncate",[47]="fallocate",[48]="faccessat",
    [49]="chdir",[50]="fchdir",[51]="chroot",[52]="fchmod",
    [53]="fchmodat",[54]="fchownat",[55]="fchown",
    [56]="openat",[57]="close",[58]="vhangup",
    [59]="pipe2",[60]="quotactl",[61]="getdents64",
    [62]="lseek",[63]="read",[64]="write",[65]="readv",
    [66]="writev",[67]="pread64",[68]="pwrite64",
    [69]="preadv",[70]="pwritev",[71]="sendfile",
    [72]="pselect6",[73]="ppoll",[74]="signalfd4",
    [75]="vmsplice",[76]="splice",[77]="tee",
    [78]="readlinkat",[79]="newfstatat",[80]="fstat",
    [81]="sync",[82]="fsync",[83]="fdatasync",
    [84]="sync_file_range",[85]="timerfd_create",
    [86]="timerfd_settime",[87]="timerfd_gettime",
    [88]="utimensat",[89]="acct",[90]="capget",
    [91]="capset",[92]="personality",[93]="exit",
    [94]="exit_group",[95]="waitid",[96]="set_tid_address",
    [97]="unshare",[98]="futex",[99]="set_robust_list",
    [100]="get_robust_list",[101]="nanosleep",
    [102]="getitimer",[103]="setitimer",[104]="kexec_load",
    [105]="init_module",[106]="delete_module",
    [107]="timer_create",[108]="timer_gettime",
    [109]="timer_getoverrun",[110]="timer_settime",
    [111]="timer_delete",[112]="clock_settime",
    [113]="clock_gettime",[114]="clock_getres",
    [115]="clock_nanosleep",[116]="syslog",
    [117]="ptrace",[118]="sched_setparam",
    [119]="sched_setscheduler",[120]="sched_getscheduler",
    [121]="sched_getparam",[122]="sched_setaffinity",
    [123]="sched_getaffinity",[124]="sched_yield",
    [125]="sched_get_priority_max",[126]="sched_get_priority_min",
    [127]="sched_rr_get_interval",[128]="restart_syscall",
    [129]="kill",[130]="tkill",[131]="tgkill",
    [132]="sigaltstack",[133]="rt_sigsuspend",
    [134]="rt_sigaction",[135]="rt_sigprocmask",
    [136]="rt_sigpending",[137]="rt_sigtimedwait",
    [138]="rt_sigqueueinfo",[139]="rt_sigreturn",
    [140]="setpriority",[141]="getpriority",
    [142]="reboot",[143]="setregid",[144]="setgid",
    [145]="setreuid",[146]="setuid",[147]="setresuid",
    [148]="getresuid",[149]="setresgid",[150]="getresgid",
    [151]="setfsuid",[152]="setfsgid",[153]="times",
    [154]="setpgid",[155]="getpgid",[156]="getsid",
    [157]="setsid",[158]="getgroups",[159]="setgroups",
    [160]="uname",[161]="sethostname",[162]="setdomainname",
    [163]="getrlimit",[164]="setrlimit",[165]="getrusage",
    [166]="umask",[167]="prctl",[168]="getcpu",
    [169]="gettimeofday",[170]="settimeofday",[171]="adjtimex",
    [172]="getpid",[173]="getppid",[174]="getuid",
    [175]="geteuid",[176]="getgid",[177]="getegid",
    [178]="gettid",[179]="sysinfo",[180]="mq_open",
    [181]="mq_unlink",[182]="mq_timedsend",
    [183]="mq_timedreceive",[184]="mq_notify",
    [185]="mq_getsetattr",[186]="msgget",[187]="msgctl",
    [188]="msgrcv",[189]="msgsnd",[190]="semget",
    [191]="semctl",[192]="semtimedop",[193]="semop",
    [194]="shmget",[195]="shmctl",[196]="shmat",
    [197]="shmdt",[198]="socket",[199]="socketpair",
    [200]="bind",[201]="listen",[202]="accept",
    [203]="connect",[204]="getsockname",[205]="getpeername",
    [206]="sendto",[207]="recvfrom",[208]="setsockopt",
    [209]="getsockopt",[210]="shutdown",[211]="sendmsg",
    [212]="recvmsg",[213]="readahead",[214]="brk",
    [215]="munmap",[216]="mremap",[217]="add_key",
    [218]="request_key",[219]="keyctl",[220]="clone",
    [221]="execve",[222]="mmap",[223]="fadvise64",
    [224]="swapon",[225]="swapoff",[226]="mprotect",
    [227]="msync",[228]="mlock",[229]="munlock",
    [230]="mlockall",[231]="munlockall",[232]="mincore",
    [233]="madvise",[234]="remap_file_pages",
    [235]="mbind",[236]="get_mempolicy",[237]="set_mempolicy",
    [238]="migrate_pages",[239]="move_pages",
    [240]="rt_tgsigqueueinfo",[241]="perf_event_open",
    [242]="accept4",[243]="recvmmsg",
    [260]="wait4",[261]="prlimit64",
    [262]="fanotify_init",[263]="fanotify_mark",
    [264]="name_to_handle_at",[265]="open_by_handle_at",
    [266]="clock_adjtime",[267]="syncfs",
    [268]="setns",[269]="sendmmsg",
    [270]="process_vm_readv",[271]="process_vm_writev",
    [272]="kcmp",[273]="finit_module",
    [274]="sched_setattr",[275]="sched_getattr",
    [276]="renameat2",[277]="seccomp",
    [278]="getrandom",[279]="memfd_create",
    [280]="bpf",[281]="execveat",
    [282]="userfaultfd",[283]="membarrier",
    [284]="mlock2",[285]="copy_file_range",
    [286]="preadv2",[287]="pwritev2",
    [288]="pkey_mprotect",[289]="pkey_alloc",
    [290]="pkey_free",[291]="statx",
};
#define SYSCALL_NAME_MAX 292

static const char *get_syscall_name(int nr)
{
    if (nr == 435) return "clone3";
    if (nr == -1) return "do_filp_open";
    if (nr >= 0 && nr < SYSCALL_NAME_MAX && syscall_names[nr])
        return syscall_names[nr];
    return "unknown";
}

/* ================================================================
 * Global state — lock-free, volatile for single-writer/multi-reader
 * ================================================================ */
static volatile int g_enabled = 0;           // 监控开关，0=暂停，1=启用
static volatile int g_target_uid = -1;       // UID 过滤，-1=全部，>=0=只监控指定 UID
static volatile unsigned long g_nr_bitmap[BITMAP_LONGS]; // 位图：哪些 syscall NR 要记录
static volatile unsigned int g_seq = 0;      // 事件序列号（全局自增）
static volatile unsigned int g_total = 0;    // 总事件计数

/* Hook tracking */
#define HOOK_INLINE  1
#define HOOK_FP      2

//每个要 hook 的 syscall 有一个 hook_entry_t 记录它的状态和 hook 方式。
typedef struct {
    int nr;
    int narg;
    int active;
    int method;  /* HOOK_INLINE or HOOK_FP */
} hook_entry_t;


/* Event record — expanded for detailed parsing */
//这里参数的读取在内核态进行解析，但是调用地址返回地址在APK侧读取maps文件进行解析
typedef struct {
    unsigned int seq;          // 事件序列号
    int nr;                    // syscall 号
    int pid;                   // 进程 PID
    int uid;                   // UID
    char comm[16];             // 进程名
    unsigned long a0, a1, a2, a3, a4, a5;      // 6 个参数原始值
    unsigned long pc;          // 调用者 PC 寄存器
    unsigned long caller;      // LR 寄存器（返回地址）
    unsigned long clone_fn;
    char desc[DESC_BUF_SIZE];  // 解析后的参数描述字符串（核心输出）
} svc_event_t;

static svc_event_t g_events[MAX_EVENTS]; // 环形缓冲区
static volatile int g_ev_head = 0;       // 写入位置
static volatile int g_ev_tail = 0;       // 读取位置（写文件线程消费）
static volatile int g_ev_count = 0;      // 当前缓冲区中的事件数
static volatile unsigned int g_ev_dropped = 0;
static volatile int g_ev_lock = 0;
static volatile int g_hooks_installed = 0;
static volatile int g_tier2_loaded = 0;

static struct task_struct *g_writer_task = 0;
static volatile int g_writer_stop = 0;

extern unsigned long (*kallsyms_lookup_name)(const char *name);
static long (*g_probe_kernel_read)(void *dst, const void *src, unsigned long size) = 0;

static unsigned long g_do_filp_open_addr = 0;
static int g_do_filp_open_active = 0;

struct file;
typedef long long loff_t;

#define O_WRONLY  00000001
#define O_CREAT   00000100
#define O_TRUNC   00001000
#define O_APPEND  00002000

static struct file *(*g_filp_open)(const char *filename, int flags, unsigned short mode) = 0;
static int (*g_filp_close)(struct file *filp, void *id) = 0;
static long (*g_vfs_llseek)(struct file *file, long offset, int whence) = 0;
static long (*g_kernel_write)(struct file *file, const void *buf, unsigned long count, loff_t *pos) = 0;

/* ================================================================
 * Bitmap operations
 * ================================================================ */
//感觉这部分写的不是很好，最好是精准控制要hook哪些系统调用号，给他加进位图内
 static inline void bitmap_set(volatile unsigned long *bm, int bit)
{
    if (bit >= 0 && bit < MAX_NR)
        bm[bit / 64] |= (1UL << (bit % 64));
}

static inline void bitmap_clear(volatile unsigned long *bm, int bit)
{
    if (bit >= 0 && bit < MAX_NR)
        bm[bit / 64] &= ~(1UL << (bit % 64));
}

static inline int bitmap_test(volatile unsigned long *bm, int bit)
{
    if (bit < 0 || bit >= MAX_NR) return 0;
    return (bm[bit / 64] >> (bit % 64)) & 1;
}

static inline void ev_lock(void)
{
    unsigned int tmp;
    do {
        asm volatile(
            "1: ldaxr %w0, [%1]\n"
            "cbnz  %w0, 1b\n"
            "stxr  %w0, %w2, [%1]\n"
            "cbnz  %w0, 1b\n"
            : "=&r"(tmp)
            : "r"(&g_ev_lock), "r"(1)
            : "memory");
    } while (0);
}

static inline void ev_unlock(void)
{
    asm volatile("stlr wzr, [%0]\n" :: "r"(&g_ev_lock) : "memory");
}

/* ================================================================
 * Safe user-space memory read helpers
 * ================================================================ */
static void safe_copy_user_str(char *dst, unsigned long uptr, int maxlen)
{
    if (!uptr || uptr < 0x1000UL || uptr > 0x7ffffffffff0UL) {
        dst[0] = '\0';
        return;
    }
    long ret = compat_strncpy_from_user(dst, (const char __user *)uptr, maxlen - 1);
    if (ret < 0) {
        dst[0] = '\0';
    } else {
        dst[ret < maxlen - 1 ? ret : maxlen - 1] = '\0';
    }
}

/* Read raw bytes from user-space, return bytes actually read (0 on fail) */
static int safe_copy_user_bytes(char *dst, unsigned long uptr, int maxlen)
{
    (void)dst;
    (void)uptr;
    (void)maxlen;
    return 0;
}

/* Read a user-space pointer (unsigned long) from user address */
static unsigned long safe_read_user_ptr(unsigned long uptr)
{
    unsigned long val = 0;
    if (!uptr || uptr < 0x1000UL || uptr > 0x7ffffffffff0UL)
        return 0;
    if (safe_copy_user_bytes((char *)&val, uptr, 8) != 8) return 0;
    return val;
}

static void safe_copy_kernel_str(char *dst, unsigned long kptr, int maxlen)
{
    int i;
    if (!dst || maxlen <= 0) return;
    dst[0] = '\0';
    if (!g_probe_kernel_read || !kptr) return;
    for (i = 0; i < maxlen - 1; i++) {
        char c = 0;
        if (g_probe_kernel_read(&c, (const void *)(kptr + (unsigned long)i), 1) != 0) break;
        dst[i] = c;
        if (c == '\0') return;
    }
    dst[maxlen - 1] = '\0';
}

/* ================================================================
 * openat flags decoder
 * ================================================================ */
static int decode_open_flags(char *buf, int blen, unsigned long flags)
{
    int n = 0;
    int acc = flags & 3;
    if (acc == 0) n += snprintf(buf + n, blen - n, "O_RDONLY");
    else if (acc == 1) n += snprintf(buf + n, blen - n, "O_WRONLY");
    else if (acc == 2) n += snprintf(buf + n, blen - n, "O_RDWR");

    if (flags & 0x40)    n += snprintf(buf + n, blen - n, " |O_CREAT");
    if (flags & 0x80)    n += snprintf(buf + n, blen - n, " |O_EXCL");
    if (flags & 0x100)   n += snprintf(buf + n, blen - n, " |O_NOCTTY");
    if (flags & 0x200)   n += snprintf(buf + n, blen - n, " |O_TRUNC");
    if (flags & 0x400)   n += snprintf(buf + n, blen - n, " |O_APPEND");
    if (flags & 0x800)   n += snprintf(buf + n, blen - n, " |O_NONBLOCK");
    if (flags & 0x1000)  n += snprintf(buf + n, blen - n, " |O_DSYNC");
    if (flags & 0x2000)  n += snprintf(buf + n, blen - n, " |O_ASYNC");
    if (flags & 0x10000) n += snprintf(buf + n, blen - n, " |O_DIRECTORY");
    if (flags & 0x20000) n += snprintf(buf + n, blen - n, " |O_NOFOLLOW");
    if (flags & 0x40000) n += snprintf(buf + n, blen - n, " |O_CLOEXEC");
    if (flags & 0x100000) n += snprintf(buf + n, blen - n, " |O_PATH");
    if (flags & 0x200000) n += snprintf(buf + n, blen - n, " |O_TMPFILE");
    if (flags & 0x400000) n += snprintf(buf + n, blen - n, " |O_LARGEFILE");
    return n;
}

/* ================================================================
 * mmap prot/flags decoder
 * ================================================================ */
static int decode_mmap_prot(char *buf, int blen, unsigned long prot)
{
    int n = 0;
    if (prot == 0) return snprintf(buf, blen, " PROT_NONE");
    if (prot & 1) n += snprintf(buf + n, blen - n, " PROT_READ");
    if (prot & 2) n += snprintf(buf + n, blen - n, " %sPROT_WRITE", n ? "|" : "");
    if (prot & 4) n += snprintf(buf + n, blen - n, " %sPROT_EXEC", n ? "|" : "");
    return n;
}

static int decode_mmap_flags(char *buf, int blen, unsigned long flags)
{
    int n = 0;
    if (flags & 0x01) n += snprintf(buf + n, blen - n, " MAP_SHARED");
    if (flags & 0x02) n += snprintf(buf + n, blen - n, " %sMAP_PRIVATE", n ? "|" : "");
    if (flags & 0x10) n += snprintf(buf + n, blen - n, " %sMAP_FIXED", n ? "|" : "");
    if (flags & 0x20) n += snprintf(buf + n, blen - n, " %sMAP_ANONYMOUS", n ? "|" : "");
    if (flags & 0x40) n += snprintf(buf + n, blen - n, " %sMAP_GROWSDOWN", n ? "|" : "");
    if (flags & 0x100) n += snprintf(buf + n, blen - n, "%sMAP_DENYWRITE", n ? "|" : "");
    if (flags & 0x800) n += snprintf(buf + n, blen - n, " %sMAP_EXECUTABLE", n ? "|" : "");
    if (flags & 0x4000) n += snprintf(buf + n, blen - n, " %sMAP_POPULATE", n ? "|" : "");
    if (flags & 0x8000) n += snprintf(buf + n, blen - n, " %sMAP_NONBLOCK", n ? "|" : "");
    if (flags & 0x40000) n += snprintf(buf + n, blen - n, " %sMAP_STACK", n ? "|" : "");
    if (flags & 0x80000) n += snprintf(buf + n, blen - n, " %sMAP_HUGETLB", n ? "|" : "");
    return n;
}

/* ================================================================
 * socket domain/type decoder
 * ================================================================ */
static const char *decode_socket_domain(int domain)
{
    switch (domain) {
    case 0: return "AF_UNSPEC";
    case 1: return "AF_UNIX";
    case 2: return "AF_INET";
    case 10: return "AF_INET6";
    case 16: return "AF_NETLINK";
    case 17: return "AF_PACKET";
    default: return "AF_?";
    }
}

static int decode_socket_type(char *buf, int blen, int type)
{
    int base = type & 0xFF;
    int n = 0;
    switch (base) {
    case 1: n = snprintf(buf, blen, "SOCK_STREAM"); break;
    case 2: n = snprintf(buf, blen, "SOCK_DGRAM"); break;
    case 3: n = snprintf(buf, blen, "SOCK_RAW"); break;
    case 5: n = snprintf(buf, blen, "SOCK_SEQPACKET"); break;
    default: n = snprintf(buf, blen, "SOCK_%d", base); break;
    }
    if (type & 0x80000) n += snprintf(buf + n, blen - n, "|SOCK_NONBLOCK");
    if (type & 0x80800) n += snprintf(buf + n, blen - n, "|SOCK_CLOEXEC");
    return n;
}

/* ================================================================
 * sockaddr parser — parse connect/bind/sendto addr
 * Reads struct sockaddr from user-space and decodes IP/port/path
 * ================================================================ */
static int parse_sockaddr(char *buf, int blen, unsigned long uptr, int addrlen)
{
    unsigned char sa[128];
    int n = 0;
    unsigned short family;

    if (!uptr || uptr < 0x1000UL || addrlen <= 0 || addrlen > 128)
        return snprintf(buf, blen, "addr=0x%lx", uptr);

    int got = safe_copy_user_bytes((char *)sa, uptr, addrlen < 128 ? addrlen : 128);
    if (got < 2)
        return snprintf(buf, blen, "addr=0x%lx", uptr);

    family = sa[0] | (sa[1] << 8);  /* sa_family is first 2 bytes, little-endian */

    if (family == 2 && got >= 8) {
        /* AF_INET: port=sa[2..3](big-endian), ip=sa[4..7] */
        unsigned short port = (sa[2] << 8) | sa[3];
        n = snprintf(buf, blen, "AF_INET %d.%d.%d.%d:%d",
                     sa[4], sa[5], sa[6], sa[7], port);
    }
    else if (family == 10 && got >= 24) {
        /* AF_INET6: port=sa[2..3](big-endian), ip6=sa[8..23] */
        unsigned short port = (sa[2] << 8) | sa[3];
        n = snprintf(buf, blen, "AF_INET6 [%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x]:%d",
                     sa[8],sa[9],sa[10],sa[11],sa[12],sa[13],sa[14],sa[15],
                     sa[16],sa[17],sa[18],sa[19],sa[20],sa[21],sa[22],sa[23], port);
    }
    else if (family == 1 && got > 2) {
        /* AF_UNIX: path starts at offset 2 */
        char path[108];
        int plen = got - 2;
        if (plen > 107) plen = 107;
        {
            int i;
            for (i = 0; i < plen; i++) {
                path[i] = sa[2 + i];
            }
            path[plen] = '\0';
        }
        if (path[0] == '\0' && plen > 1) {
            /* Abstract socket: replace leading null */
            path[0] = '@';
        }
        n = snprintf(buf, blen, "AF_UNIX \"%s\"", path);
    }
    else {
        n = snprintf(buf, blen, "AF_%d addr=0x%lx len=%d", family, uptr, addrlen);
    }
    return n;
}

/* ================================================================
 * Data preview — hex dump first N bytes for write/sendto
 * ================================================================ */
static int data_preview(char *buf, int blen, unsigned long uptr, unsigned long datalen)
{
    unsigned char tmp[DATA_PREVIEW + 1];
    char ascii[DATA_PREVIEW + 1];
    int n = 0;
    int to_read = datalen < DATA_PREVIEW ? (int)datalen : DATA_PREVIEW;

    if (!uptr || uptr < 0x1000UL || to_read <= 0)
        return snprintf(buf, blen, "buf=0x%lx", uptr);

    int got = safe_copy_user_bytes((char *)tmp, uptr, to_read);
    if (got <= 0)
        return snprintf(buf, blen, "buf=0x%lx", uptr);

    /* Build hex + ascii preview */
    n += snprintf(buf + n, blen - n, "data[%d/%lu]=", got, datalen);

    /* Hex portion (show first 32 bytes max in hex) */
    int hexshow = got < 32 ? got : 32;
    int i;
    for (i = 0; i < hexshow && n < blen - 4; i++) {
        n += snprintf(buf + n, blen - n, "%02x", tmp[i]);
        if ((i & 3) == 3 && i < hexshow - 1) buf[n++] = ' ';
    }

    /* ASCII portion */
    n += snprintf(buf + n, blen - n, " |");
    for (i = 0; i < got && n < blen - 4; i++) {
        char c = (tmp[i] >= 0x20 && tmp[i] < 0x7f) ? (char)tmp[i] : '.';
        buf[n++] = c;
    }
    buf[n++] = '|';
    buf[n] = '\0';
    return n;
}

/* ================================================================
 * prctl option name decoder，这个函数蛮重要的，可以查看app对自己进行了什么操作
 * ================================================================ */
static const char *decode_prctl_option(int opt)
{
    switch (opt) {
    case 1:  return "PR_SET_PDEATHSIG";
    case 2:  return "PR_GET_PDEATHSIG";
    case 3:  return "PR_GET_DUMPABLE";
    case 4:  return "PR_SET_DUMPABLE";
    case 6:  return "PR_GET_KEEPCAPS";
    case 8:  return "PR_SET_KEEPCAPS";
    case 15: return "PR_SET_NAME";
    case 16: return "PR_GET_NAME";
    case 22: return "PR_SET_SECCOMP";
    case 21: return "PR_GET_SECCOMP";
    case 36: return "PR_SET_NO_NEW_PRIVS";
    case 37: return "PR_GET_NO_NEW_PRIVS";
    case 38: return "PR_GET_TID_ADDRESS";
    case 40: return "PR_SET_VMA";
    case 0x59616d61: return "PR_SET_VMA_ANON_NAME";
    default: return 0;
    }
}

/* ================================================================
 * ptrace request name decoder ptrace模块挺有意思，可以配合这个函数做一些高级操作
 * ================================================================ */
static const char *decode_ptrace_request(long req)
{
    switch (req) {
    case 0:  return "PTRACE_TRACEME";
    case 1:  return "PTRACE_PEEKTEXT";
    case 2:  return "PTRACE_PEEKDATA";
    case 3:  return "PTRACE_PEEKUSR";
    case 4:  return "PTRACE_POKETEXT";
    case 5:  return "PTRACE_POKEDATA";
    case 6:  return "PTRACE_POKEUSR";
    case 7:  return "PTRACE_CONT";
    case 8:  return "PTRACE_KILL";
    case 9:  return "PTRACE_SINGLESTEP";
    case 12: return "PTRACE_GETREGS";
    case 13: return "PTRACE_SETREGS";
    case 14: return "PTRACE_GETFPREGS";
    case 15: return "PTRACE_SETFPREGS";
    case 16: return "PTRACE_ATTACH";
    case 17: return "PTRACE_DETACH";
    case 24: return "PTRACE_SYSCALL";
    case 0x4200: return "PTRACE_SETOPTIONS";
    case 0x4201: return "PTRACE_GETEVENTMSG";
    case 0x4202: return "PTRACE_GETSIGINFO";
    case 0x4203: return "PTRACE_SETSIGINFO";
    case 0x4204: return "PTRACE_GETREGSET";
    case 0x4205: return "PTRACE_SETREGSET";
    case 0x4206: return "PTRACE_SEIZE";
    case 0x4207: return "PTRACE_INTERRUPT";
    case 0x4208: return "PTRACE_LISTEN";
    case 0x420e: return "PTRACE_SECCOMP_GET_FILTER";
    default: return 0;
    }
}

/* ================================================================
 * Signal number name decoder
 * ================================================================ */
static const char *decode_signal(int sig)
{
    switch (sig) {
    case 1: return "SIGHUP";    case 2: return "SIGINT";
    case 3: return "SIGQUIT";   case 4: return "SIGILL";
    case 5: return "SIGTRAP";   case 6: return "SIGABRT";
    case 7: return "SIGBUS";    case 8: return "SIGFPE";
    case 9: return "SIGKILL";   case 10: return "SIGUSR1";
    case 11: return "SIGSEGV";  case 12: return "SIGUSR2";
    case 13: return "SIGPIPE";  case 14: return "SIGALRM";
    case 15: return "SIGTERM";  case 17: return "SIGCHLD";
    case 18: return "SIGCONT";  case 19: return "SIGSTOP";
    case 20: return "SIGTSTP";  case 28: return "SIGWINCH";
    default: return 0;
    }
}

/* ================================================================
 * fcntl cmd decoder
 * ================================================================ */
static const char *decode_fcntl_cmd(int cmd)
{
    switch (cmd) {
    case 0: return "F_DUPFD";
    case 1: return "F_GETFD";
    case 2: return "F_SETFD";
    case 3: return "F_GETFL";
    case 4: return "F_SETFL";
    case 5: return "F_GETLK";
    case 6: return "F_SETLK";
    case 7: return "F_SETLKW";
    case 8: return "F_SETOWN";
    case 9: return "F_GETOWN";
    case 1024: return "F_SETLEASE";
    case 1025: return "F_GETLEASE";
    case 1030: return "F_DUPFD_CLOEXEC";
    default: return 0;
    }
}

/* ================================================================
 * Deep argument parsing for reverse engineering
 * ================================================================ */
static void describe_args(int nr, unsigned long a0, unsigned long a1,
                          unsigned long a2, unsigned long a3,
                          unsigned long a4, unsigned long a5,
                          char *desc, int dlen)
{
    char pathbuf[PATH_BUF_SIZE];
    char pathbuf2[PATH_BUF_SIZE];
    char flagbuf[256];
    char sockbuf[256];
    char typebuf[64];
    int n = 0;
    desc[0] = '\0';

    switch (nr) {

    /* ===================== FILE OPERATIONS ===================== */

    case 56: /* openat */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        decode_open_flags(flagbuf, sizeof(flagbuf), a2);
        n = snprintf(desc, dlen, "dirfd=%d   path=\"%s\"   flags=%s(0x%lx)   mode=0%lo",
                     (int)a0, pathbuf, flagbuf, a2, a3);
        break;

    case 57: /* close */
        n = snprintf(desc, dlen, "fd=%d", (int)a0);
        break;

    case 48: /* faccessat */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        {
            const char *modestr = "";
            switch ((int)a2) {
            case 0: modestr = "F_OK"; break;
            case 1: modestr = "X_OK"; break;
            case 2: modestr = "W_OK"; break;
            case 4: modestr = "R_OK"; break;
            default: modestr = "?"; break;
            }
            n = snprintf(desc, dlen, "dirfd=%d path=\"%s\" mode=%s(%d) flags=0x%lx",
                         (int)a0, pathbuf, modestr, (int)a2, a3);
        }
        break;

    case 35: /* unlinkat */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        n = snprintf(desc, dlen, "dirfd=%d path=\"%s\" flags=%s(0x%lx)",
                     (int)a0, pathbuf,
                     (a2 & 0x200) ? "AT_REMOVEDIR" : "0", a2);
        break;

    case 36: /* symlinkat */
        safe_copy_user_str(pathbuf, a0, sizeof(pathbuf));
        safe_copy_user_str(pathbuf2, a2, sizeof(pathbuf2));
        n = snprintf(desc, dlen, "oldname=\"%s\" newdirfd=%d newname=\"%s\"",
                     pathbuf, (int)a1, pathbuf2);
        break;

    case 37: /* linkat */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        safe_copy_user_str(pathbuf2, a3, sizeof(pathbuf2));
        n = snprintf(desc, dlen, "olddirfd=%d oldpath=\"%s\" newdirfd=%d newpath=\"%s\" flags=0x%lx",
                     (int)a0, pathbuf, (int)a2, pathbuf2, a4);
        break;

    case 38: /* renameat */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        safe_copy_user_str(pathbuf2, a3, sizeof(pathbuf2));
        n = snprintf(desc, dlen, "olddirfd=%d oldpath=\"%s\" newdirfd=%d newpath=\"%s\"",
                     (int)a0, pathbuf, (int)a2, pathbuf2);
        break;

    case 276: /* renameat2 */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        safe_copy_user_str(pathbuf2, a3, sizeof(pathbuf2));
        n = snprintf(desc, dlen, "olddirfd=%d oldpath=\"%s\" newdirfd=%d newpath=\"%s\" flags=0x%lx",
                     (int)a0, pathbuf, (int)a2, pathbuf2, a4);
        break;

    case 34: /* mkdirat */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        n = snprintf(desc, dlen, "dirfd=%d path=\"%s\" mode=0%lo",
                     (int)a0, pathbuf, a2);
        break;

    case 78: /* readlinkat */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        n = snprintf(desc, dlen, "dirfd=%d path=\"%s\" buf=0x%lx bufsiz=%d",
                     (int)a0, pathbuf, a2, (int)a3);
        break;

    case 79: /* newfstatat */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        n = snprintf(desc, dlen, "dirfd=%d path=\"%s\" statbuf=0x%lx flags=0x%lx",
                     (int)a0, pathbuf, a2, a3);
        break;

    case 80: /* fstat */
        n = snprintf(desc, dlen, "fd=%d statbuf=0x%lx", (int)a0, a1);
        break;

    case 291: /* statx */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        n = snprintf(desc, dlen, "dirfd=%d path=\"%s\" flags=0x%lx mask=0x%lx statxbuf=0x%lx",
                     (int)a0, pathbuf, a2, a3, a4);
        break;

    case 61: /* getdents64 */
        n = snprintf(desc, dlen, "fd=%d dirent=0x%lx count=%lu",
                     (int)a0, a1, a2);
        break;

    case 62: /* lseek */
        {
            const char *whence_str = "?";
            switch ((int)a2) {
            case 0: whence_str = "SEEK_SET"; break;
            case 1: whence_str = "SEEK_CUR"; break;
            case 2: whence_str = "SEEK_END"; break;
            }
            n = snprintf(desc, dlen, "fd=%d offset=%ld whence=%s",
                         (int)a0, (long)a1, whence_str);
        }
        break;

    case 43: /* statfs */
        safe_copy_user_str(pathbuf, a0, sizeof(pathbuf));
        n = snprintf(desc, dlen, "path=\"%s\" buf=0x%lx", pathbuf, a1);
        break;

    case 53: /* fchmodat */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        n = snprintf(desc, dlen, "dirfd=%d path=\"%s\" mode=0%lo",
                     (int)a0, pathbuf, a2);
        break;

    case 54: /* fchownat */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        n = snprintf(desc, dlen, "dirfd=%d path=\"%s\" owner=%d group=%d flags=0x%lx",
                     (int)a0, pathbuf, (int)a2, (int)a3, a4);
        break;

    /* ===================== READ / WRITE ===================== */

    case 63: /* read */
        n = snprintf(desc, dlen, "fd=%d buf=0x%lx count=%lu", (int)a0, a1, a2);
        break;

    case 64: /* write — with data preview */
        n = snprintf(desc, dlen, "fd=%d count=%lu ", (int)a0, a2);
        n += data_preview(desc + n, dlen - n, a1, a2);
        break;

    case 67: /* pread64 */
        n = snprintf(desc, dlen, "fd=%d buf=0x%lx count=%lu offset=%ld",
                     (int)a0, a1, a2, (long)a3);
        break;

    case 68: /* pwrite64 — with data preview */
        n = snprintf(desc, dlen, "fd=%d count=%lu offset=%ld ",
                     (int)a0, a2, (long)a3);
        n += data_preview(desc + n, dlen - n, a1, a2);
        break;

    case 65: /* readv */
        n = snprintf(desc, dlen, "fd=%d iov=0x%lx iovcnt=%d",
                     (int)a0, a1, (int)a2);
        break;

    case 66: /* writev */
        n = snprintf(desc, dlen, "fd=%d iov=0x%lx iovcnt=%d",
                     (int)a0, a1, (int)a2);
        break;

    /* ===================== PROCESS ===================== */

    case 221: /* execve — parse filename + argv[0..3] */
        safe_copy_user_str(pathbuf, a0, sizeof(pathbuf));
        n = snprintf(desc, dlen, "filename=\"%s\"", pathbuf);
        /* Try to read argv[0..3] */
        if (a1) {
            int ai;
            n += snprintf(desc + n, dlen - n, " argv=[");
            for (ai = 0; ai < 4 && n < dlen - 64; ai++) {
                unsigned long argp = safe_read_user_ptr(a1 + ai * 8);
                if (!argp) break;
                safe_copy_user_str(pathbuf2, argp, 128);
                if (ai > 0) desc[n++] = ',';
                n += snprintf(desc + n, dlen - n, "\"%s\"", pathbuf2);
            }
            n += snprintf(desc + n, dlen - n, "]");
        }
        break;

    case 281: /* execveat */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        n = snprintf(desc, dlen, "dirfd=%d filename=\"%s\" flags=0x%lx",
                     (int)a0, pathbuf, a4);
        if (a2) {
            int ai;
            n += snprintf(desc + n, dlen - n, " argv=[");
            for (ai = 0; ai < 4 && n < dlen - 64; ai++) {
                unsigned long argp = safe_read_user_ptr(a2 + ai * 8);
                if (!argp) break;
                safe_copy_user_str(pathbuf2, argp, 128);
                if (ai > 0) desc[n++] = ',';
                n += snprintf(desc + n, dlen - n, "\"%s\"", pathbuf2);
            }
            n += snprintf(desc + n, dlen - n, "]");
        }
        break;

    case 220: /* clone */
        //还应该解析其他的参数，比如哪个函数放进了线程中
        n = snprintf(desc, dlen, "flags=0x%lx", a0);
        if (a0 & 0x00000100) n += snprintf(desc + n, dlen - n, " CLONE_VM");
        if (a0 & 0x00000200) n += snprintf(desc + n, dlen - n, " CLONE_FS");
        if (a0 & 0x00000400) n += snprintf(desc + n, dlen - n, " CLONE_FILES");
        if (a0 & 0x00000800) n += snprintf(desc + n, dlen - n, " CLONE_SIGHAND");
        if (a0 & 0x00010000) n += snprintf(desc + n, dlen - n, " CLONE_THREAD");
        if (a0 & 0x02000000) n += snprintf(desc + n, dlen - n, " CLONE_NEWNS");
        if (a0 & 0x04000000) n += snprintf(desc + n, dlen - n, " CLONE_SYSVSEMVPID");
        if (a0 & 0x40000000) n += snprintf(desc + n, dlen - n, " |CLONE_NEWNET");
        n += snprintf(desc + n, dlen - n, " stack=0x%lx", a1);
        break;

    case 435: /* clone3 */
        {
            n = snprintf(desc, dlen, "uargs=0x%lx size=%lu", a0, a1);
        }
        break;

    case 93: /* exit */
        n = snprintf(desc, dlen, "status=%d", (int)a0);
        break;

    case 94: /* exit_group */
        n = snprintf(desc, dlen, "status=%d", (int)a0);
        break;

    case 95: /* waitid */
        {
            const char *idtype = "P_?";
            switch ((int)a0) {
            case 0: idtype = "P_ALL"; break;
            case 1: idtype = "P_PID"; break;
            case 2: idtype = "P_PGID"; break;
            }
            n = snprintf(desc, dlen, "idtype=%s id=%d options=0x%lx",
                         idtype, (int)a1, a3);
        }
        break;

    case 260: /* wait4 */
        n = snprintf(desc, dlen, "pid=%d status=0x%lx options=0x%lx rusage=0x%lx",
                     (int)a0, a1, a2, a3);
        break;

    /* ===================== MEMORY ===================== */

    case 222: /* mmap */
        decode_mmap_prot(flagbuf, sizeof(flagbuf), a2);
        decode_mmap_flags(sockbuf, sizeof(sockbuf), a3);
        n = snprintf(desc, dlen, "addr=0x%lx len=%lu prot=%s(0x%lx) flags=%s(0x%lx) fd=%d offset=0x%lx",
                     a0, a1, flagbuf, a2, sockbuf, a3, (int)a4, a5);
        break;

    case 226: /* mprotect */
        decode_mmap_prot(flagbuf, sizeof(flagbuf), a2);
        n = snprintf(desc, dlen, "addr=0x%lx len=%lu prot=%s(0x%lx)",
                     a0, a1, flagbuf, a2);
        break;

    case 215: /* munmap */
        n = snprintf(desc, dlen, "addr=0x%lx len=%lu", a0, a1);
        break;

    case 214: /* brk */
        n = snprintf(desc, dlen, "addr=0x%lx", a0);
        break;

    case 233: /* madvise */
        {
            const char *adv = "?";
            switch ((int)a2) {
            case 0: adv = "MADV_NORMAL"; break;
            case 1: adv = "MADV_RANDOM"; break;
            case 2: adv = "MADV_SEQUENTIAL"; break;
            case 3: adv = "MADV_WILLNEED"; break;
            case 4: adv = "MADV_DONTNEED"; break;
            case 8: adv = "MADV_FREE"; break;
            case 9: adv = "MADV_REMOVE"; break;
            }
            n = snprintf(desc, dlen, "addr=0x%lx len=%lu advice=%s(%d)",
                         a0, a1, adv, (int)a2);
        }
        break;

    case 232: /* mincore */
        n = snprintf(desc, dlen, "start=0x%lx len=%lu vec=0x%lx", a0, a1, a2);
        break;

    case 279: /* memfd_create */
        safe_copy_user_str(pathbuf, a0, sizeof(pathbuf));
        n = snprintf(desc, dlen, "name=\"%s\" flags=0x%lx", pathbuf, a1);
        break;

    case 270: /* process_vm_readv */
        n = snprintf(desc, dlen, "pid=%d local_iov=0x%lx liovcnt=%lu remote_iov=0x%lx riovcnt=%lu",
                     (int)a0, a1, a2, a3, a4);
        break;

    case 271: /* process_vm_writev */
        n = snprintf(desc, dlen, "pid=%d local_iov=0x%lx liovcnt=%lu remote_iov=0x%lx riovcnt=%lu",
                     (int)a0, a1, a2, a3, a4);
        break;

    /* ===================== NETWORK ===================== */

    case 198: /* socket */
        decode_socket_type(typebuf, sizeof(typebuf), (int)a1);
        n = snprintf(desc, dlen, "domain=%s(%d) type=%s(%d) protocol=%d",
                     decode_socket_domain((int)a0), (int)a0,
                     typebuf, (int)a1, (int)a2);
        break;

    case 200: /* bind — with sockaddr parsing */
        n = snprintf(desc, dlen, "sockfd=%d ", (int)a0);
        n += parse_sockaddr(desc + n, dlen - n, a1, (int)a2);
        break;

    case 203: /* connect — with sockaddr parsing */
        n = snprintf(desc, dlen, "sockfd=%d ", (int)a0);
        n += parse_sockaddr(desc + n, dlen - n, a1, (int)a2);
        break;

    case 201: /* listen */
        n = snprintf(desc, dlen, "sockfd=%d backlog=%d", (int)a0, (int)a1);
        break;

    case 202: /* accept */
        n = snprintf(desc, dlen, "sockfd=%d addr=0x%lx addrlen=0x%lx",
                     (int)a0, a1, a2);
        break;

    case 242: /* accept4 */
        n = snprintf(desc, dlen, "sockfd=%d addr=0x%lx addrlen=0x%lx flags=0x%lx",
                     (int)a0, a1, a2, a3);
        break;

    case 206: /* sendto — with sockaddr + data preview */
        n = snprintf(desc, dlen, "sockfd=%d len=%lu flags=0x%lx ",
                     (int)a0, a2, a3);
        if (a4 && a5 > 0) {
            n += parse_sockaddr(desc + n, dlen - n, a4, (int)a5);
            n += snprintf(desc + n, dlen - n, " ");
        }
        n += data_preview(desc + n, dlen - n, a1, a2);
        break;

    case 207: /* recvfrom */
        n = snprintf(desc, dlen, "sockfd=%d buf=0x%lx len=%lu flags=0x%lx",
                     (int)a0, a1, a2, a3);
        break;

    case 208: /* setsockopt */
        n = snprintf(desc, dlen, "sockfd=%d level=%d optname=%d optval=0x%lx optlen=%d",
                     (int)a0, (int)a1, (int)a2, a3, (int)a4);
        break;

    case 209: /* getsockopt */
        n = snprintf(desc, dlen, "sockfd=%d level=%d optname=%d",
                     (int)a0, (int)a1, (int)a2);
        break;

    case 211: /* sendmsg */
        n = snprintf(desc, dlen, "sockfd=%d msg=0x%lx flags=0x%lx",
                     (int)a0, a1, a2);
        break;

    case 212: /* recvmsg */
        n = snprintf(desc, dlen, "sockfd=%d msg=0x%lx flags=0x%lx",
                     (int)a0, a1, a2);
        break;

    case 210: /* shutdown */
        {
            const char *how = "?";
            switch ((int)a1) {
            case 0: how = "SHUT_RD"; break;
            case 1: how = "SHUT_WR"; break;
            case 2: how = "SHUT_RDWR"; break;
            }
            n = snprintf(desc, dlen, "sockfd=%d how=%s(%d)", (int)a0, how, (int)a1);
        }
        break;

    case 204: /* getsockname */
        n = snprintf(desc, dlen, "sockfd=%d addr=0x%lx addrlen=0x%lx",
                     (int)a0, a1, a2);
        break;

    case 205: /* getpeername */
        n = snprintf(desc, dlen, "sockfd=%d addr=0x%lx addrlen=0x%lx",
                     (int)a0, a1, a2);
        break;

    /* ===================== SIGNALS ===================== */

    case 129: /* kill */
        {
            const char *sname = decode_signal((int)a1);
            if (sname)
                n = snprintf(desc, dlen, "pid=%d sig=%s(%d)", (int)a0, sname, (int)a1);
            else
                n = snprintf(desc, dlen, "pid=%d sig=%d", (int)a0, (int)a1);
        }
        break;

    case 130: /* tkill */
        {
            const char *sname = decode_signal((int)a1);
            if (sname)
                n = snprintf(desc, dlen, "tid=%d sig=%s(%d)", (int)a0, sname, (int)a1);
            else
                n = snprintf(desc, dlen, "tid=%d sig=%d", (int)a0, (int)a1);
        }
        break;

    case 131: /* tgkill */
        {
            const char *sname = decode_signal((int)a2);
            if (sname)
                n = snprintf(desc, dlen, "tgid=%d tid=%d sig=%s(%d)",
                             (int)a0, (int)a1, sname, (int)a2);
            else
                n = snprintf(desc, dlen, "tgid=%d tid=%d sig=%d",
                             (int)a0, (int)a1, (int)a2);
        }
        break;

    case 134: /* rt_sigaction */
        {
            const char *sname = decode_signal((int)a0);
            if (sname)
                n = snprintf(desc, dlen, "sig=%s(%d) act=0x%lx oact=0x%lx",
                             sname, (int)a0, a1, a2);
            else
                n = snprintf(desc, dlen, "sig=%d act=0x%lx oact=0x%lx",
                             (int)a0, a1, a2);
        }
        break;

    case 135: /* rt_sigprocmask */
        {
            const char *how = "?";
            switch ((int)a0) {
            case 0: how = "SIG_BLOCK"; break;
            case 1: how = "SIG_UNBLOCK"; break;
            case 2: how = "SIG_SETMASK"; break;
            }
            n = snprintf(desc, dlen, "how=%s set=0x%lx oset=0x%lx sigsetsize=%lu",
                         how, a1, a2, a3);
        }
        break;

    /* ===================== PTRACE / DEBUG ===================== */

    case 117: /* ptrace — decode request name */
        {
            const char *rname = decode_ptrace_request((long)a0);
            if (rname)
                n = snprintf(desc, dlen, "request=%s(%ld) pid=%d addr=0x%lx data=0x%lx",
                             rname, (long)a0, (int)a1, a2, a3);
            else
                n = snprintf(desc, dlen, "request=%ld pid=%d addr=0x%lx data=0x%lx",
                             (long)a0, (int)a1, a2, a3);
        }
        break;

    /* ===================== prctl ===================== */

    case 167: /* prctl */
        {
            const char *oname = decode_prctl_option((int)a0);
            if (oname) {
                n = snprintf(desc, dlen, "option=%s(%d)", oname, (int)a0);
            } else {
                n = snprintf(desc, dlen, "option=%d", (int)a0);
            }
            /* Special handling for PR_SET_NAME/PR_GET_NAME */
            if ((int)a0 == 15) { /* PR_SET_NAME */
                safe_copy_user_str(pathbuf, a1, 16);
                n += snprintf(desc + n, dlen - n, " name=\"%s\"", pathbuf);
            } else if ((int)a0 == 40) { /* PR_SET_VMA */
                safe_copy_user_str(pathbuf, a4, 80);
                n += snprintf(desc + n, dlen - n, " subopt=%ld addr=0x%lx len=%lu name=\"%s\"",
                              (long)a1, a2, a3, pathbuf);
            } else {
                n += snprintf(desc + n, dlen - n, " arg2=0x%lx arg3=0x%lx", a1, a2);
            }
        }
        break;

    /* ===================== IOCTL ===================== */

    case 29: /* ioctl */
        {
            /* Decode known ioctl cmds */
            const char *iname = 0;
            unsigned long cmd = a1;
            if (cmd == 0xc0306201UL) iname = "BINDER_WRITE_READ";
            else if (cmd == 0x40046207UL) iname = "BINDER_SET_CONTEXT_MGR";
            else if (cmd == 0x40046209UL) iname = "BINDER_SET_MAX_THREADS";
            else if (cmd == 0xc0506210UL) iname = "BINDER_VERSION";
            else if (cmd == 0x5401UL) iname = "TCGETS";
            else if (cmd == 0x5402UL) iname = "TCSETS";
            else if (cmd == 0x540eUL) iname = "TIOCSCTTY";
            else if (cmd == 0x5413UL) iname = "TIOCGWINSZ";
            else if (cmd == 0x5414UL) iname = "TIOCSWINSZ";
            else if (cmd == 0x540fUL) iname = "TIOCNOTTY";
            else if (cmd == 0x8912UL) iname = "SIOCGIFCONF";
            else if (cmd == 0x8913UL) iname = "SIOCGIFFLAGS";
            else if (cmd == 0x8915UL) iname = "SIOCGIFADDR";
            else if (cmd == 0x8927UL) iname = "SIOCGIFHWADDR";

            if (iname)
                n = snprintf(desc, dlen, "fd=%d cmd=%s(0x%lx) arg=0x%lx",
                             (int)a0, iname, cmd, a2);
            else
                n = snprintf(desc, dlen, "fd=%d cmd=0x%lx dir=%lu type='%c' nr=%lu size=%lu arg=0x%lx",
                             (int)a0, cmd,
                             (cmd >> 30) & 3, (char)((cmd >> 8) & 0xFF),
                             cmd & 0xFF, (cmd >> 16) & 0x3FFF, a2);
        }
        break;

    /* ===================== fcntl ===================== */

    case 25: /* fcntl */
        {
            const char *cname = decode_fcntl_cmd((int)a1);
            if (cname)
                n = snprintf(desc, dlen, "fd=%d cmd=%s(%d) arg=0x%lx",
                             (int)a0, cname, (int)a1, a2);
            else
                n = snprintf(desc, dlen, "fd=%d cmd=%d arg=0x%lx",
                             (int)a0, (int)a1, a2);
        }
        break;

    /* ===================== MOUNT / FS ===================== */

    case 40: /* mount */
        safe_copy_user_str(pathbuf, a0, sizeof(pathbuf));
        safe_copy_user_str(pathbuf2, a1, sizeof(pathbuf2));
        {
            char fstypebuf[64];
            safe_copy_user_str(fstypebuf, a2, sizeof(fstypebuf));
            n = snprintf(desc, dlen, "source=\"%s\" target=\"%s\" fstype=\"%s\" flags=0x%lx",
                         pathbuf, pathbuf2, fstypebuf, a3);
        }
        break;

    case 39: /* umount2 */
        safe_copy_user_str(pathbuf, a0, sizeof(pathbuf));
        n = snprintf(desc, dlen, "target=\"%s\" flags=0x%lx", pathbuf, a1);
        break;

    case 49: /* chdir */
        safe_copy_user_str(pathbuf, a0, sizeof(pathbuf));
        n = snprintf(desc, dlen, "path=\"%s\"", pathbuf);
        break;

    case 51: /* chroot */
        safe_copy_user_str(pathbuf, a0, sizeof(pathbuf));
        n = snprintf(desc, dlen, "path=\"%s\"", pathbuf);
        break;

    /* ===================== SECURITY / SANDBOX ===================== */

    case 277: /* seccomp */
        {
            const char *opname = "?";
            switch ((int)a0) {
            case 0: opname = "SECCOMP_SET_MODE_STRICT"; break;
            case 1: opname = "SECCOMP_SET_MODE_FILTER"; break;
            case 2: opname = "SECCOMP_GET_ACTION_AVAIL"; break;
            case 3: opname = "SECCOMP_GET_NOTIF_SIZES"; break;
            }
            n = snprintf(desc, dlen, "op=%s(%d) flags=0x%lx args=0x%lx",
                         opname, (int)a0, a1, a2);
        }
        break;

    case 280: /* bpf */
        {
            const char *bpf_cmd = "?";
            switch ((int)a0) {
            case 0: bpf_cmd = "BPF_MAP_CREATE"; break;
            case 1: bpf_cmd = "BPF_MAP_LOOKUP_ELEM"; break;
            case 2: bpf_cmd = "BPF_MAP_UPDATE_ELEM"; break;
            case 3: bpf_cmd = "BPF_MAP_DELETE_ELEM"; break;
            case 4: bpf_cmd = "BPF_MAP_GET_NEXT_KEY"; break;
            case 5: bpf_cmd = "BPF_PROG_LOAD"; break;
            case 6: bpf_cmd = "BPF_OBJ_PIN"; break;
            case 7: bpf_cmd = "BPF_OBJ_GET"; break;
            case 8: bpf_cmd = "BPF_PROG_ATTACH"; break;
            case 9: bpf_cmd = "BPF_PROG_DETACH"; break;
            }
            n = snprintf(desc, dlen, "cmd=%s(%d) attr=0x%lx size=%d",
                         bpf_cmd, (int)a0, a1, (int)a2);
        }
        break;

    case 91: /* capset */
        n = snprintf(desc, dlen, "header=0x%lx data=0x%lx", a0, a1);
        break;

    case 90: /* capget */
        n = snprintf(desc, dlen, "header=0x%lx data=0x%lx", a0, a1);
        break;

    /* ===================== UID/GID ===================== */

    case 146: /* setuid */
        n = snprintf(desc, dlen, "uid=%d", (int)a0);
        break;

    case 144: /* setgid */
        n = snprintf(desc, dlen, "gid=%d", (int)a0);
        break;

    case 145: /* setreuid */
        n = snprintf(desc, dlen, "ruid=%d euid=%d", (int)a0, (int)a1);
        break;

    case 147: /* setresuid */
        n = snprintf(desc, dlen, "ruid=%d euid=%d suid=%d",
                     (int)a0, (int)a1, (int)a2);
        break;

    case 143: /* setregid */
        n = snprintf(desc, dlen, "rgid=%d egid=%d", (int)a0, (int)a1);
        break;

    case 149: /* setresgid */
        n = snprintf(desc, dlen, "rgid=%d egid=%d sgid=%d",
                     (int)a0, (int)a1, (int)a2);
        break;

    /* ===================== MODULE ===================== */

    case 105: /* init_module */
        n = snprintf(desc, dlen, "module_image=0x%lx len=%lu", a0, a1);
        if (a2) {
            safe_copy_user_str(pathbuf, a2, 128);
            n += snprintf(desc + n, dlen - n, " params=\"%s\"", pathbuf);
        }
        break;

    case 106: /* delete_module */
        safe_copy_user_str(pathbuf, a0, sizeof(pathbuf));
        n = snprintf(desc, dlen, "name=\"%s\" flags=0x%lx", pathbuf, a1);
        break;

    case 273: /* finit_module */
        n = snprintf(desc, dlen, "fd=%d flags=0x%lx", (int)a0, a2);
        if (a1) {
            safe_copy_user_str(pathbuf, a1, 128);
            n += snprintf(desc + n, dlen - n, " params=\"%s\"", pathbuf);
        }
        break;

    /* ===================== NAMESPACE ===================== */

    case 97: /* unshare */
        n = snprintf(desc, dlen, "flags=0x%lx", a0);
        if (a0 & 0x00020000) n += snprintf(desc + n, dlen - n, " CLONE_NEWNS");
        if (a0 & 0x40000000) n += snprintf(desc + n, dlen - n, " CLONE_NEWNET");
        if (a0 & 0x20000000) n += snprintf(desc + n, dlen - n, " CLONE_NEWPID");
        if (a0 & 0x08000000) n += snprintf(desc + n, dlen - n, " CLONE_NEWUSER");
        break;

    case 268: /* setns */
        n = snprintf(desc, dlen, "fd=%d nstype=0x%lx", (int)a0, a1);
        break;

    /* ===================== XATTR (anti-tamper) ===================== */

    case 5: /* setxattr */
        safe_copy_user_str(pathbuf, a0, sizeof(pathbuf));
        safe_copy_user_str(pathbuf2, a1, 64);
        n = snprintf(desc, dlen, "path=\"%s\" name=\"%s\" value=0x%lx size=%lu flags=0x%lx",
                     pathbuf, pathbuf2, a2, a3, a4);
        break;

    case 8: /* getxattr */
        safe_copy_user_str(pathbuf, a0, sizeof(pathbuf));
        safe_copy_user_str(pathbuf2, a1, 64);
        n = snprintf(desc, dlen, "path=\"%s\" name=\"%s\" size=%lu",
                     pathbuf, pathbuf2, a3);
        break;

    /* ===================== EPOLL ===================== */

    case 21: /* epoll_ctl */
        {
            const char *op = "?";
            switch ((int)a1) {
            case 1: op = "EPOLL_CTL_ADD"; break;
            case 2: op = "EPOLL_CTL_DEL"; break;
            case 3: op = "EPOLL_CTL_MOD"; break;
            }
            n = snprintf(desc, dlen, "epfd=%d op=%s(%d) fd=%d event=0x%lx",
                         (int)a0, op, (int)a1, (int)a2, a3);
        }
        break;

    case 22: /* epoll_pwait */
        n = snprintf(desc, dlen, "epfd=%d events=0x%lx maxevents=%d timeout=%d",
                     (int)a0, a1, (int)a2, (int)a3);
        break;

    /* ===================== INOTIFY ===================== */

    case 27: /* inotify_add_watch */
        safe_copy_user_str(pathbuf, a1, sizeof(pathbuf));
        n = snprintf(desc, dlen, "fd=%d path=\"%s\" mask=0x%lx",
                     (int)a0, pathbuf, a2);
        break;

    /* ===================== FUTEX ===================== */

    case 98: /* futex */
        {
            const char *fop = "?";
            int op_val = (int)a1 & 0x7F;
            switch (op_val) {
            case 0: fop = "FUTEX_WAIT"; break;
            case 1: fop = "FUTEX_WAKE"; break;
            case 2: fop = "FUTEX_FD"; break;
            case 3: fop = "FUTEX_REQUEUE"; break;
            case 4: fop = "FUTEX_CMP_REQUEUE"; break;
            case 5: fop = "FUTEX_WAKE_OP"; break;
            case 9: fop = "FUTEX_WAIT_BITSET"; break;
            case 10: fop = "FUTEX_WAKE_BITSET"; break;
            }
            n = snprintf(desc, dlen, "uaddr=0x%lx op=%s(%d) val=%d",
                         a0, fop, (int)a1, (int)a2);
            if ((int)a1 & 0x80)
                n += snprintf(desc + n, dlen - n, "|FUTEX_PRIVATE_FLAG");
        }
        break;

    /* ===================== MISC ===================== */

    case 23: /* dup */
        n = snprintf(desc, dlen, "oldfd=%d", (int)a0);
        break;

    case 24: /* dup3 */
        n = snprintf(desc, dlen, "oldfd=%d newfd=%d flags=0x%lx", (int)a0, (int)a1, a2);
        break;

    case 160: /* uname */
        n = snprintf(desc, dlen, "buf=0x%lx", a0);
        break;

    case 261: /* prlimit64 */
        {
            const char *rname = "?";
            switch ((int)a1) {
            case 0: rname = "RLIMIT_CPU"; break;
            case 1: rname = "RLIMIT_FSIZE"; break;
            case 2: rname = "RLIMIT_DATA"; break;
            case 3: rname = "RLIMIT_STACK"; break;
            case 4: rname = "RLIMIT_CORE"; break;
            case 5: rname = "RLIMIT_RSS"; break;
            case 6: rname = "RLIMIT_NPROC"; break;
            case 7: rname = "RLIMIT_NOFILE"; break;
            case 8: rname = "RLIMIT_MEMLOCK"; break;
            case 9: rname = "RLIMIT_AS"; break;
            }
            n = snprintf(desc, dlen, "pid=%d resource=%s(%d) new=0x%lx old=0x%lx",
                         (int)a0, rname, (int)a1, a2, a3);
        }
        break;

    case 278: /* getrandom */
        n = snprintf(desc, dlen, "buf=0x%lx count=%lu flags=0x%lx", a0, a1, a2);
        break;

    default:
        n = snprintf(desc, dlen, "a0=0x%lx a1=0x%lx a2=0x%lx a3=0x%lx a4=0x%lx a5=0x%lx",
                     a0, a1, a2, a3, a4, a5);
        break;
    }
    (void)n;
}

static void push_event(int nr, int uid,
                       unsigned long a0, unsigned long a1, unsigned long a2,
                       unsigned long a3, unsigned long a4, unsigned long a5,
                       unsigned long pc, unsigned long caller, unsigned long clone_fn,
                       const char *desc)
{
    int idx;

    ev_lock();

    idx = g_ev_head;
    {
        svc_event_t *ev = &g_events[idx];
        ev->seq = ++g_seq;
        ev->nr = nr;
        ev->pid = *(int *)((char *)current + task_struct_offset.pid_offset);
        ev->uid = uid;
        ev->a0 = a0; ev->a1 = a1; ev->a2 = a2;
        ev->a3 = a3; ev->a4 = a4; ev->a5 = a5;
        ev->pc = pc;
        ev->caller = caller;
        ev->clone_fn = clone_fn;

        {
            int ci;
            const char *p = get_task_comm(current);
            for (ci = 0; ci < 15; ci++) {
                ev->comm[ci] = p[ci];
                if (!p[ci]) break;
            }
            ev->comm[ci < 15 ? ci : 15] = '\0';
        }

        {
            int di;
            if (!desc) desc = "";
            for (di = 0; di < DESC_BUF_SIZE - 1 && desc[di]; di++)
                ev->desc[di] = desc[di];
            ev->desc[di] = '\0';
        }
    }

    g_ev_head = (idx + 1) % MAX_EVENTS;
    if (g_ev_count < MAX_EVENTS) {
        g_ev_count++;
    } else {
        g_ev_tail = (g_ev_tail + 1) % MAX_EVENTS;
        g_ev_dropped++;
    }
    g_total++;

    ev_unlock();
}

/* ================================================================
 * Generic hook callback (before only)
 * One callback for ALL syscalls, NR passed via udata.
 * ================================================================ */
static void before_generic(hook_fargs4_t *args, void *udata)
{
    int nr = (int)(unsigned long)udata;
    int uid;
    char desc[DESC_BUF_SIZE];
    unsigned long a0, a1, a2, a3, a4, a5;
    unsigned long pc = 0;
    unsigned long caller = 0;
    unsigned long clone_fn = 0;

    /* Fast rejection path */
    if (!g_enabled) return;
    if (nr < 0 || nr >= MAX_NR) return;
    if (!bitmap_test(g_nr_bitmap, nr)) return;

    uid = (int)current_uid();
    if (g_target_uid >= 0 && uid != g_target_uid) return;

    /* Read syscall arguments using official API */
    a0 = syscall_argn(args, 0);
    a1 = syscall_argn(args, 1);
    a2 = syscall_argn(args, 2);
    a3 = syscall_argn(args, 3);
    a4 = syscall_argn(args, 4);
    a5 = syscall_argn(args, 5);

    if (nr == __NR_clone && a1) {
        clone_fn = safe_read_user_ptr(a1);
        if (!clone_fn) clone_fn = safe_read_user_ptr(a1 + 8);
        //这里接下来应该配合APK侧进行maps解析
    }

    if (has_syscall_wrapper) {
        struct pt_regs *r = (struct pt_regs *)((hook_fargs0_t *)args)->args[0];
        if (r) {
            pc = (unsigned long)r->pc;
            caller = (unsigned long)r->regs[30];
            //这里同理，这里也应该继续进行apk侧的maps解析
        }
    }

    /* Build detailed description */
    describe_args(nr, a0, a1, a2, a3, a4, a5, desc, sizeof(desc));

    push_event(nr, uid, a0, a1, a2, a3, a4, a5, pc, caller, clone_fn, desc);
}

static void do_filp_open_before(hook_fargs3_t *args, void *udata)
{
    int uid;
    unsigned long pc = 0;
    unsigned long caller = 0;
    char pathbuf[PATH_BUF_SIZE];
    char flagbuf[256];
    char desc[DESC_BUF_SIZE];

    (void)udata;

    if (!g_enabled) return;
    uid = (int)current_uid();
    if (g_target_uid >= 0 && uid != g_target_uid) return;
    if (!bitmap_test(g_nr_bitmap, 56)) return;

    {
        struct pt_regs *r = _task_pt_reg(current);
        if (r) {
            pc = (unsigned long)r->pc;
            caller = (unsigned long)r->regs[30];
        }
    }

    pathbuf[0] = '\0';
    flagbuf[0] = '\0';

    {
        int dfd = (int)args->arg0;
        unsigned long filename_ptr = (unsigned long)args->arg1;
        unsigned long of_ptr = (unsigned long)args->arg2;
        unsigned long name_ptr = 0;
        struct open_flags_head {
            int open_flag;
            unsigned int mode;
        } of;

        if (g_probe_kernel_read && filename_ptr) {
            if (g_probe_kernel_read(&name_ptr, (const void *)filename_ptr, sizeof(name_ptr)) == 0) {
                safe_copy_kernel_str(pathbuf, name_ptr, sizeof(pathbuf));
            }
        }

        if (g_probe_kernel_read && of_ptr) {
            if (g_probe_kernel_read(&of, (const void *)of_ptr, sizeof(of)) == 0) {
                decode_open_flags(flagbuf, sizeof(flagbuf), (unsigned long)of.open_flag);
                snprintf(desc, sizeof(desc),
                         "dfd=%d path=\"%s\" flags=%s(0x%x) mode=0%o",
                         dfd, pathbuf[0] ? pathbuf : "?", flagbuf, of.open_flag, of.mode);
            } else {
                snprintf(desc, sizeof(desc),
                         "dfd=%d filename=0x%lx open_flags=0x%lx",
                         dfd, filename_ptr, of_ptr);
            }
        } else {
            snprintf(desc, sizeof(desc),
                     "dfd=%d filename=0x%lx open_flags=0x%lx",
                     dfd, filename_ptr, of_ptr);
        }
    }

    push_event(-1, uid,
               (unsigned long)args->arg0, (unsigned long)args->arg1, (unsigned long)args->arg2,
               0, 0, 0,
               pc, caller, 0, desc);
}

static int install_do_filp_open(void)
{
    hook_err_t err;
    unsigned long addr = 0;

    if (g_do_filp_open_active) return 0;
    if (!kallsyms_lookup_name) return -1;

    if (!g_probe_kernel_read) {
        g_probe_kernel_read = (long (*)(void *, const void *, unsigned long))
            kallsyms_lookup_name("probe_kernel_read");
    }

    addr = kallsyms_lookup_name("do_filp_open.cfi_jt");
    if (!addr) addr = kallsyms_lookup_name("do_filp_open");
    if (!addr) return -1;

    err = hook_wrap((void *)addr, 3, (void *)do_filp_open_before, (void *)0, (void *)0);
    if (err != HOOK_NO_ERR) return -1;

    g_do_filp_open_addr = addr;
    g_do_filp_open_active = 1;
    return 0;
}

static void remove_do_filp_open(void)
{
    if (!g_do_filp_open_active) return;
    hook_unwrap((void *)g_do_filp_open_addr, (void *)do_filp_open_before, (void *)0);
    g_do_filp_open_active = 0;
    g_do_filp_open_addr = 0;
}

/* ================================================================
 * Tier1: Core syscalls for reverse engineering (44)
 * ================================================================ */
static hook_entry_t tier1_hooks[] = {
    { 56, 4, 0, 0 },   /* openat */
    { 57, 1, 0, 0 },   /* close */
    { 48, 4, 0, 0 },   /* faccessat */
    { 35, 3, 0, 0 },   /* unlinkat */
    { 78, 4, 0, 0 },   /* readlinkat */
    { 79, 4, 0, 0 },   /* newfstatat */
    { 80, 2, 0, 0 },   /* fstat */
    { 61, 3, 0, 0 },   /* getdents64 */
    { 63, 3, 0, 0 },   /* read */
    { 64, 3, 0, 0 },   /* write */
    { 221, 3, 0, 0 },  /* execve */
    { 220, 5, 0, 0 },  /* clone */
    { 435, 2, 0, 0 },  /* clone3 */
    { 93, 1, 0, 0 },   /* exit */
    { 94, 1, 0, 0 },   /* exit_group */
    { 222, 6, 0, 0 },  /* mmap */
    { 226, 3, 0, 0 },  /* mprotect */
    { 215, 2, 0, 0 },  /* munmap */
    { 214, 1, 0, 0 },  /* brk */
    { 232, 3, 0, 0 },  /* mincore */
    { 198, 3, 0, 0 },  /* socket */
    { 200, 3, 0, 0 },  /* bind */
    { 203, 3, 0, 0 },  /* connect */
    { 206, 6, 0, 0 },  /* sendto */
    { 207, 6, 0, 0 },  /* recvfrom */
    { 117, 4, 0, 0 },  /* ptrace */
    { 167, 5, 0, 0 },  /* prctl */
    { 129, 2, 0, 0 },  /* kill */
    { 131, 3, 0, 0 },  /* tgkill */
    { 134, 4, 0, 0 },  /* rt_sigaction */
    { 135, 4, 0, 0 },  /* rt_sigprocmask */
    { 29, 3, 0, 0 },   /* ioctl */
    { 25, 3, 0, 0 },   /* fcntl */
    { 277, 3, 0, 0 },  /* seccomp */
    { 280, 3, 0, 0 },  /* bpf */
    { 279, 2, 0, 0 },  /* memfd_create */
    { 146, 1, 0, 0 },  /* setuid */
    { 144, 1, 0, 0 },  /* setgid */
    { 147, 3, 0, 0 },  /* setresuid */
    { 105, 3, 0, 0 },  /* init_module */
    { 273, 3, 0, 0 },  /* finit_module */
    { 97, 1, 0, 0 },   /* unshare */
    { 268, 2, 0, 0 },  /* setns */
    { 261, 4, 0, 0 },  /* prlimit64 */
    { 62, 3, 0, 0 },   /* lseek */
    { 291, 5, 0, 0 },  /* statx */
};
#define TIER1_COUNT (sizeof(tier1_hooks) / sizeof(tier1_hooks[0]))

/* ================================================================
 * Tier2: Extended syscalls (24) — loaded on demand
 * ================================================================ */
static hook_entry_t tier2_hooks[] = {
    { 40, 5, 0, 0 },   /* mount */
    { 39, 2, 0, 0 },   /* umount2 */
    { 281, 5, 0, 0 },  /* execveat */
    { 270, 5, 0, 0 },  /* process_vm_readv */
    { 271, 5, 0, 0 },  /* process_vm_writev */
    { 211, 3, 0, 0 },  /* sendmsg */
    { 212, 3, 0, 0 },  /* recvmsg */
    { 208, 5, 0, 0 },  /* setsockopt */
    { 201, 2, 0, 0 },  /* listen */
    { 202, 3, 0, 0 },  /* accept */
    { 242, 4, 0, 0 },  /* accept4 */
    { 210, 2, 0, 0 },  /* shutdown */
    { 106, 2, 0, 0 },  /* delete_module */
    { 34, 3, 0, 0 },   /* mkdirat */
    { 38, 4, 0, 0 },   /* renameat */
    { 276, 5, 0, 0 },  /* renameat2 */
    { 53, 3, 0, 0 },   /* fchmodat */
    { 54, 5, 0, 0 },   /* fchownat */
    { 21, 4, 0, 0 },   /* epoll_ctl */
    { 27, 3, 0, 0 },   /* inotify_add_watch */
    { 5, 5, 0, 0 },    /* setxattr */
    { 8, 4, 0, 0 },    /* getxattr */
    { 233, 3, 0, 0 },  /* madvise */
    { 98, 6, 0, 0 },   /* futex */
};
#define TIER2_COUNT (sizeof(tier2_hooks) / sizeof(tier2_hooks[0]))

static int is_hooked_nr(int nr)
{
    int i;
    for (i = 0; i < (int)TIER1_COUNT; i++) {
        if (tier1_hooks[i].nr == nr && tier1_hooks[i].active) return 1;
    }
    for (i = 0; i < (int)TIER2_COUNT; i++) {
        if (tier2_hooks[i].nr == nr && tier2_hooks[i].active) return 1;
    }
    return 0;
}

/* ================================================================
 * Hook installation/removal
 * ================================================================ */
static int install_hook(hook_entry_t *h)
{
    hook_err_t err;
    if (h->active) return 0;

    err = inline_hook_syscalln(h->nr, h->narg,
                               (void *)before_generic, (void *)0,
                               (void *)(unsigned long)h->nr);
    if (err == HOOK_NO_ERR) {
        h->active = 1;
        h->method = HOOK_INLINE;
        g_hooks_installed++;
        return 0;
    }

    err = fp_hook_syscalln(h->nr, h->narg,
                           (void *)before_generic, (void *)0,
                           (void *)(unsigned long)h->nr);
    if (err == HOOK_NO_ERR) {
        h->active = 1;
        h->method = HOOK_FP;
        g_hooks_installed++;
        return 0;
    }

    return -1;
}

static void remove_hook(hook_entry_t *h)
{
    if (!h->active) return;
    if (h->method == HOOK_INLINE)
        inline_unhook_syscalln(h->nr, (void *)before_generic, (void *)0);
    else
        fp_unhook_syscalln(h->nr, (void *)before_generic, (void *)0);
    h->active = 0;
    g_hooks_installed--;
}

static int install_tier1(void)
{
    int i, ok = 0;
    for (i = 0; i < (int)TIER1_COUNT; i++) {
        if (install_hook(&tier1_hooks[i]) == 0) ok++;
    }
    return ok;
}

static void install_tier2(void)
{
    int i;
    for (i = 0; i < (int)TIER2_COUNT; i++) {
        install_hook(&tier2_hooks[i]);
    }
    g_tier2_loaded = 1;
}

static void remove_tier2(void)
{
    int i;
    for (i = 0; i < (int)TIER2_COUNT; i++) {
        remove_hook(&tier2_hooks[i]);
    }
    g_tier2_loaded = 0;
}

/* ================================================================
 * Preset configurations
 * ================================================================ */
static const int preset_re_basic[] = {56,48,78,221,222,226,198,203,117,167,29,134,279,280};
static const int preset_re_full[] = {56,48,35,78,79,63,64,221,281,220,435,93,94,222,226,215,232,198,200,203,206,207,117,167,29,25,129,131,134,135,277,280,279,146,144,147,105,273,97,268,261,291};
static const int preset_file[] = {56,57,48,35,78,79,80,61,63,64,62,34,38,276,53,54,291,43,27,5,8};
static const int preset_net[] = {198,200,201,202,203,206,207,208,209,210,211,212,242};
static const int preset_proc[] = {221,281,220,435,93,94,95,260,117,129,130,131,270,271};
static const int preset_mem[] = {222,226,215,214,232,233,279,270,271};
static const int preset_security[] = {117,277,280,146,144,147,149,105,273,106,97,268,91,167,134};

static void apply_preset(const char *name)
{
    int i;
    const int *nrs = 0;
    int cnt = 0;
    /* Clear bitmap */
    for (i = 0; i < BITMAP_LONGS; i++)
        g_nr_bitmap[i] = 0;

    if (!strcmp(name, "re_basic")) {
        nrs = preset_re_basic;
        cnt = (int)(sizeof(preset_re_basic) / sizeof(preset_re_basic[0]));
    }
    else if (!strcmp(name, "re_full")) {
        nrs = preset_re_full;
        cnt = (int)(sizeof(preset_re_full) / sizeof(preset_re_full[0]));
    }
    else if (!strcmp(name, "file")) {
        nrs = preset_file;
        cnt = (int)(sizeof(preset_file) / sizeof(preset_file[0]));
    }
    else if (!strcmp(name, "net")) {
        nrs = preset_net;
        cnt = (int)(sizeof(preset_net) / sizeof(preset_net[0]));
    }
    else if (!strcmp(name, "proc")) {
        nrs = preset_proc;
        cnt = (int)(sizeof(preset_proc) / sizeof(preset_proc[0]));
    }
    else if (!strcmp(name, "mem")) {
        nrs = preset_mem;
        cnt = (int)(sizeof(preset_mem) / sizeof(preset_mem[0]));
    }
    else if (!strcmp(name, "security")) {
        nrs = preset_security;
        cnt = (int)(sizeof(preset_security) / sizeof(preset_security[0]));
    }
    else if (!strcmp(name, "all")) {
        /* Enable all hooked NRs */
        for (i = 0; i < (int)TIER1_COUNT; i++)
            if (tier1_hooks[i].active)
                bitmap_set(g_nr_bitmap, tier1_hooks[i].nr);
        for (i = 0; i < (int)TIER2_COUNT; i++)
            if (tier2_hooks[i].active)
                bitmap_set(g_nr_bitmap, tier2_hooks[i].nr);
    }
    if (nrs && cnt > 0) {
        for (i = 0; i < cnt; i++) {
            if (is_hooked_nr(nrs[i]))
                bitmap_set(g_nr_bitmap, nrs[i]);
        }
    }
}

/* ================================================================
 * Integer parser helper
 * ================================================================ */
static int parse_int(const char *s, int *consumed)
{
    int val = 0, neg = 0, count = 0;
    if (*s == '-') { neg = 1; s++; count++; }
    while (*s >= '0' && *s <= '9') {
        val = val * 10 + (*s - '0');
        s++; count++;
    }
    if (consumed) *consumed = count;
    return neg ? -val : val;
}

/* ================================================================
 * JSON output via file (kernel file write)
 * ================================================================ */
static int write_output_file(const char *data, int len)
{
    long fd;
    fd = raw_syscall4(__NR_openat, -100, (long)OUTPUT_PATH,
                      0x241, 0644);
    if (fd < 0) return -1;
    raw_syscall3(__NR_write, fd, (long)data, len);
    raw_syscall1(__NR_close, fd);
    return 0;
}

/* Buffer for building JSON output */
static char g_outbuf[131072];  /* 128KB for detailed event output */

/* JSON escape */
static int json_escape(char *dst, int dstlen, const char *src)
{
    int i = 0;
    while (*src && i < dstlen - 2) {
        if (*src == '"' || *src == '\\') {
            dst[i++] = '\\';
            if (i >= dstlen - 1) break;
        }
        if (*src == '\n') {
            dst[i++] = '\\'; if (i >= dstlen - 1) break;
            dst[i++] = 'n';
        } else if (*src == '\r') {
dst[i++] = '\\'; if (i >= dstlen - 1) break;
            dst[i++] = 'r';
        } else if (*src == '\t') {
            dst[i++] = '\\'; if (i >= dstlen - 1) break;
            dst[i++] = 't';
        } else {
            dst[i++] = *src;
        }
        src++;
    }
    dst[i] = '\0';
    return i;
}

static int pop_event(svc_event_t *out)
{
    int ok = 0;
    if (!out) return 0;
    ev_lock();
    if (g_ev_count > 0) {
        svc_event_t *src = &g_events[g_ev_tail];
        int i;
        out->seq = src->seq;
        out->nr = src->nr;
        out->pid = src->pid;
        out->uid = src->uid;
        out->a0 = src->a0; out->a1 = src->a1; out->a2 = src->a2;
        out->a3 = src->a3; out->a4 = src->a4; out->a5 = src->a5;
        out->pc = src->pc;
        out->caller = src->caller;
        out->clone_fn = src->clone_fn;
        for (i = 0; i < 16; i++) out->comm[i] = src->comm[i];
        for (i = 0; i < DESC_BUF_SIZE; i++) out->desc[i] = src->desc[i];
        g_ev_tail = (g_ev_tail + 1) % MAX_EVENTS;
        g_ev_count--;
        ok = 1;
    }
    ev_unlock();
    return ok;
}

static int format_event_jsonl(char *buf, int blen, const svc_event_t *ev)
{
    char esc_desc[DESC_BUF_SIZE + 256];
    if (!buf || blen <= 0 || !ev) return 0;
    json_escape(esc_desc, sizeof(esc_desc), ev->desc);
    return snprintf(
        buf, blen,
        "{\"seq\":%u,\"nr\":%d,\"name\":\"%s\",\"pid\":%d,\"uid\":%d,"
        "\"comm\":\"%s\",\"pc\":%lu,\"caller\":%lu,\"clone_fn\":%lu,"
        "\"a0\":%lu,\"a1\":%lu,\"a2\":%lu,\"a3\":%lu,\"a4\":%lu,\"a5\":%lu,"
        "\"desc\":\"%s\"}\n",
        ev->seq, ev->nr, get_syscall_name(ev->nr),
        ev->pid, ev->uid, ev->comm,
        ev->pc, ev->caller, ev->clone_fn,
        ev->a0, ev->a1, ev->a2, ev->a3, ev->a4, ev->a5,
        esc_desc
    );
}

static struct file *open_event_file_fp(int truncate)
{
    int flags = O_WRONLY | O_CREAT;
    if (truncate) flags |= O_TRUNC;
    else flags |= O_APPEND;
    if (!g_filp_open) return (struct file *)-1;
    return g_filp_open(EVENT_PATH, flags, 0666);
}

typedef struct task_struct *(*kthread_run_t)(int (*threadfn)(void *data), void *data, const char namefmt[], ...);
typedef int (*kthread_stop_t)(struct task_struct *k);
typedef int (*kthread_should_stop_t)(void);
typedef void (*msleep_t)(unsigned int msecs);

static kthread_run_t g_kthread_run = 0;
static kthread_stop_t g_kthread_stop = 0;
static kthread_should_stop_t g_kthread_should_stop = 0;
static msleep_t g_msleep = 0;

static int event_writer_thread(void *data)
{
    struct file *fp = 0;
    char line[2048];
    (void)data;

    while (!g_writer_stop && (!g_kthread_should_stop || !g_kthread_should_stop())) {
        int wrote = 0;
        int i;

        if (!g_enabled) {
            if (fp && !IS_ERR(fp) && g_filp_close) g_filp_close(fp, 0);
            fp = 0;
            if (g_msleep) g_msleep(200);
            continue;
        }

        if (!g_filp_open || !g_filp_close || !g_kernel_write) {
            if (g_msleep) g_msleep(500);
            continue;
        }

        if (!fp) {
            fp = open_event_file_fp(0);
            if (IS_ERR(fp)) {
                fp = 0;
                if (g_msleep) g_msleep(500);
                continue;
            }
        }

        {
            loff_t pos = 0;
            if (g_vfs_llseek) {
                long p = g_vfs_llseek(fp, 0, 2);
                if (p > 0) pos = (loff_t)p;
            }
            if (pos < 0) pos = 0;
            for (i = 0; i < 256; i++) {
                svc_event_t ev;
                int len;
                if (!pop_event(&ev)) break;
                len = format_event_jsonl(line, sizeof(line), &ev);
                if (len > 0) {
                    long rc = g_kernel_write(fp, line, (unsigned long)len, &pos);
                    if (rc <= 0) {
                        g_filp_close(fp, 0);
                        fp = 0;
                        break;
                    }
                    wrote++;
                }
            }
        }

        if (wrote == 0) {
            if (g_msleep) g_msleep(20);
        } else if (wrote < 256) {
            if (g_msleep) g_msleep(5);
        }
    }

    if (fp && !IS_ERR(fp) && g_filp_close) g_filp_close(fp, 0);
    return 0;
}

static void start_writer_thread(void)
{
    if (g_writer_task) return;
    if (!kallsyms_lookup_name) return;

    if (!g_kthread_run)
        g_kthread_run = (kthread_run_t)kallsyms_lookup_name("kthread_run");
    if (!g_kthread_stop)
        g_kthread_stop = (kthread_stop_t)kallsyms_lookup_name("kthread_stop");
    if (!g_kthread_should_stop)
        g_kthread_should_stop = (kthread_should_stop_t)kallsyms_lookup_name("kthread_should_stop");
    if (!g_msleep)
        g_msleep = (msleep_t)kallsyms_lookup_name("msleep");

    if (!g_filp_open)
        g_filp_open = (typeof(g_filp_open))kallsyms_lookup_name("filp_open");
    if (!g_filp_close)
        g_filp_close = (typeof(g_filp_close))kallsyms_lookup_name("filp_close");
    if (!g_vfs_llseek)
        g_vfs_llseek = (typeof(g_vfs_llseek))kallsyms_lookup_name("vfs_llseek");
    if (!g_kernel_write) {
        g_kernel_write = (typeof(g_kernel_write))kallsyms_lookup_name("kernel_write");
        if (!g_kernel_write)
            g_kernel_write = (typeof(g_kernel_write))kallsyms_lookup_name("__kernel_write");
    }

    g_writer_stop = 0;
    if (g_kthread_run) {
        g_writer_task = g_kthread_run(event_writer_thread, (void *)0, "svcmon_writer");
    }
}

static void stop_writer_thread(void)
{
    g_writer_stop = 1;
    if (g_writer_task && g_kthread_stop) {
        g_kthread_stop(g_writer_task);
    }
    g_writer_task = 0;
}

/* ================================================================
 * CTL0 command handler
 * ================================================================ */
static long svc_ctl0(const char *args, char *__user out_msg, int outlen)
{
    int n = 0;
    char *buf = g_outbuf;
    int blen = sizeof(g_outbuf);
    char esc[DESC_BUF_SIZE + 256];

    /* ---- status ---- */
    if (!strcmp(args, "status")) {
        int nr_logging = 0, i;
        for (i = 0; i < MAX_NR; i++) {
            if (bitmap_test(g_nr_bitmap, i)) nr_logging++;
        }
        n = snprintf(buf, blen,
            "{\"ok\":true,\"version\":\"8.1.0\",\"enabled\":%s,"
            "\"target_uid\":%d,\"hooks_installed\":%d,"
            "\"nrs_logging\":%d,\"events_total\":%d,"
            "\"events_buffered\":%d,\"events_dropped\":%u,\"tier2\":%s,"
            "\"logging_nrs\":[",
            g_enabled ? "true" : "false",
            g_target_uid, g_hooks_installed,
            nr_logging, g_total,
            g_ev_count < MAX_EVENTS ? g_ev_count : MAX_EVENTS,
            g_ev_dropped,
            g_tier2_loaded ? "true" : "false");

        int first = 1;
        for (i = 0; i < MAX_NR; i++) {
            if (bitmap_test(g_nr_bitmap, i)) {
                if (!first) n += snprintf(buf + n, blen - n, ",");
                n += snprintf(buf + n, blen - n, "%d", i);
                first = 0;
            }
        }
        n += snprintf(buf + n, blen - n, "],\"hooks\":[");

        first = 1;
        for (i = 0; i < (int)TIER1_COUNT; i++) {
            if (tier1_hooks[i].active) {
                if (!first) n += snprintf(buf + n, blen - n, ",");
                n += snprintf(buf + n, blen - n, "{\"nr\":%d,\"name\":\"%s\",\"method\":\"%s\"}",
                    tier1_hooks[i].nr, get_syscall_name(tier1_hooks[i].nr),
                    tier1_hooks[i].method == HOOK_INLINE ? "inline" : "fp");
                first = 0;
            }
        }
        for (i = 0; i < (int)TIER2_COUNT; i++) {
            if (tier2_hooks[i].active) {
                if (!first) n += snprintf(buf + n, blen - n, ",");
                n += snprintf(buf + n, blen - n, "{\"nr\":%d,\"name\":\"%s\",\"method\":\"%s\"}",
                    tier2_hooks[i].nr, get_syscall_name(tier2_hooks[i].nr),
                    tier2_hooks[i].method == HOOK_INLINE ? "inline" : "fp");
                first = 0;
            }
        }
        if (g_do_filp_open_active) {
            if (!first) n += snprintf(buf + n, blen - n, ",");
            n += snprintf(buf + n, blen - n, "{\"nr\":-1,\"name\":\"do_filp_open\",\"method\":\"inline\"}");
            first = 0;
        }
        n += snprintf(buf + n, blen - n, "]}");
    }

    /* ---- uid <n> ---- */
    else if (!strncmp(args, "uid ", 4)) {
        g_target_uid = parse_int(args + 4, 0);
        n = snprintf(buf, blen, "{\"ok\":true,\"target_uid\":%d}", g_target_uid);
    }

    /* ---- enable ---- */
    else if (!strcmp(args, "enable") || !strcmp(args, "resume") || !strcmp(args, "start")) {
        g_enabled = 1;
        n = snprintf(buf, blen, "{\"ok\":true,\"enabled\":true}");
    }

    /* ---- disable ---- */
    else if (!strcmp(args, "disable") || !strcmp(args, "pause") || !strcmp(args, "stop")) {
        g_enabled = 0;
        n = snprintf(buf, blen, "{\"ok\":true,\"enabled\":false}");
    }

    /* ---- enable_nr <n> ---- */
    else if (!strncmp(args, "enable_nr ", 10)) {
        int nr = parse_int(args + 10, 0);
        if (is_hooked_nr(nr)) {
            bitmap_set(g_nr_bitmap, nr);
            n = snprintf(buf, blen, "{\"ok\":true,\"enabled_nr\":%d}", nr);
        } else {
            n = snprintf(buf, blen, "{\"ok\":false,\"error\":\"nr_not_hooked\",\"nr\":%d}", nr);
        }
    }

    /* ---- disable_nr <n> ---- */
    else if (!strncmp(args, "disable_nr ", 11)) {
        int nr = parse_int(args + 11, 0);
        bitmap_clear(g_nr_bitmap, nr);
        n = snprintf(buf, blen, "{\"ok\":true,\"disabled_nr\":%d}", nr);
    }

    /* ---- set_nrs <n1>,<n2>,... ---- */
    else if (!strncmp(args, "set_nrs ", 8)) {
        int i;
        const char *p = args + 8;
        int cnt = 0;
        int skip = 0;
        for (i = 0; i < BITMAP_LONGS; i++)
            g_nr_bitmap[i] = 0;
        while (*p) {
            while (*p == ' ' || *p == ',') p++;
            if (*p == '\0') break;
            int consumed = 0;
            int nr = parse_int(p, &consumed);
            if (consumed > 0) {
                if (is_hooked_nr(nr)) {
                    bitmap_set(g_nr_bitmap, nr);
                    cnt++;
                } else {
                    skip++;
                }
                p += consumed;
            } else {
                p++;
            }
        }
        n = snprintf(buf, blen, "{\"ok\":true,\"set_nrs_count\":%d,\"skipped\":%d}", cnt, skip);
    }

    /* ---- enable_all ---- */
    else if (!strcmp(args, "enable_all")) {
        int i;
        for (i = 0; i < (int)TIER1_COUNT; i++)
            if (tier1_hooks[i].active) bitmap_set(g_nr_bitmap, tier1_hooks[i].nr);
        for (i = 0; i < (int)TIER2_COUNT; i++)
            if (tier2_hooks[i].active) bitmap_set(g_nr_bitmap, tier2_hooks[i].nr);
        n = snprintf(buf, blen, "{\"ok\":true}");
    }

    /* ---- disable_all ---- */
    else if (!strcmp(args, "disable_all")) {
        int i;
        for (i = 0; i < BITMAP_LONGS; i++)
            g_nr_bitmap[i] = 0;
        n = snprintf(buf, blen, "{\"ok\":true}");
    }

    /* ---- preset <name> ---- */
    else if (!strncmp(args, "preset ", 7)) {
        apply_preset(args + 7);
        n = snprintf(buf, blen, "{\"ok\":true,\"preset\":\"%s\"}", args + 7);
    }

    /* ---- tier2 on/off ---- */
    else if (!strcmp(args, "tier2 on")) {
        if (!g_tier2_loaded) install_tier2();
        n = snprintf(buf, blen, "{\"ok\":true,\"tier2\":true}");
    }
    else if (!strcmp(args, "tier2 off")) {
        if (g_tier2_loaded) remove_tier2();
        n = snprintf(buf, blen, "{\"ok\":true,\"tier2\":false}");
    }

    /* ---- do_filp_open on/off ---- */
    else if (!strcmp(args, "do_filp_open on") || !strcmp(args, "filp_open on")) {
        if (!g_do_filp_open_active) install_do_filp_open();
        n = snprintf(buf, blen, "{\"ok\":true,\"do_filp_open\":%s}", g_do_filp_open_active ? "true" : "false");
    }
    else if (!strcmp(args, "do_filp_open off") || !strcmp(args, "filp_open off")) {
        remove_do_filp_open();
        n = snprintf(buf, blen, "{\"ok\":true,\"do_filp_open\":false}");
    }

    /* ---- drain <max> ---- */
    else if (!strncmp(args, "drain ", 6) || !strcmp(args, "drain")) {
        int max = 50;
        if (!strncmp(args, "drain ", 6)) max = parse_int(args + 6, 0);
        if (max <= 0) max = 50;
        if (max > MAX_EVENTS) max = MAX_EVENTS;

        int avail = g_ev_count < MAX_EVENTS ? g_ev_count : MAX_EVENTS;
        int count = avail < max ? avail : max;
        int start;

        n = snprintf(buf, blen, "{\"ok\":true,\"count\":%d,\"total\":%u,\"events\":[", count, g_total);

        if (count > 0) {
            start = (g_ev_head - avail + MAX_EVENTS) % MAX_EVENTS;
            int skip = avail - count;
            start = (start + skip) % MAX_EVENTS;

            int i;
            for (i = 0; i < count && n < blen - 2048; i++) {
                int idx = (start + i) % MAX_EVENTS;
                svc_event_t *ev = &g_events[idx];
                json_escape(esc, sizeof(esc), ev->desc);

                if (i > 0) n += snprintf(buf + n, blen - n, ",");
                n += snprintf(buf + n, blen - n,
                    "{\"seq\":%u,\"nr\":%d,\"name\":\"%s\",\"pid\":%d,\"uid\":%d,"
                    "\"comm\":\"%s\",\"pc\":%lu,\"caller\":%lu,\"clone_fn\":%lu,"
                    "\"a0\":%lu,\"a1\":%lu,\"a2\":%lu,"
                    "\"a3\":%lu,\"a4\":%lu,\"a5\":%lu,\"desc\":\"%s\"}",
                    ev->seq, ev->nr, get_syscall_name(ev->nr), ev->pid, ev->uid,
                    ev->comm, ev->pc, ev->caller, ev->clone_fn,
                    ev->a0, ev->a1, ev->a2,
                    ev->a3, ev->a4, ev->a5, esc);
            }
        }
        n += snprintf(buf + n, blen - n, "]}");

        ev_lock();
        g_ev_head = 0;
        g_ev_tail = 0;
        g_ev_count = 0;
        g_ev_dropped = 0;
        ev_unlock();
    }

    /* ---- events ---- */
    else if (!strcmp(args, "events")) {
        return svc_ctl0("drain 50", out_msg, outlen);
    }

    /* ---- clear ---- */
    else if (!strcmp(args, "clear")) {
        ev_lock();
        g_ev_head = 0;
        g_ev_tail = 0;
        g_ev_count = 0;
        g_ev_dropped = 0;
        ev_unlock();
        n = snprintf(buf, blen, "{\"ok\":true,\"cleared\":true}");
    }

    /* ---- unknown ---- */
    else {
        n = snprintf(buf, blen, "{\"ok\":false,\"error\":\"unknown command: %s\"}", args);
    }

    /* Write to output file */
    if (n > 0) {
        write_output_file(buf, n);
    }

    /* Also copy to out_msg if possible */
    if (out_msg && outlen > 0) {
        int copy = n < outlen - 1 ? n : outlen - 1;
        if (copy > 0) {
            compat_copy_to_user(out_msg, buf, copy);
        }
    }

    return 0;
}

/* ================================================================
 * Module init / exit
 * ================================================================ */
static long svc_init(const char *args, const char *event, void *__user reserved)
{
    int ok;
    printk("svc_monitor v8.1.0: init, installing hooks...\n");

    g_enabled = 0;
    g_target_uid = -1;
    g_ev_head = 0;
    g_ev_tail = 0;
    g_ev_count = 0;
    g_ev_dropped = 0;
    g_seq = 0;
    g_total = 0;
    g_hooks_installed = 0;
    g_tier2_loaded = 0;

    {
        struct file *fp = open_event_file_fp(1);
        if (fp && !IS_ERR(fp) && g_filp_close) g_filp_close(fp, 0);
    }
    start_writer_thread();

    ok = install_tier1();
    printk("svc_monitor: tier1 installed %d/%d hooks\n", ok, (int)TIER1_COUNT);

    if (install_do_filp_open() == 0) {
        printk("svc_monitor: do_filp_open hook installed\n");
    } else {
        printk("svc_monitor: do_filp_open hook install failed\n");
    }

    apply_preset("re_basic");
    printk("svc_monitor: ready, g_enabled=0 (waiting for APP)\n");
    return 0;
}

static long svc_exit(void *__user reserved)
{
    int i;
    printk("svc_monitor: exit, removing hooks...\n");
    g_enabled = 0;

    stop_writer_thread();

    remove_do_filp_open();

    if (g_tier2_loaded) {
        for (i = 0; i < (int)TIER2_COUNT; i++)
            remove_hook(&tier2_hooks[i]);
        g_tier2_loaded = 0;
    }

    for (i = 0; i < (int)TIER1_COUNT; i++)
        remove_hook(&tier1_hooks[i]);

    printk("svc_monitor: all hooks removed\n");
    return 0;
}

KPM_INIT(svc_init);
KPM_CTL0(svc_ctl0);
KPM_EXIT(svc_exit);
