## 目标

把 [/Users/bytedance/Desktop/GithubProject/SVC_Call/SVCMonitor](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/SVCMonitor) 里的 Android APK 工程与 KPM 模块工程迁移到仓库根目录（不再作为子目录存在），并确保：

- KPM 模块可在本机交叉编译生成可被 APatch/KernelPatch 加载的 `.kpm`；
- Android APP 可用 Gradle/Android Studio 正常编译产出 APK；
- APP 与模块的命令协议一致，手机端可通过 APatch 加载后正常使用。

## 现状盘点

- 当前仓库根目录已有一套 KPM 工程（`src/` + 根目录 `Makefile`，产物 `module.kpm`）。
- 新增的 SVCMonitor 目录包含两套工程：
  - `SVCMonitor/kpm/`：模块名 `svc-monitor`，命令通过 `kpm ctl0 svc-monitor "<cmd>"`。
  - `SVCMonitor/app/`：Android Studio 工程，`KpmBridge.kt` 固定调用模块名 `svc-monitor`。
- `SVCMonitor/kpm/Makefile` 当前引用了 `$(KP_DIR)/kernel/kpm.lds`，而仓库内 KernelPatch 子模块里不存在该文件；需要与当前仓库的 KPM 构建方式对齐（`ld -r` 生成 relocatable）。
- `SVCMonitor/kpm/src/include/svc_monitor.h` 直接访问 `current->pid/tgid/cred/comm`，在 KernelPatch 提供的裁剪头环境下可能出现“不完整类型”导致编译失败，需要改为“offset/辅助函数”方式（类似当前根目录模块对 `task_struct` 的处理方式）。

## 目标目录结构（迁移后）

在仓库根目录新增/调整为：

- `android/`：Android APP 工程根（由 `SVCMonitor/app` 迁移而来）
- `kpm/`：KPM 模块工程根（由 `SVCMonitor/kpm` 迁移而来）
- `SVCMonitor/`：迁移完成后删除（或仅保留 README 内容并合并到根目录 README）

说明：

- 现有根目录 `src/` 的模块可以保留为历史实现，但默认构建目标切换为新 `kpm/`（避免用户误用旧模块）；如你希望同时保留两套模块并可选择构建，会在 Makefile 中提供两个 target。

## 实施步骤（不省略任何关键点）

### 1) 迁移文件到根目录

1. 将 `SVCMonitor/app` 整体移动到根目录 `android/`。
2. 将 `SVCMonitor/kpm` 整体移动到根目录 `kpm/`。
3. 删除 `SVCMonitor/` 目录（如果 README 内容需要保留，则先把使用说明合并到根目录 `README.md` 或 `项目说明.md`）。
4. 确认 Android 工程内部引用路径没有写死 `SVCMonitor/...`（例如 wrapper、settings.gradle 等一般不依赖相对父路径）。

### 2) 统一并修复 KPM 的构建系统

目标：`cd kpm && make` 直接生成 `svc-monitor.kpm`（或统一命名为 `module.kpm`，二选一，推荐保留 `svc-monitor.kpm` 便于区分模块名）。

1. 修复 `kpm/Makefile`：
   - 移除对不存在的 `kpm.lds` 的依赖，改为与当前仓库一致的 `$(LD) -r -o ...` 方式链接；
   - include 路径改为直接使用仓库根 `./KernelPatch` 子模块（相对路径从 `kpm/` 出发通常为 `../KernelPatch`）；
   - toolchain 变量对齐仓库风格：
     - 支持使用 `TARGET_COMPILE`（如 `aarch64-none-elf-`）或 `CROSS_COMPILE`（如 `aarch64-linux-gnu-`）二选一；
     - 在 Makefile 内给出一致的优先级（例如优先读 `TARGET_COMPILE`，否则回落到 `CROSS_COMPILE`）。
2. 修复/补齐编译宏与 include：
   - 确保包含 `KernelPatch/kernel/include`、`KernelPatch/kernel/patch/include`、`KernelPatch/kernel/linux/include`、`KernelPatch/kernel/linux/arch/arm64/include` 等与根目录模块一致的路径集合；
   - 确保 `hook_fargs*`、`fp_hook_syscalln`、`compat_copy_to_user` 等符号所在头文件均可被正确 include。

### 3) 修复 SVCMonitor KPM 代码的 KernelPatch 头兼容性

目标：避免对 `struct task_struct/cred` 的直接字段访问，保证在 KernelPatch 的裁剪头环境下稳定编译。

1. 在 `kpm/src/include/svc_monitor.h` 中：
   - 将 `svc_current_pid/tgid/uid/comm` 改为：
     - `pid/tgid`：基于 `task_struct_offset.*_offset` 的 `uintptr_t` 偏移读取；
     - `uid`：通过 `task_struct_offset.cred_offset` + `cred_offset.uid_offset` 获取 `kuid_t.val`；
     - `comm`：使用 `get_task_comm(current)`（KernelPatch 提供的安全 helper）。
   - 增加必要头文件：`<ktypes.h> <stdint.h> <linux/sched.h> <linux/cred.h> <asm/current.h>` 等。
2. 将 `lookup_name("ktime_get_ns")` 替换为 `kallsyms_lookup_name("ktime_get_ns")`（KernelPatch 环境可用且有声明），并做空指针检查。
3. 遍历 `kpm/src/*.c`，统一确保：
   - 不在 spinlock 持锁区内进行文件 I/O、较重的字符串拼接、堆分配；
   - CTL0 输出缓冲区写入使用 `snprintf` 且确保越界保护；
   - hook 回调内避免递归（如有需要增加 re-entrance guard）。

### 4) 根目录构建入口调整（同时支持 APK 与 KPM）

目标：在仓库根目录提供清晰的一键构建入口，避免用户进入子目录手动找 Makefile/Gradle。

1. 更新根目录 `Makefile`：
   - 添加 `make kpm` / `make kpm_monitor`：调用 `kpm/Makefile` 产出 `kpm/svc-monitor.kpm`；
   - 保留现有 `make`（如果你希望默认构建新模块，则把 `all` 默认指向 `kpm`；如你希望保留旧模块默认，则新增 `make kpm_monitor` 并在 README 中写清楚）。
2. 在根目录新增（或更新）文档：
   - `项目说明.md` 增加两部分：
     - KPM 构建：根目录如何编译 `svc-monitor.kpm`；
     - Android 构建：`cd android && ./gradlew assembleDebug` 或 Android Studio 打开 `android/`。

### 5) 编译与设备侧验证（验收标准）

1. 本地编译验证：
   - `make clean && make kpm`（或 `cd kpm && make`）成功；
   - `cd android && ./gradlew :app:assembleDebug` 成功生成 APK（或 Android Studio Sync + Build APK 成功）。
2. 手机端验证（APatch/KernelPatch）：
   - 推送并加载 `svc-monitor.kpm`；
   - `su -c "kpatch <SuperKey> kpm list"` 能看到 `svc-monitor`；
   - `su -c "kpatch <SuperKey> kpm ctl0 svc-monitor status"` 返回 JSON 且字段合理；
   - 安装 APP，填入 SuperKey，APP 能 `Connect` 并能拉取 `status/events`。

## 风险与回滚策略

- 风险：迁移后根目录已有旧模块与新模块并存导致误构建/误加载。
  - 策略：Makefile 明确 target 名称；文档明确模块文件名与模块名；默认构建目标按你偏好选择。
- 风险：不同工具链（`aarch64-linux-gnu-` vs `aarch64-none-elf-`）导致编译差异。
  - 策略：Makefile 同时支持两种变量输入，并在文档中给出推荐配置与示例。

