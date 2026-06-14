#!/system/bin/sh
# ============================================================
# Blitz Bypass - Notification Test Script
# Run this as root to verify notifications work
# ============================================================

APP_PACKAGE="com.youcefm.bypassctrl"

echo "=== Blitz Bypass Notification Test ==="
echo ""

# Test 1: cmd notification (most reliable)
echo "[Test 1] Using cmd notification..."
if /system/bin/cmd notification post -S bigtext \
    -t "🔥 Test Notification" \
    "bypass_test_1" \
    "If you see this, cmd notification works!" >/dev/null 2>&1; then
    echo "  ✓ cmd notification: SUCCESS"
else
    echo "  ✗ cmd notification: FAILED"
fi

sleep 1

# Test 2: am start NotificationActivity
echo ""
echo "[Test 2] Using am start NotificationActivity..."
if /system/bin/am start --user 0 -a android.intent.action.MAIN \
    -n "$APP_PACKAGE/.NotificationActivity" \
    --es title "🔥 Test Notification" \
    --es text "If you see this, am start works!" \
    -f 0x10000000 >/dev/null 2>&1; then
    echo "  ✓ am start: SUCCESS"
else
    echo "  ✗ am start: FAILED"
fi

sleep 1

# Test 3: Check if app is installed
echo ""
echo "[Test 3] Checking if app is installed..."
if /system/bin/pm list packages | grep -q "$APP_PACKAGE"; then
    echo "  ✓ App $APP_PACKAGE is installed"
else
    echo "  ✗ App $APP_PACKAGE NOT FOUND!"
    echo "    Make sure the package name matches your app."
fi

# Test 4: Check notification permission
echo ""
echo "[Test 4] Checking notification permission..."
PERM=$(/system/bin/dumpsys package "$APP_PACKAGE" | grep "android.permission.POST_NOTIFICATIONS" | head -1)
if [ -n "$PERM" ]; then
    echo "  ℹ POST_NOTIFICATIONS: $PERM"
else
    echo "  ⚠ POST_NOTIFICATIONS not found in manifest or not granted"
fi

echo ""
echo "=== Test Complete ==="
echo "If Test 1 succeeded, notifications will work from daemon."
echo "If Test 2 succeeded but Test 1 failed, daemon will use fallback."
