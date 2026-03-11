#!/usr/bin/env python3
"""
adb_bridge.py — PC-side ADB bridge
Reads JSONL events directly from device via 'adb shell' and serves as local TCP.
No need to install anything on the device.

Usage:
  python adb_bridge.py                     # default: read from /data/svc_monitor/events.jsonl
  python adb_bridge.py --file /path/to/events.jsonl
  python adb_bridge.py --port 9527
"""

import subprocess
import socket
import threading
import argparse
import time
import sys
import json
import signal
import os

DEFAULT_PORT = 9527
DEFAULT_FILE = "/data/local/tmp/svc_events.jsonl"
_SHUTDOWN_MAGIC = b"__SVC_BRIDGE_SHUTDOWN__\n"
PID_DIR = "/tmp"


class ADBBridge:
    def __init__(self, device_file, port, serial=None):
        self.device_file = device_file
        self.port = port
        self.serial = serial
        self.clients = []
        self.lock = threading.Lock()
        self.stop_event = threading.Event()
        self._srv = None
        self._adb_proc = None
        self._pidfile = os.path.join(PID_DIR, f"svc_monitor_adb_bridge_{self.port}.pid")

    def _adb_cmd(self, *args):
        cmd = ["adb"]
        if self.serial:
            cmd += ["-s", self.serial]
        cmd += list(args)
        return cmd

    def _broadcast(self, line):
        """Send a line to all connected clients."""
        data = (line + "\n").encode("utf-8")
        with self.lock:
            dead = []
            for c in self.clients:
                try:
                    c.sendall(data)
                except:
                    dead.append(c)
            for c in dead:
                self.clients.remove(c)
                try:
                    c.close()
                except:
                    pass

    def _adb_reader(self):
        """Continuously read from adb shell cat/tail."""
        while not self.stop_event.is_set():
            print(f"[ADB] Starting: adb shell tail -f -n +1 {self.device_file}")
            try:
                cmd = self._adb_cmd("shell", f"tail -f -n +1 {self.device_file}")
                proc = subprocess.Popen(cmd, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, bufsize=0)
                self._adb_proc = proc
                for raw_line in proc.stdout:
                    if self.stop_event.is_set():
                        break
                    line = raw_line.decode("utf-8", errors="replace").strip()
                    if "Permission denied" in line or "No such file" in line:
                        break
                    if not line:
                        continue
                    # Validate JSON
                    try:
                        json.loads(line)
                        self._broadcast(line)
                    except json.JSONDecodeError:
                        pass  # skip non-JSON lines

                proc.wait()
                print(f"[ADB] Process exited with code {proc.returncode}")
            except Exception as e:
                print(f"[ADB] Error: {e}")
            finally:
                self._adb_proc = None

            if self.stop_event.is_set():
                break

            print("[ADB] Fallback: try su -c tail (if available)")
            try:
                cmd = self._adb_cmd("shell", f"su -c 'tail -f -n +1 {self.device_file}'")
                proc = subprocess.Popen(cmd, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, bufsize=0)
                self._adb_proc = proc
                for raw_line in proc.stdout:
                    if self.stop_event.is_set():
                        break
                    line = raw_line.decode("utf-8", errors="replace").strip()
                    if not line:
                        continue
                    try:
                        json.loads(line)
                        self._broadcast(line)
                    except json.JSONDecodeError:
                        pass
                proc.wait()
                print(f"[ADB] su tail exited with code {proc.returncode}")
            except Exception as e:
                print(f"[ADB] su tail error: {e}")
            finally:
                self._adb_proc = None

            if self.stop_event.is_set():
                break

            print(f"[ADB] Reconnecting in 3s...")
            for _ in range(30):
                if self.stop_event.is_set():
                    break
                time.sleep(0.1)

    def _tcp_server(self):
        """Accept TCP connections from the PC viewer."""
        srv = self._srv
        if not srv:
            return
        srv.listen(5)
        srv.settimeout(1.0)
        print(f"[TCP] Listening on 127.0.0.1:{self.port}")

        while not self.stop_event.is_set():
            try:
                client, addr = srv.accept()
            except socket.timeout:
                continue
            except OSError:
                break

            try:
                client.settimeout(0.2)
                head = client.recv(64)
                if head == _SHUTDOWN_MAGIC:
                    try:
                        client.close()
                    except:
                        pass
                    self.stop()
                    break
                client.settimeout(None)
            except Exception:
                pass

            print(f"[TCP] Client connected: {addr}")
            with self.lock:
                self.clients.append(client)

    def run(self):
        print(f"{'='*50}")
        print(f"  SVC Monitor ADB Bridge")
        print(f"  Device file: {self.device_file}")
        print(f"  TCP port: {self.port}")
        print(f"{'='*50}")

        self._bind_or_kill_then_bind()
        self._write_pidfile()

        # Start ADB reader thread
        t1 = threading.Thread(target=self._adb_reader, daemon=True)
        t1.start()

        try:
            self._tcp_server()
        except KeyboardInterrupt:
            pass
        finally:
            self.stop()

    def stop(self):
        if self.stop_event.is_set():
            return
        self.stop_event.set()

        p = self._adb_proc
        if p is not None:
            try:
                p.terminate()
            except:
                pass
            self._adb_proc = None

        with self.lock:
            cs = list(self.clients)
            self.clients.clear()
        for c in cs:
            try:
                c.close()
            except:
                pass

        s = self._srv
        self._srv = None
        if s is not None:
            try:
                s.close()
            except:
                pass

        self._remove_pidfile()

    def _bind_or_kill_then_bind(self):
        self._kill_previous_by_pidfile()

        def try_bind():
            srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
            except Exception:
                pass
            srv.bind(("127.0.0.1", self.port))
            return srv

        for _ in range(5):
            try:
                self._srv = try_bind()
                return
            except OSError as e:
                if getattr(e, "errno", None) != 48:
                    raise
                time.sleep(0.1)

        try:
            self._srv = try_bind()
            return
        except OSError as e:
            if getattr(e, "errno", None) != 48:
                raise

        try:
            s = socket.create_connection(("127.0.0.1", self.port), timeout=0.3)
            try:
                s.sendall(_SHUTDOWN_MAGIC)
            finally:
                s.close()
            time.sleep(0.2)
        except Exception:
            pass

        last = None
        for _ in range(20):
            try:
                self._srv = try_bind()
                return
            except OSError as e:
                last = e
                if getattr(e, "errno", None) != 48:
                    raise
                time.sleep(0.1)

        raise last if last else OSError(48, "Address already in use")

    def _write_pidfile(self):
        try:
            with open(self._pidfile, "w") as f:
                f.write(str(os.getpid()))
        except Exception:
            pass

    def _remove_pidfile(self):
        try:
            if os.path.exists(self._pidfile):
                os.remove(self._pidfile)
        except Exception:
            pass

    def _kill_previous_by_pidfile(self):
        try:
            if not os.path.exists(self._pidfile):
                return
            with open(self._pidfile, "r") as f:
                s = f.read().strip()
            if not s:
                return
            pid = int(s)
            if pid <= 1 or pid == os.getpid():
                return

            try:
                os.kill(pid, 0)
            except Exception:
                self._remove_pidfile()
                return

            cmd = ""
            try:
                out = subprocess.check_output(["ps", "-p", str(pid), "-o", "command="], text=True).strip()
                cmd = out
            except Exception:
                cmd = ""

            if "adb_bridge.py" not in cmd and "SVC_PC_View/android_bridge/adb_bridge.py" not in cmd:
                return

            try:
                os.kill(pid, signal.SIGTERM)
            except Exception:
                return

            for _ in range(20):
                try:
                    os.kill(pid, 0)
                    time.sleep(0.1)
                except Exception:
                    break

            self._remove_pidfile()
        except Exception:
            pass


def main():
    parser = argparse.ArgumentParser(description="SVC Monitor ADB Bridge")
    parser.add_argument("--file", default=DEFAULT_FILE,
                        help=f"Device JSONL path (default: {DEFAULT_FILE})")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT,
                        help=f"Local TCP port (default: {DEFAULT_PORT})")
    parser.add_argument("-s", "--serial", default=None,
                        help="ADB device serial (if multiple devices)")
    args = parser.parse_args()

    bridge = ADBBridge(args.file, args.port, args.serial)
    def _sig(_signum, _frame):
        bridge.stop()
        raise SystemExit(0)

    try:
        signal.signal(signal.SIGINT, _sig)
        signal.signal(signal.SIGTERM, _sig)
    except Exception:
        pass

    bridge.run()


if __name__ == "__main__":
    main()
