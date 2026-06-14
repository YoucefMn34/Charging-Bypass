#!/system/bin/sh
# ============================================================
# Blitz Bypass Daemon v5 - AlertActivity Notifications
# ============================================================

# Paths
BYPASS_DIR="/data/bypass"
STATE_FILE="$BYPASS_DIR/state"
CONFIG_FILE="$BYPASS_DIR/config"
TEMP_CONFIG_FILE="$BYPASS_DIR/temp_config"
ORIGINAL_CC_FILE="$BYPASS_DIR/original_cc"
LOG_FILE="$BYPASS_DIR/daemon.log"
AUTO_BYPASS_MARKER="$BYPASS_DIR/auto_bypass"
CC_NODE="/sys/class/power_supply/battery/constant_charge_current"

# App package
APP_PACKAGE="com.youcefm.bypassctrl"
ALERT_ACTIVITY="$APP_PACKAGE/.NotificationActivity"

# Config tracking
LAST_PERCENT_CONFIG=""
LAST_TEMP_CONFIG=""

# Log helper (keeps last 150 lines)
log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"
    tail -n 150 "$LOG_FILE" > "$LOG_FILE.tmp" 2>/dev/null
    mv "$LOG_FILE.tmp" "$LOG_FILE" 2>/dev/null
}

# Notification helper - uses AlertActivity (popup dialog)
# This is more reliable than cmd notification on custom ROMs
notify() {
    local title="$1"
    local text="$2"

    # Launch transparent activity with alert dialog
    /system/bin/am start --user 0 \
        -a android.intent.action.MAIN \
        -n "$ALERT_ACTIVITY" \
        --es title "$title" \
        --es text "$text" \
        -f 0x10000000 \
        >/dev/null 2>&1

    log_msg "NOTIFY: $title - $text"
}

# Get battery temp in °C
get_temp() {
    local raw=$(cat /sys/class/power_supply/battery/temp 2>/dev/null)
    [ -n "$raw" ] && echo $((raw / 10)) || echo "0"
}

# Get battery percentage
get_level() {
    cat /sys/class/power_supply/battery/capacity 2>/dev/null || echo "0"
}

# Check if charging
is_charging() {
    local status=$(cat /sys/class/power_supply/battery/status 2>/dev/null)
    [ "$status" = "Charging" ] || [ "$status" = "Full" ]
}

# Read original CC
read_original_cc() {
    if [ -f "$ORIGINAL_CC_FILE" ]; then
        cat "$ORIGINAL_CC_FILE"
    else
        cat "$CC_NODE" 2>/dev/null || echo "3000000"
    fi
}

# Apply bypass
apply_bypass() {
    echo 0 > "$CC_NODE" 2>/dev/null
}

# Apply charge
apply_charge() {
    local cc=$(read_original_cc)
    echo "$cc" > "$CC_NODE" 2>/dev/null
}

# Check manual bypass
is_manual_bypass() {
    local current_state=$(cat "$STATE_FILE" 2>/dev/null | tr -d ' \n')
    if [ "$current_state" = "bypass" ] && [ ! -f "$AUTO_BYPASS_MARKER" ]; then
        echo "true"
    else
        echo "false"
    fi
}

# ============================================================
# MAIN LOOP
# ============================================================

[ -d "$BYPASS_DIR" ] || mkdir -p "$BYPASS_DIR"
[ -f "$STATE_FILE" ] || echo "charge" > "$STATE_FILE"
[ -f "$CONFIG_FILE" ] || echo "0:80" > "$CONFIG_FILE"
[ -f "$TEMP_CONFIG_FILE" ] || echo "0:40:38" > "$TEMP_CONFIG_FILE"
[ -f "$LOG_FILE" ] || touch "$LOG_FILE"
chmod 666 "$STATE_FILE" "$CONFIG_FILE" "$TEMP_CONFIG_FILE" "$LOG_FILE" 2>/dev/null

if [ ! -f "$ORIGINAL_CC_FILE" ]; then
    cat "$CC_NODE" > "$ORIGINAL_CC_FILE" 2>/dev/null
    chmod 666 "$ORIGINAL_CC_FILE"
fi

rm -f "$AUTO_BYPASS_MARKER"

LAST_TEMP_STATE="normal"
LAST_PERCENT_STATE="normal"
LAST_PLUGGED="unknown"
LAST_STATE="charge"

log_msg "=== Daemon v5 started ==="

while true; do
    CURRENT_STATE=$(cat "$STATE_FILE" 2>/dev/null | tr -d ' \n')
    [ -z "$CURRENT_STATE" ] && CURRENT_STATE="charge"

    # Reset states on manual bypass->charge
    if [ "$LAST_STATE" = "bypass" ] && [ "$CURRENT_STATE" = "charge" ]; then
        if [ "$LAST_PERCENT_STATE" = "triggered" ]; then
            LAST_PERCENT_STATE="normal"
            rm -f "$AUTO_BYPASS_MARKER"
            log_msg "RESET: bypass->charge, percent state reset"
        fi
        if [ "$LAST_TEMP_STATE" = "hot" ]; then
            LAST_TEMP_STATE="normal"
            rm -f "$AUTO_BYPASS_MARKER"
            log_msg "RESET: bypass->charge, temp state reset"
        fi
    fi
    LAST_STATE="$CURRENT_STATE"

    CONFIG=$(cat "$CONFIG_FILE" 2>/dev/null | tr -d ' \n')
    PERCENT_ENABLED=$(echo "$CONFIG" | cut -d: -f1)
    PERCENT_THRESHOLD=$(echo "$CONFIG" | cut -d: -f2)

    TEMP_CONFIG=$(cat "$TEMP_CONFIG_FILE" 2>/dev/null | tr -d ' \n')
    TEMP_ENABLED=$(echo "$TEMP_CONFIG" | cut -d: -f1)
    TEMP_TRIGGER=$(echo "$TEMP_CONFIG" | cut -d: -f2)
    TEMP_RESUME=$(echo "$TEMP_CONFIG" | cut -d: -f3)

    if [ "$CONFIG" != "$LAST_PERCENT_CONFIG" ] && [ -n "$CONFIG" ]; then
        log_msg "CONFIG: Percentage threshold ${PERCENT_THRESHOLD}% saved (enabled=$PERCENT_ENABLED)"
        LAST_PERCENT_CONFIG="$CONFIG"
    fi

    if [ "$TEMP_CONFIG" != "$LAST_TEMP_CONFIG" ] && [ -n "$TEMP_CONFIG" ]; then
        log_msg "CONFIG: Temperature threshold ${TEMP_TRIGGER}°C/${TEMP_RESUME}°C saved (enabled=$TEMP_ENABLED)"
        LAST_TEMP_CONFIG="$TEMP_CONFIG"
    fi

    TEMP=$(get_temp)
    LEVEL=$(get_level)
    CHARGING=$(is_charging && echo "true" || echo "false")
    PLUGGED=$(cat /sys/class/power_supply/battery/present 2>/dev/null)

    MANUAL_BYPASS=$(is_manual_bypass)

    if [ "$MANUAL_BYPASS" = "false" ]; then

        if [ "$TEMP_ENABLED" = "1" ] && [ "$CHARGING" = "true" ]; then
            if [ "$TEMP" -ge "$TEMP_TRIGGER" ] && [ "$LAST_TEMP_STATE" = "normal" ]; then
                echo "bypass" > "$STATE_FILE"
                touch "$AUTO_BYPASS_MARKER"
                notify "🔥 Temp Alert" "Battery temp ${TEMP}°C — bypass triggered until cooldown to ${TEMP_RESUME}°C"
                log_msg "AUTO-TEMP: ${TEMP}°C >= ${TEMP_TRIGGER}°C, bypass triggered"
                LAST_TEMP_STATE="hot"
            elif [ "$TEMP" -le "$TEMP_RESUME" ] && [ "$LAST_TEMP_STATE" = "hot" ]; then
                echo "charge" > "$STATE_FILE"
                rm -f "$AUTO_BYPASS_MARKER"
                notify "❄️ Cooled Down" "Battery temp ${TEMP}°C — charging resumed"
                log_msg "AUTO-TEMP: ${TEMP}°C <= ${TEMP_RESUME}°C, charging resumed"
                LAST_TEMP_STATE="normal"
            fi
        fi

        if [ "$CHARGING" = "false" ] && [ "$LAST_TEMP_STATE" = "hot" ]; then
            LAST_TEMP_STATE="normal"
            rm -f "$AUTO_BYPASS_MARKER"
            log_msg "RESET: Charger unplugged, temp state reset"
        fi

        if [ "$PERCENT_ENABLED" = "1" ] && [ "$CHARGING" = "true" ]; then
            if [ "$LEVEL" -ge "$PERCENT_THRESHOLD" ] && [ "$LAST_PERCENT_STATE" = "normal" ]; then
                echo "bypass" > "$STATE_FILE"
                touch "$AUTO_BYPASS_MARKER"
                notify "🔋 Percentage Reached" "Battery ${LEVEL}% — bypass triggered"
                log_msg "AUTO-PERCENT: ${LEVEL}% >= ${PERCENT_THRESHOLD}%, bypass triggered"
                LAST_PERCENT_STATE="triggered"
            fi
        fi

        if [ "$CHARGING" = "false" ] && [ "$LAST_PERCENT_STATE" = "triggered" ]; then
            LAST_PERCENT_STATE="normal"
            rm -f "$AUTO_BYPASS_MARKER"
            log_msg "RESET: Charger unplugged, percent state reset"
        fi

    fi

    if [ "$CURRENT_STATE" = "bypass" ]; then
        apply_bypass
    else
        apply_charge
    fi

    if [ "$CHARGING" = "false" ] && [ "$CURRENT_STATE" = "bypass" ] && [ "$MANUAL_BYPASS" = "false" ]; then
        echo "charge" > "$STATE_FILE"
        rm -f "$AUTO_BYPASS_MARKER"
        LAST_TEMP_STATE="normal"
        LAST_PERCENT_STATE="normal"
        log_msg "AUTO: Charger unplugged while bypass, reverted to charge"
    fi

    LAST_PLUGGED="$PLUGGED"

    sleep 3
done

