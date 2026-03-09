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

#include <compiler.h>
#include <kpmodule.h>
#include <linux/printk.h>
#include <uapi/asm-generic/unistd.h>
#include <linux/uaccess.h>
#include <syscall.h>
#include <linux/string.h>
#include <kputils.h>
#include <asm/current.h>

KPM_NAME("svc_monitor");
KPM_VERSION("8.1.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("SVC Monitor Team");
KPM_DESCRIPTION("Enhanced ARM64 SVC syscall monitor with deep arg parsing");

/* ================================================================
 * Constants
 * ================================================================ */
#define MAX_EVENTS      1024
#define MAX_NR          320
#define BITMAP_LONGS    (MAX_NR / 64 + 1)
#define OUTPUT_PATH     "/data/local/tmp/svc_out.json"
#define DESC_BUF_SIZE   1024
#define PATH_BUF_SIZE   256
#define SMALL_BUF       128
#define DATA_PREVIEW    64

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
    if (nr >= 0 && nr < SYSCALL_NAME_MAX && syscall_names[nr])
        return syscall_names[nr];
    return "unknown";
}

/* ================================================================
 * Global state — lock-free, volatile for single-writer/multi-reader
 * ================================================================ */
static volatile int g_enabled = 0;                  /* 0=paused, 1=active */
static volatile int g_target_uid = -1;              /* -1=all, >=0=specific */
static volatile unsigned long g_nr_bitmap[BITMAP_LONGS];

/* Hook tracking */
#define HOOK_INLINE  1
#define HOOK_FP      2

typedef struct {
    int nr;
    int narg;
    int active;
    int method;  /* HOOK_INLINE or HOOK_FP */
} hook_entry_t;

/* Event record — expanded for detailed parsing */
typedef struct {
    int nr;
    int pid;
    int uid;
    char comm[16];
    unsigned long a0, a1, a2, a3, a4, a5;
    char desc[DESC_BUF_SIZE];
} svc_event_t;

static svc_event_t g_events[MAX_EVENTS];
static volatile int g_ev_head = 0;
static volatile int g_ev_count = 0;
static volatile int g_hooks_installed = 0;
static volatile int g_tier2_loaded = 0;

/* ================================================================
 * Bitmap operations
 * ================================================================ */
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
    if (!uptr || uptr < 0x1000UL || uptr > 0x7ffffffffff0UL)
        return 0;
    /* Use compat_strncpy_from_user but treat as raw bytes */
    long ret = compat_strncpy_from_user(dst, (const char __user *)uptr, maxlen);
    if (ret < 0) return 0;
    return (int)(ret < maxlen ? ret : maxlen);
}

/* Read a user-space pointer (unsigned long) from user address */
static unsigned long safe_read_user_ptr(unsigned long uptr)
{
    unsigned long val = 0;
    if (!uptr || uptr < 0x1000UL || uptr > 0x7ffffffffff0UL)
        return 0;
    /* Read 8 bytes (pointer size on arm64) */
    if (compat_strncpy_from_user((char *)&val, (const char __user *)uptr, 8) < 0)
        return 0;
    return val;
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

    if (flags & 0x40)    n += snprintf(buf + n, blen - n, "|O_CREAT");
    if (flags & 0x80)    n += snprintf(buf + n, blen - n, "|O_EXCL");
    if (flags & 0x100)   n += snprintf(buf + n, blen - n, "|O_NOCTTY");
    if (flags & 0x200)   n += snprintf(buf + n, blen - n, "|O_TRUNC");
    if (flags & 0x400)   n += snprintf(buf + n, blen - n, "|O_APPEND");
    if (flags & 0x800)   n += snprintf(buf + n, blen - n, "|O_NONBLOCK");
    if (flags & 0x1000)  n += snprintf(buf + n, blen - n, "|O_DSYNC");
    if (flags & 0x2000)  n += snprintf(buf + n, blen - n, "|O_ASYNC");
    if (flags & 0x10000) n += snprintf(buf + n, blen - n, "|O_DIRECTORY");
    if (flags & 0x20000) n += snprintf(buf + n, blen - n, "|O_NOFOLLOW");
    if (flags & 0x40000) n += snprintf(buf + n, blen - n, "|O_CLOEXEC");
    if (flags & 0x100000) n += snprintf(buf + n, blen - n, "|O_PATH");
    if (flags & 0x200000) n += snprintf(buf + n, blen - n, "|O_TMPFILE");
    if (flags & 0x400000) n += snprintf(buf + n, blen - n, "|O_LARGEFILE");
    return n;
}

/* ================================================================
 * mmap prot/flags decoder
 * ================================================================ */
static int decode_mmap_prot(char *buf, int blen, unsigned long prot)
{
    int n = 0;
    if (prot == 0) return snprintf(buf, blen, "PROT_NONE");
    if (prot & 1) n += snprintf(buf + n, blen - n, "PROT_READ");
    if (prot & 2) n += snprintf(buf + n, blen - n, "%sPROT_WRITE", n ? "|" : "");
    if (prot & 4) n += snprintf(buf + n, blen - n, "%sPROT_EXEC", n ? "|" : "");
    return n;
}

static int decode_mmap_flags(char *buf, int blen, unsigned long flags)
{
    int n = 0;
    if (flags & 0x01) n += snprintf(buf + n, blen - n, "MAP_SHARED");
    if (flags & 0x02) n += snprintf(buf + n, blen - n, "%sMAP_PRIVATE", n ? "|" : "");
    if (flags & 0x10) n += snprintf(buf + n, blen - n, "%sMAP_FIXED", n ? "|" : "");
    if (flags & 0x20) n += snprintf(buf + n, blen - n, "%sMAP_ANONYMOUS", n ? "|" : "");
    if (flags & 0x40) n += snprintf(buf + n, blen - n, "%sMAP_GROWSDOWN", n ? "|" : "");
    if (flags & 0x100) n += snprintf(buf + n, blen - n, "%sMAP_DENYWRITE", n ? "|" : "");
    if (flags & 0x800) n += snprintf(buf + n, blen - n, "%sMAP_EXECUTABLE", n ? "|" : "");
    if (flags & 0x4000) n += snprintf(buf + n, blen - n, "%sMAP_POPULATE", n ? "|" : "");
    if (flags & 0x8000) n += snprintf(buf + n, blen - n, "%sMAP_NONBLOCK", n ? "|" : "");
    if (flags & 0x40000) n += snprintf(buf + n, blen - n, "%sMAP_STACK", n ? "|" : "");
    if (flags & 0x80000) n += snprintf(buf + n, blen - n, "%sMAP_HUGETLB", n ? "|" : "");
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
        memcpy(path, sa + 2, plen);
        path[plen] = '\0';
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
 * prctl option name decoder
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
 * ptrace request name decoder
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
        n = snprintf(desc, dlen, "dirfd=%d path=\"%s\" flags=%s(0x%lx) mode=0%lo",
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
        n = snprintf(desc, dlen, "flags=0x%lx", a0);
        if (a0 & 0x00000100) n += snprintf(desc + n, dlen - n, " CLONE_VM");
        if (a0 & 0x00000200) n += snprintf(desc + n, dlen - n, "|CLONE_FS");
        if (a0 & 0x00000400) n += snprintf(desc + n, dlen - n, "|CLONE_FILES");
        if (a0 & 0x00000800) n += snprintf(desc + n, dlen - n, "|CLONE_SIGHAND");
        if (a0 & 0x00010000) n += snprintf(desc + n, dlen - n, "|CLONE_THREAD");
        if (a0 & 0x02000000) n += snprintf(desc + n, dlen - n, "|CLONE_NEWNS");
        if (a0 & 0x04000000) n += snprintf(desc + n, dlen - n, "|CLONE_SYSVSEM");
        if (a0 & 0x00200000) n += snprintf(desc + n, dlen - n, "|CLONE_NEWPID");
        if (a0 & 0x40000000) n += snprintf(desc + n, dlen - n, "|CLONE_NEWNET");
        n += snprintf(desc + n, dlen - n, " stack=0x%lx", a1);
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

    /* Build detailed description */
    describe_args(nr, a0, a1, a2, a3, a4, a5, desc, sizeof(desc));

    /* Store event in ring buffer */
    {
        int idx = g_ev_head;
        svc_event_t *ev = &g_events[idx];
        ev->nr = nr;
        ev->pid = current->pid;
        /* Use a safer approach: get uid via official API */
        ev->uid = uid;
        ev->a0 = a0; ev->a1 = a1; ev->a2 = a2;
        ev->a3 = a3; ev->a4 = a4; ev->a5 = a5;

        /* Copy comm from task_struct */
        {
            const char *p = (const char *)current + 2560;
            int ci;
            for (ci = 0; ci < 15; ci++) {
                ev->comm[ci] = p[ci];
                if (!p[ci]) break;
            }
            ev->comm[ci < 15 ? ci : 15] = '\0';
        }

        /* Copy description */
        {
            int di;
            for (di = 0; di < DESC_BUF_SIZE - 1 && desc[di]; di++)
                ev->desc[di] = desc[di];
            ev->desc[di] = '\0';
        }

        g_ev_head = (idx + 1) % MAX_EVENTS;
        if (g_ev_count < MAX_EVENTS) g_ev_count++;
    }
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
    { 93, 1, 0, 0 },   /* exit */
    { 94, 1, 0, 0 },   /* exit_group */
    { 222, 6, 0, 0 },  /* mmap */
    { 226, 3, 0, 0 },  /* mprotect */
    { 215, 2, 0, 0 },  /* munmap */
    { 214, 1, 0, 0 },  /* brk */
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
static void apply_preset(const char *name)
{
    int i;
    /* Clear bitmap */
    for (i = 0; i < BITMAP_LONGS; i++)
        g_nr_bitmap[i] = 0;

    if (!strcmp(name, "re_basic")) {
        int nrs[] = {56,48,78,221,222,226,198,203,117,167,29,134,279,280};
        for (i = 0; i < (int)(sizeof(nrs)/sizeof(nrs[0])); i++)
            bitmap_set(g_nr_bitmap, nrs[i]);
    }
    else if (!strcmp(name, "re_full")) {
        int nrs[] = {56,48,35,78,79,63,64,221,281,220,93,94,222,226,215,198,200,203,206,207,117,167,29,25,129,131,134,135,277,280,279,146,144,147,105,273,97,268,261,291};
        for (i = 0; i < (int)(sizeof(nrs)/sizeof(nrs[0])); i++)
            bitmap_set(g_nr_bitmap, nrs[i]);
    }
    else if (!strcmp(name, "file")) {
        int nrs[] = {56,57,48,35,78,79,80,61,63,64,62,34,38,276,53,54,291,43,27,5,8};
        for (i = 0; i < (int)(sizeof(nrs)/sizeof(nrs[0])); i++)
            bitmap_set(g_nr_bitmap, nrs[i]);
    }
    else if (!strcmp(name, "net")) {
        int nrs[] = {198,200,201,202,203,206,207,208,209,210,211,212,242};
        for (i = 0; i < (int)(sizeof(nrs)/sizeof(nrs[0])); i++)
            bitmap_set(g_nr_bitmap, nrs[i]);
    }
    else if (!strcmp(name, "proc")) {
        int nrs[] = {221,281,220,93,94,95,260,117,129,130,131,270,271};
        for (i = 0; i < (int)(sizeof(nrs)/sizeof(nrs[0])); i++)
            bitmap_set(g_nr_bitmap, nrs[i]);
    }
    else if (!strcmp(name, "mem")) {
        int nrs[] = {222,226,215,214,233,279,270,271};
        for (i = 0; i < (int)(sizeof(nrs)/sizeof(nrs[0])); i++)
            bitmap_set(g_nr_bitmap, nrs[i]);
    }
    else if (!strcmp(name, "security")) {
        int nrs[] = {117,277,280,146,144,147,149,105,273,106,97,268,91,167,134};
        for (i = 0; i < (int)(sizeof(nrs)/sizeof(nrs[0])); i++)
            bitmap_set(g_nr_bitmap, nrs[i]);
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
            "\"events_buffered\":%d,\"tier2\":%s,"
            "\"logging_nrs\":[",
            g_enabled ? "true" : "false",
            g_target_uid, g_hooks_installed,
            nr_logging, g_ev_count,
            g_ev_count < MAX_EVENTS ? g_ev_count : MAX_EVENTS,
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
        bitmap_set(g_nr_bitmap, nr);
        n = snprintf(buf, blen, "{\"ok\":true,\"enabled_nr\":%d}", nr);
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
        for (i = 0; i < BITMAP_LONGS; i++)
            g_nr_bitmap[i] = 0;
        while (*p) {
            while (*p == ' ' || *p == ',') p++;
            if (*p == '\0') break;
            int consumed = 0;
            int nr = parse_int(p, &consumed);
            if (consumed > 0) {
                bitmap_set(g_nr_bitmap, nr);
                cnt++;
                p += consumed;
            } else {
                p++;
            }
        }
        n = snprintf(buf, blen, "{\"ok\":true,\"set_nrs_count\":%d}", cnt);
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

    /* ---- drain <max> ---- */
    else if (!strncmp(args, "drain ", 6) || !strcmp(args, "drain")) {
        int max = 50;
        if (!strncmp(args, "drain ", 6)) max = parse_int(args + 6, 0);
        if (max <= 0) max = 50;
        if (max > MAX_EVENTS) max = MAX_EVENTS;

        int avail = g_ev_count < MAX_EVENTS ? g_ev_count : MAX_EVENTS;
        int count = avail < max ? avail : max;
        int start;

        n = snprintf(buf, blen, "{\"ok\":true,\"count\":%d,\"total\":%d,\"events\":[", count, g_ev_count);

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
                    "{\"nr\":%d,\"name\":\"%s\",\"pid\":%d,\"uid\":%d,"
                    "\"comm\":\"%s\",\"a0\":%lu,\"a1\":%lu,\"a2\":%lu,"
                    "\"a3\":%lu,\"a4\":%lu,\"a5\":%lu,\"desc\":\"%s\"}",
                    ev->nr, get_syscall_name(ev->nr), ev->pid, ev->uid,
                    ev->comm, ev->a0, ev->a1, ev->a2,
                    ev->a3, ev->a4, ev->a5, esc);
            }
        }
        n += snprintf(buf + n, blen - n, "]}");

        g_ev_head = 0;
        g_ev_count = 0;
    }

    /* ---- events ---- */
    else if (!strcmp(args, "events")) {
        return svc_ctl0("drain 50", out_msg, outlen);
    }

    /* ---- clear ---- */
    else if (!strcmp(args, "clear")) {
        g_ev_head = 0;
        g_ev_count = 0;
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
    g_ev_count = 0;
    g_hooks_installed = 0;
    g_tier2_loaded = 0;

    ok = install_tier1();
    printk("svc_monitor: tier1 installed %d/%d hooks\n", ok, (int)TIER1_COUNT);

    apply_preset("re_basic");
    printk("svc_monitor: ready, g_enabled=0 (waiting for APP)\n");
    return 0;
}

static long svc_exit(void *__user reserved)
{
    int i;
    printk("svc_monitor: exit, removing hooks...\n");
    g_enabled = 0;

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
