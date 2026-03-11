#!/usr/bin/env python3
"""
SVC Monitor PC Viewer — Backend Server
Flask + SocketIO for real-time syscall event streaming and analysis.

Communication chain:
  KPM module -> JSONL file -> Android bridge (TCP) -> ADB forward -> This server -> WebSocket -> Browser

Usage:
  pip install flask flask-socketio
  python app.py
"""

import os
import sys
import json
import time
import socket
import threading
import argparse
import subprocess
from datetime import datetime
from collections import defaultdict

from flask import Flask, render_template, request, jsonify, send_file
from flask_socketio import SocketIO, emit

# ─────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────
DEFAULT_BRIDGE_HOST = "127.0.0.1"
DEFAULT_BRIDGE_PORT = 9527
DEFAULT_WEB_PORT = 5001

# ─────────────────────────────────────────────────
# App setup
# ─────────────────────────────────────────────────
app = Flask(__name__,
            template_folder=os.path.join(os.path.dirname(__file__), "..", "templates"),
            static_folder=os.path.join(os.path.dirname(__file__), "..", "static"))
app.config["SECRET_KEY"] = "svc-monitor-secret"
socketio = SocketIO(app, cors_allowed_origins="*", async_mode="threading")


# ─────────────────────────────────────────────────
# Event normalization (support multiple upstream formats)
# ─────────────────────────────────────────────────
def normalize_event(ev):
    if not isinstance(ev, dict):
        return None

    out = dict(ev)
    now_ns = time.time_ns()

    if "tgid" in out and "pid" in out:
        if "tid" not in out:
            out["tid"] = out.get("pid", 0)
        out["pid"] = out.get("tgid", out.get("pid", 0))

    if "timestamp_ns" not in out:
        out["timestamp_ns"] = now_ns

    if "ret" in out and "retval" not in out:
        out["retval"] = out.get("ret")

    if "tid" not in out:
        out["tid"] = out.get("pid", 0)

    if "args" not in out:
        if "desc" in out:
            out["args"] = out.get("desc", "")

    if "has_retval" not in out:
        out["has_retval"] = "retval" in out and out.get("retval") is not None

    bt = out.get("bt")
    if bt is not None:
        if isinstance(bt, list):
            out["backtrace"] = bt
            out["bt_depth"] = out.get("bt_depth", len([x for x in bt if x]))
        else:
            out["bt_depth"] = out.get("bt_depth", 0)

    if "bt_hex" not in out:
        if isinstance(out.get("backtrace"), list):
            out["bt_hex"] = " ".join(
                ("0x%x" % int(x)) if isinstance(x, int) else str(x)
                for x in out.get("backtrace", [])
            )

    if "antidebug" not in out:
        s = (out.get("args") or "") + "\n" + (out.get("desc") or "")
        s = s.lower()
        name = (out.get("name") or "").lower()
        nr = out.get("nr", -1)
        out["antidebug"] = (
            nr in (117, 129, 134) or
            "ptrace" in name or
            "/proc/self/maps" in s or
            "/proc/self/mem" in s or
            "frida" in s or
            "xposed" in s
        )

    if "duration_ns" not in out:
        out["duration_ns"] = 0

    fp_chain = out.get("fp_chain")
    if fp_chain and isinstance(fp_chain, str):
        args = out.get("args") or ""
        if fp_chain not in args:
            out["args"] = (args + "\n" + fp_chain).strip()

    return out


# ─────────────────────────────────────────────────
# In-memory event store (all events, no limit)
# ─────────────────────────────────────────────────
class EventStore:
    """Thread-safe event storage with indexing for fast queries."""

    def __init__(self):
        self.lock = threading.Lock()
        self.events = []               # all events in order
        self.by_pid = defaultdict(list) # pid -> [event indices]
        self.by_tid = defaultdict(list) # tid -> [event indices]
        self.by_nr = defaultdict(list)  # syscall nr -> [event indices]
        self.by_name = defaultdict(list)# syscall name -> [event indices]
        self.by_comm = defaultdict(list)# comm -> [event indices]
        self.stats = {
            "total": 0,
            "by_syscall": defaultdict(int),
            "by_pid": defaultdict(int),
            "antidebug_count": 0,
            "start_time": None,
            "last_time": None,
        }

    def add(self, event):
        with self.lock:
            idx = len(self.events)
            self.events.append(event)

            pid = event.get("pid", 0)
            tid = event.get("tid", pid)
            nr = event.get("nr", -1)
            comm = event.get("comm", "")
            name = event.get("name", f"syscall_{nr}")

            self.by_pid[pid].append(idx)
            self.by_tid[tid].append(idx)
            self.by_nr[nr].append(idx)
            if name:
                self.by_name[name].append(idx)
            if comm:
                self.by_comm[comm].append(idx)

            self.stats["total"] = idx + 1
            self.stats["by_syscall"][name] += 1
            self.stats["by_pid"][pid] += 1
            if event.get("antidebug"):
                self.stats["antidebug_count"] += 1

            ts = event.get("timestamp_ns", 0)
            if self.stats["start_time"] is None:
                self.stats["start_time"] = ts
            self.stats["last_time"] = ts

            return idx

    def get_event(self, idx):
        with self.lock:
            if 0 <= idx < len(self.events):
                return self.events[idx]
            return None

    def search(self, query=None, pid=None, comm=None, nr=None,
               name=None,
               tid=None,
               antidebug_only=False, has_retval=False,
               offset=0, limit=100):
        """Full-text search across all events. No limit on total results."""
        with self.lock:
            # Determine candidate indices
            if tid is not None:
                candidates = self.by_tid.get(tid, [])
            elif pid is not None:
                candidates = self.by_pid.get(pid, [])
            elif nr is not None:
                candidates = self.by_nr.get(nr, [])
            elif name is not None:
                candidates = self.by_name.get(name, [])
            elif comm is not None:
                candidates = self.by_comm.get(comm, [])
            else:
                candidates = range(len(self.events))

            results = []
            for idx in candidates:
                ev = self.events[idx]

                if antidebug_only and not ev.get("antidebug"):
                    continue
                if has_retval and not ev.get("has_retval"):
                    continue
                if query:
                    q = query.lower()
                    searchable = json.dumps(ev, ensure_ascii=False).lower()
                    if q not in searchable:
                        continue

                results.append(idx)

            total = len(results)
            page = results[offset:offset + limit]
            return {
                "total": total,
                "offset": offset,
                "limit": limit,
                "events": [self.events[i] for i in page],
                "indices": page,
            }

    def get_pid_summary(self):
        """Get summary for all PIDs."""
        with self.lock:
            summaries = []
            for pid, indices in self.by_pid.items():
                if not indices:
                    continue
                first = self.events[indices[0]]
                last = self.events[indices[-1]]
                syscalls = defaultdict(int)
                antidebug = 0
                for i in indices:
                    ev = self.events[i]
                    syscalls[ev.get("name", f"nr_{ev.get('nr')}")] += 1
                    if ev.get("antidebug"):
                        antidebug += 1

                summaries.append({
                    "pid": pid,
                    "comm": first.get("comm", ""),
                    "uid": first.get("uid", 0),
                    "tid_count": len(set(self.events[i].get("tid", pid) for i in indices)),
                    "event_count": len(indices),
                    "antidebug_count": antidebug,
                    "first_ts": first.get("timestamp_ns", 0),
                    "last_ts": last.get("timestamp_ns", 0),
                    "top_syscalls": dict(sorted(syscalls.items(), key=lambda x: -x[1])[:10]),
                })
            return sorted(summaries, key=lambda x: -x["event_count"])

    def get_pid_events(self, pid, offset=0, limit=200):
        """Get all events for a specific PID."""
        with self.lock:
            indices = self.by_pid.get(pid, [])
            total = len(indices)
            page = indices[offset:offset + limit]
            return {
                "pid": pid,
                "total": total,
                "offset": offset,
                "limit": limit,
                "events": [self.events[i] for i in page],
            }

    def get_pid_timeline(self, pid):
        """Get timeline data for ECharts visualization."""
        with self.lock:
            indices = self.by_pid.get(pid, [])
            timeline = []
            for i in indices:
                ev = self.events[i]
                timeline.append({
                    "ts": ev.get("timestamp_ns", 0),
                    "name": ev.get("name", ""),
                    "nr": ev.get("nr", 0),
                    "duration": ev.get("duration_ns", 0),
                    "retval": ev.get("retval", None),
                    "antidebug": ev.get("antidebug", False),
                    "tid": ev.get("tid", 0),
                    "idx": i,
                })
            return timeline

    def get_global_stats(self):
        """Real-time dashboard statistics."""
        with self.lock:
            return {
                "total_events": self.stats["total"],
                "unique_pids": len(self.by_pid),
                "unique_syscalls": len(self.by_nr),
                "antidebug_count": self.stats["antidebug_count"],
                "top_syscalls": dict(sorted(
                    self.stats["by_syscall"].items(),
                    key=lambda x: -x[1]
                )[:20]),
                "top_pids": dict(sorted(
                    self.stats["by_pid"].items(),
                    key=lambda x: -x[1]
                )[:20]),
                "start_time": self.stats["start_time"],
                "last_time": self.stats["last_time"],
            }

    def export_jsonl(self, pid=None):
        """Export events as JSONL string."""
        with self.lock:
            if pid is not None:
                indices = self.by_pid.get(pid, [])
                evts = [self.events[i] for i in indices]
            else:
                evts = self.events
            return "\n".join(json.dumps(e, ensure_ascii=False) for e in evts)

    def export_csv(self, pid=None):
        """Export as CSV."""
        with self.lock:
            if pid is not None:
                indices = self.by_pid.get(pid, [])
                evts = [self.events[i] for i in indices]
            else:
                evts = self.events

            if not evts:
                return "no events"

            # Collect all keys
            keys = []
            seen = set()
            for e in evts[:100]:  # sample first 100 for keys
                for k in e.keys():
                    if k not in seen:
                        keys.append(k)
                        seen.add(k)

            lines = [",".join(keys)]
            for e in evts:
                row = []
                for k in keys:
                    v = e.get(k, "")
                    s = str(v).replace('"', '""')
                    if "," in s or '"' in s or "\n" in s:
                        s = f'"{s}"'
                    row.append(s)
                lines.append(",".join(row))
            return "\n".join(lines)

    def clear(self):
        with self.lock:
            self.events = []
            self.by_pid = defaultdict(list)
            self.by_tid = defaultdict(list)
            self.by_nr = defaultdict(list)
            self.by_name = defaultdict(list)
            self.by_comm = defaultdict(list)
            self.stats = {
                "total": 0,
                "by_syscall": defaultdict(int),
                "by_pid": defaultdict(int),
                "antidebug_count": 0,
                "start_time": None,
                "last_time": None,
            }


store = EventStore()

app_socket_lock = threading.Lock()
app_socket_file = None
app_socket_sock = None


def app_socket_send(cmd):
    global app_socket_file
    with app_socket_lock:
        f = app_socket_file
        if not f:
            return False
        try:
            if isinstance(cmd, str):
                cmd = cmd.encode("utf-8")
            f.write(cmd + b"\n")
            return True
        except:
            return False


# ─────────────────────────────────────────────────
# Bridge connection (reads from Android device)
# ─────────────────────────────────────────────────
class BridgeClient:
    """Connects to the Android bridge via TCP (through adb forward)."""

    def __init__(self, host, port):
        self.host = host
        self.port = port
        self.sock = None
        self.running = False
        self.thread = None
        self.connected = False
        self.reconnect_interval = 3
        self._last_stats_emit = 0.0

    def start(self):
        self.running = True
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def stop(self):
        self.running = False
        if self.sock:
            try:
                self.sock.close()
            except:
                pass

    def _connect(self):
        while self.running:
            try:
                self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.sock.settimeout(5)
                self.sock.connect((self.host, self.port))
                self.sock.settimeout(None)
                self.connected = True
                print(f"[Bridge] Connected to {self.host}:{self.port}")
                socketio.emit("bridge_status", {"connected": True})
                return True
            except Exception as e:
                self.connected = False
                print(f"[Bridge] Connection failed: {e}, retrying in {self.reconnect_interval}s...")
                time.sleep(self.reconnect_interval)
        return False

    def _run(self):
        while self.running:
            if not self._connect():
                break

            buf = b""
            try:
                while self.running:
                    data = self.sock.recv(65536)
                    if not data:
                        break
                    buf += data
                    while b"\n" in buf:
                        line, buf = buf.split(b"\n", 1)
                        line = line.strip()
                        if not line:
                            continue
                        try:
                            event = json.loads(line.decode("utf-8", errors="replace"))
                            event = normalize_event(event)
                            if event is None:
                                continue
                            idx = store.add(event)
                            # Broadcast to all connected browsers
                            socketio.emit("new_event", {
                                "idx": idx,
                                "event": event,
                            })
                            now = time.time()
                            if now - self._last_stats_emit > 0.5:
                                self._last_stats_emit = now
                                socketio.emit("stats_update", store.get_global_stats())
                        except json.JSONDecodeError:
                            pass
            except Exception as e:
                print(f"[Bridge] Disconnected: {e}")

            self.connected = False
            socketio.emit("bridge_status", {"connected": False})
            if self.running:
                print(f"[Bridge] Reconnecting in {self.reconnect_interval}s...")
                time.sleep(self.reconnect_interval)


bridge = None


# ─────────────────────────────────────────────────
# Also support loading from JSONL file directly
# ─────────────────────────────────────────────────
def load_jsonl_file(filepath):
    """Load events from a JSONL file (for offline analysis)."""
    count = 0
    with open(filepath, "r") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                event = normalize_event(json.loads(line))
                if event is not None:
                    store.add(event)
                count += 1
            except json.JSONDecodeError:
                pass
    print(f"[File] Loaded {count} events from {filepath}")
    return count


# ─────────────────────────────────────────────────
# REST API routes
# ─────────────────────────────────────────────────
@app.route("/")
def index():
    return send_file(os.path.join(app.static_folder, "index.html"))


@app.route("/api/stats")
def api_stats():
    return jsonify(store.get_global_stats())


@app.route("/api/ingest", methods=["POST"])
def api_ingest():
    data = request.get_json(silent=True)
    if data is None:
        return jsonify({"ok": False, "error": "invalid_json"}), 400

    items = data if isinstance(data, list) else [data]
    added = 0
    last_idx = None
    for raw in items:
        ev = normalize_event(raw)
        if ev is None:
            continue
        idx = store.add(ev)
        last_idx = idx
        added += 1
        socketio.emit("new_event", {"idx": idx, "event": ev})

    socketio.emit("stats_update", store.get_global_stats())
    return jsonify({"ok": True, "added": added, "last_idx": last_idx})


@app.route("/api/clear", methods=["POST"])
def api_clear():
    store.clear()
    socketio.emit("stats_update", store.get_global_stats())
    return jsonify({"ok": True})


@app.route("/api/app/stop", methods=["POST"])
def api_app_stop():
    ok = app_socket_send("CMD STOP")
    return jsonify({"ok": ok})


@app.route("/api/app/clear", methods=["POST"])
def api_app_clear():
    ok = app_socket_send("CMD CLEAR_EVENTS")
    return jsonify({"ok": ok})


@app.route("/api/app/clear_history", methods=["POST"])
def api_app_clear_history():
    ok = app_socket_send("CMD CLEAR_HISTORY")
    return jsonify({"ok": ok})


@app.route("/api/maps")
def api_maps():
    pid = request.args.get("pid", None, type=int)
    if pid is None or pid <= 0:
        return jsonify({"ok": False, "error": "pid_required"}), 400

    def run(cmd):
        try:
            p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=8)
            out = (p.stdout or b"").decode("utf-8", errors="ignore")
            err = (p.stderr or b"").decode("utf-8", errors="ignore")
            return p.returncode, (out if out.strip() else err)
        except Exception as e:
            return -1, str(e)

    rc, out = run(["adb", "shell", "cat", f"/proc/{pid}/maps"])
    if rc != 0 or not out.strip():
        rc2, out2 = run(["adb", "shell", "su", "-c", f"cat /proc/{pid}/maps"])
        if rc2 == 0 and out2.strip():
            return jsonify({"ok": True, "pid": pid, "maps": out2})
        return jsonify({"ok": False, "pid": pid, "error": out.strip() or out2.strip() or "read maps failed"}), 500

    return jsonify({"ok": True, "pid": pid, "maps": out})

def app_socket_client(addr):
    def parse_addr(a):
        if not a:
            return None
        if "://" in a:
            a = a.split("://", 1)[1]
        if ":" not in a:
            return None
        host, port_s = a.rsplit(":", 1)
        try:
            return host, int(port_s)
        except:
            return None

    hp = parse_addr(addr)
    if not hp:
        print(f"[app-socket] invalid addr: {addr}")
        return

    global app_socket_file, app_socket_sock
    host, port = hp
    while True:
        s = None
        try:
            s = socket.create_connection((host, port), timeout=5)
            s.settimeout(30)
            f = s.makefile("rwb", buffering=0)
            f.write(b"PING\n")
            pong = f.readline().decode("utf-8", errors="ignore").strip()
            if pong != "PONG":
                raise RuntimeError(f"bad handshake: {pong!r}")
            print(f"[app-socket] connected {host}:{port}")
            with app_socket_lock:
                app_socket_sock = s
                app_socket_file = f

            while True:
                try:
                    line = f.readline()
                except socket.timeout:
                    try:
                        f.write(b"PING\n")
                        _ = f.readline()
                        continue
                    except:
                        raise
                if not line:
                    raise RuntimeError("eof")
                try:
                    raw = json.loads(line.decode("utf-8", errors="ignore"))
                except:
                    continue
                ev = normalize_event(raw)
                if ev is None:
                    continue
                idx = store.add(ev)
                socketio.emit("new_event", {"idx": idx, "event": ev})
                socketio.emit("stats_update", store.get_global_stats())
        except Exception as e:
            try:
                print(f"[app-socket] disconnected: {e}")
            except:
                pass
            time.sleep(1)
        finally:
            with app_socket_lock:
                if app_socket_sock is s:
                    app_socket_sock = None
                    app_socket_file = None
            try:
                if s:
                    s.close()
            except:
                pass


@app.route("/api/search")
def api_search():
    q = request.args.get("q", None)
    pid = request.args.get("pid", None, type=int)
    tid = request.args.get("tid", None, type=int)
    comm = request.args.get("comm", None)
    nr_raw = request.args.get("nr", None)
    nr = None
    name = None
    if nr_raw is not None:
        try:
            nr = int(nr_raw)
        except:
            name = str(nr_raw)
    antidebug = request.args.get("antidebug", "0") == "1"
    has_retval = request.args.get("has_retval", "0") == "1"
    offset = request.args.get("offset", 0, type=int)
    limit = request.args.get("limit", 100, type=int)

    result = store.search(
        query=q, pid=pid, tid=tid, comm=comm, nr=nr, name=name,
        antidebug_only=antidebug, has_retval=has_retval,
        offset=offset, limit=limit,
    )
    return jsonify(result)


@app.route("/api/thread_analyze")
def api_thread_analyze():
    pid = request.args.get("pid", None, type=int)
    if pid is None:
        return jsonify({"ok": False, "error": "pid_required"}), 400

    with store.lock:
        indices = store.by_pid.get(pid, [])
        tid_count = defaultdict(int)
        edges = []
        for i in indices:
            ev = store.events[i]
            tid = ev.get("tid", pid)
            tid_count[tid] += 1
            nr = ev.get("nr", -1)
            name = (ev.get("name") or "").lower()
            if nr in (220, 435) or "clone" in name:
                rv = ev.get("retval", None)
                if isinstance(rv, int) and rv > 0:
                    edges.append({"parent": tid, "child": rv, "idx": i})

    threads = [{"tid": t, "count": c} for t, c in sorted(tid_count.items(), key=lambda x: -x[1])][:200]
    children = defaultdict(list)
    has_parent = set()
    parents = set()
    for e in edges:
        p = e["parent"]
        c = e["child"]
        children[p].append(c)
        has_parent.add(c)
        parents.add(p)
    for p in children:
        children[p] = sorted(list(dict.fromkeys(children[p])))
    roots = sorted([p for p in parents if p not in has_parent])
    seen = set()
    lines = []

    def dfs(n, indent):
        if n in seen:
            return
        seen.add(n)
        lines.append(f"{indent}{n}")
        for c in children.get(n, []):
            dfs(c, indent + "  ")

    if roots:
        for r in roots:
            dfs(r, "")
    else:
        ks = sorted(children.keys())
        if ks:
            dfs(ks[0], "")

    return jsonify({
        "ok": True,
        "pid": pid,
        "threads": threads,
        "edge_count": len(edges),
        "tree": "\n".join(lines),
    })


@app.route("/api/event/<int:idx>")
def api_event_detail(idx):
    ev = store.get_event(idx)
    if ev is None:
        return jsonify({"error": "not found"}), 404
    return jsonify(ev)


@app.route("/api/pids")
def api_pids():
    return jsonify(store.get_pid_summary())


@app.route("/api/pid/<int:pid>/events")
def api_pid_events(pid):
    offset = request.args.get("offset", 0, type=int)
    limit = request.args.get("limit", 200, type=int)
    return jsonify(store.get_pid_events(pid, offset, limit))


@app.route("/api/pid/<int:pid>/timeline")
def api_pid_timeline(pid):
    return jsonify(store.get_pid_timeline(pid))


@app.route("/api/export/jsonl")
def api_export_jsonl():
    pid = request.args.get("pid", None, type=int)
    data = store.export_jsonl(pid)
    fname = f"svc_events_pid{pid}.jsonl" if pid else "svc_events_all.jsonl"
    # Write to temp file
    tmp = os.path.join("/tmp", fname)
    with open(tmp, "w") as f:
        f.write(data)
    return send_file(tmp, as_attachment=True, download_name=fname)


@app.route("/api/export/csv")
def api_export_csv():
    pid = request.args.get("pid", None, type=int)
    data = store.export_csv(pid)
    fname = f"svc_events_pid{pid}.csv" if pid else "svc_events_all.csv"
    tmp = os.path.join("/tmp", fname)
    with open(tmp, "w") as f:
        f.write(data)
    return send_file(tmp, as_attachment=True, download_name=fname)


@app.route("/api/load", methods=["POST"])
def api_load_file():
    """Load a JSONL file for offline analysis."""
    if "file" not in request.files:
        return jsonify({"error": "no file"}), 400
    f = request.files["file"]
    tmp = os.path.join("/tmp", "upload_events.jsonl")
    f.save(tmp)
    count = load_jsonl_file(tmp)
    return jsonify({"loaded": count, "total": store.stats["total"]})


@app.route("/api/bridge/status")
def api_bridge_status():
    return jsonify({
        "connected": bridge.connected if bridge else False,
        "host": bridge.host if bridge else None,
        "port": bridge.port if bridge else None,
    })


# ─────────────────────────────────────────────────
# WebSocket events
# ─────────────────────────────────────────────────
@socketio.on("connect")
def handle_connect():
    print(f"[WS] Client connected: {request.sid}")
    emit("bridge_status", {
        "connected": bridge.connected if bridge else False
    })
    emit("stats_update", store.get_global_stats())


@socketio.on("request_stats")
def handle_request_stats():
    emit("stats_update", store.get_global_stats())


@socketio.on("request_pid_timeline")
def handle_pid_timeline(data):
    pid = data.get("pid", 0)
    emit("pid_timeline", {
        "pid": pid,
        "timeline": store.get_pid_timeline(pid),
    })


# ─────────────────────────────────────────────────
# Periodic stats broadcast
# ─────────────────────────────────────────────────
def stats_broadcaster():
    """Push stats every 2 seconds."""
    last_sig = None
    while True:
        time.sleep(2)
        try:
            s = store.get_global_stats()
            sig = (
                s.get("total_events"),
                s.get("unique_pids"),
                s.get("unique_syscalls"),
                s.get("antidebug_count"),
                s.get("last_time"),
            )
            if sig != last_sig:
                last_sig = sig
                socketio.emit("stats_update", s)
        except:
            pass


# ─────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────
def main():
    global bridge

    parser = argparse.ArgumentParser(description="SVC Monitor PC Viewer")
    parser.add_argument("--port", type=int, default=DEFAULT_WEB_PORT,
                        help=f"Web server port (default: {DEFAULT_WEB_PORT})")
    parser.add_argument("--bridge-host", default=DEFAULT_BRIDGE_HOST,
                        help=f"Bridge host (default: {DEFAULT_BRIDGE_HOST})")
    parser.add_argument("--bridge-port", type=int, default=DEFAULT_BRIDGE_PORT,
                        help=f"Bridge port (default: {DEFAULT_BRIDGE_PORT})")
    parser.add_argument("--load", type=str, default=None,
                        help="Load JSONL file for offline analysis")
    parser.add_argument("--no-bridge", action="store_true",
                        help="Don't connect to bridge (offline mode)")
    parser.add_argument("--app-socket", type=str, default=None,
                        help="Connect to Android App ServerSocket, e.g. 127.0.0.1:8080")
    args = parser.parse_args()

    def choose_web_port(preferred):
        if preferred == 0:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.bind(("127.0.0.1", 0))
            p = s.getsockname()[1]
            s.close()
            return p
        for p in range(preferred, preferred + 20):
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            try:
                s.bind(("0.0.0.0", p))
                s.close()
                return p
            except OSError:
                try:
                    s.close()
                except:
                    pass
        return preferred

    chosen_port = choose_web_port(args.port)
    if chosen_port != args.port:
        print(f"[web] port {args.port} unavailable, using {chosen_port}")
        args.port = chosen_port

    # Load file if specified
    if args.load:
        load_jsonl_file(args.load)

    # Start bridge connection
    if not args.no_bridge:
        bridge = BridgeClient(args.bridge_host, args.bridge_port)
        bridge.start()

    if args.app_socket:
        threading.Thread(target=app_socket_client, args=(args.app_socket,), daemon=True).start()

    # Start stats broadcaster
    threading.Thread(target=stats_broadcaster, daemon=True).start()

    print(f"\n{'='*50}")
    print(f"  SVC Monitor PC Viewer")
    print(f"  Web UI: http://localhost:{args.port}")
    if not args.no_bridge:
        print(f"  Bridge: {args.bridge_host}:{args.bridge_port}")
    if args.load:
        print(f"  Loaded: {store.stats['total']} events")
    print(f"{'='*50}\n")

    socketio.run(app, host="0.0.0.0", port=args.port, debug=False,
                 allow_unsafe_werkzeug=True)


if __name__ == "__main__":
    main()
