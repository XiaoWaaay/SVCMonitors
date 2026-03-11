#!/system/bin/sh
# ─────────────────────────────────────────────────
# svc_bridge.sh — Android-side TCP bridge
# Reads JSONL events from KPM output and serves via TCP
# 
# Usage:
#   adb push svc_bridge.sh /data/local/tmp/
#   adb shell sh /data/local/tmp/svc_bridge.sh
#
# Then on PC:
#   adb forward tcp:9527 tcp:9527
# ─────────────────────────────────────────────────

PORT=9527
SVC_OUTPUT="/data/local/tmp/svc_events.jsonl"
POLL_INTERVAL=0.2

echo "[svc_bridge] Starting on port $PORT"
echo "[svc_bridge] Monitoring: $SVC_OUTPUT"

# Check if busybox or toybox nc is available
NC_CMD=""
if command -v busybox > /dev/null 2>&1; then
    NC_CMD="busybox nc"
elif command -v nc > /dev/null 2>&1; then
    NC_CMD="nc"
fi

serve_client() {
    echo "[svc_bridge] Client connected"
    
    if [ ! -f "$SVC_OUTPUT" ]; then
        echo "[svc_bridge] Waiting for $SVC_OUTPUT to appear..."
        while [ ! -f "$SVC_OUTPUT" ]; do
            sleep 1
        done
    fi
    
    # Tail the JSONL file and stream to client
    # -f follows the file, -n +1 reads from the beginning
    tail -f -n +1 "$SVC_OUTPUT" 2>/dev/null
}

# Main loop: accept connections
while true; do
    if [ -n "$NC_CMD" ]; then
        echo "[svc_bridge] Listening on port $PORT (using $NC_CMD)"
        serve_client | $NC_CMD -l -p $PORT 2>/dev/null
    else
        # Fallback: use Android's built-in socat if available, or a simple approach
        echo "[svc_bridge] No nc found, trying socat..."
        if command -v socat > /dev/null 2>&1; then
            socat TCP-LISTEN:$PORT,reuseaddr,fork EXEC:"tail -f -n +1 $SVC_OUTPUT" 2>/dev/null
        else
            echo "[svc_bridge] ERROR: No nc or socat available!"
            echo "[svc_bridge] Install busybox: https://github.com/nicoulaj/busybox-android"
            echo "[svc_bridge] Or use the Java bridge app instead."
            exit 1
        fi
    fi
    echo "[svc_bridge] Client disconnected, restarting..."
    sleep 1
done
