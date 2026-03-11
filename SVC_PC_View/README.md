# SVC Monitor PC Viewer — 逆向分析平台

## 架构

```
┌─────────────┐     ┌──────────────┐     ┌────────────┐     ┌──────────────┐
│  KPM Module  │────▶│  Android APP │────▶│ PC Server  │────▶│ Web Browser  │
│  (Kernel)    │     │  (Bridge)    │     │ (Python)   │     │ (Dashboard)  │
│              │     │              │     │            │     │              │
│ JSONL events │     │ TCP Socket   │     │ Flask +    │     │ Vue3 + ECharts│
│ /data/svc/   │     │ port 9527    │     │ SocketIO   │     │ Timeline     │
└─────────────┘     └──────────────┘     └────────────┘     └──────────────┘
                     ADB Forward ──────────▶
                     tcp:9527 → tcp:9527
```

## 功能

- **实时事件流**: WebSocket 推送，零延迟
- **无限搜索**: 全量事件搜索，不限数量
- **PID 分析**: 按进程聚合，展示进程时间线
- **事件详情**: 点击展开完整参数、栈回溯、VMA、耗时
- **syscall 统计**: 频率热力图、耗时分布
- **反调试检测**: 高亮 antidebug 事件
- **导出**: JSONL / CSV 一键导出
- **离线分析**: 拖拽 JSONL 文件直接导入

## 快速开始

### 方式一：实时联动（推荐）

```bash
# 1. PC 端启动 ADB Bridge（自动通过 adb shell 读取设备上的 JSONL）
python android_bridge/adb_bridge.py

# 2. PC 端启动 Web 服务器
pip install flask flask-socketio
python server/app.py

# 3. 打开浏览器
open http://localhost:5000
```

### 方式二：设备端 Bridge + ADB Forward

```bash
# 1. 手机端启动 bridge
adb push android_bridge/svc_bridge.sh /data/local/tmp/
adb shell sh /data/local/tmp/svc_bridge.sh

# 2. ADB 端口转发
adb forward tcp:9527 tcp:9527

# 3. PC 端启动 Web 服务器
python server/app.py

# 4. 打开浏览器
open http://localhost:5000
```

### 方式三：离线分析

```bash
# 1. 从设备拉取 JSONL 文件
adb pull /data/local/tmp/svc_events.jsonl ./

# 2. 直接加载离线文件
python server/app.py --no-bridge --load events.jsonl

# 或者在 Web 界面中拖拽上传
```

## 项目结构

```
svc_pc_viewer/
├── README.md
├── server/
│   └── app.py              # Flask + SocketIO 后端
├── android_bridge/
│   ├── adb_bridge.py       # PC 端 ADB Bridge（推荐）
│   └── svc_bridge.sh       # 设备端 Shell Bridge
├── templates/
│   └── index.html          # Vue3 单页前端
├── static/                  # 静态资源（如需要）
└── test_events.jsonl       # 测试数据
```
