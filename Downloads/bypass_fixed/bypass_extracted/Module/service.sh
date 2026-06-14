#!/system/bin/sh
# ============================================================
# Blitz Bypass - Boot Service v3
# ============================================================

BYPASS_DIR="/data/bypass"
CC_NODE="/sys/class/power_supply/battery/constant_charge_current"
APP_PACKAGE="com.youcefm.bypassctrl"
ALERT_ACTIVITY="$APP_PACKAGE/.NotificationActivity"

# Create bypass directory and all required files
mkdir -p "$BYPASS_DIR"

# State file (bypass | charge)
[ -f "$BYPASS_DIR/state" ] || echo "charge" > "$BYPASS_DIR/state"

# Percentage config (enabled:threshold) e.g. 1:80
[ -f "$BYPASS_DIR/config" ] || echo "0:80" > "$BYPASS_DIR/config"

# Temperature config (enabled:trigger:resume) e.g. 1:40:38
[ -f "$BYPASS_DIR/temp_config" ] || echo "0:40:38" > "$BYPASS_DIR/temp_config"

# Log file
[ -f "$BYPASS_DIR/daemon.log" ] || touch "$BYPASS_DIR/daemon.log"

# Permissions (app needs to read/write without root)
chmod 666 "$BYPASS_DIR/state" "$BYPASS_DIR/config" "$BYPASS_DIR/temp_config" "$BYPASS_DIR/daemon.log"

# Save original constant_charge_current on first boot
if [ ! -f "$BYPASS_DIR/original_cc" ]; then
    cat "$CC_NODE" > "$BYPASS_DIR/original_cc" 2>/dev/null
    chmod 666 "$BYPASS_DIR/original_cc"
fi

# Clean auto-bypass marker on boot
rm -f "$BYPASS_DIR/auto_bypass"

# Pre-create notification channel by launching the invisible NotificationActivity
# This works even if the app is not running (no broadcast receiver needed)
/system/bin/am start --user 0 \
    -a android.intent.action.MAIN \
    -n "$ALERT_ACTIVITY" \
    --es action "create_channel" \
    -f 0x10000000 \
    >/dev/null 2>&1 || true

# Start daemon
nohup /system/bin/bypass-daemon.sh > /dev/null 2>&1 &

