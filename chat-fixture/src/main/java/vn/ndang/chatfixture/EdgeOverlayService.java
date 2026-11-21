package vn.ndang.chatfixture;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Disposable emulator-only foreign app overlay, with no game data, account or network access. */
public class EdgeOverlayService extends Service {
    private static final int EDGE_LEFT = 1;
    private static final int EDGE_TOP = 100;
    private static final int EDGE_WIDTH = 60;
    private static final int EDGE_HEIGHT = 200;
    private int touches;
    private WindowManager manager;
    private View handle;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("close", false)) {
            detach();
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (handle != null) return START_NOT_STICKY;
        if (!Settings.canDrawOverlays(this)) {
            throw new IllegalStateException("CI fixture missing SYSTEM_ALERT_WINDOW app-op");
        }
        touches = 0;
        persist();
        manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        TextView bubble = new TextView(this);
        bubble.setText("CI\nedge");
        bubble.setTextColor(Color.WHITE);
        bubble.setBackgroundColor(Color.rgb(130, 65, 20));
        bubble.setContentDescription("Synthetic foreign edge input receiver");
        bubble.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        bubble.setAccessibilityPaneTitle("Synthetic foreign edge input receiver");
        bubble.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (!bubble.isAttachedToWindow() || !bubble.isShown() || bubble.getWidth() == 0 || bubble.getHeight() == 0) return;
                bubble.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                bubble.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                persistWindowIdentity(bubble);
            }
        });
        bubble.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                touches++;
                persist();
                view.performClick();
            }
            return true;
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                EDGE_WIDTH, EDGE_HEIGHT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = EDGE_LEFT;
        params.y = EDGE_TOP;
        manager.addView(bubble, params);
        handle = bubble;
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        detach();
        super.onDestroy();
    }

    private void detach() {
        if (manager != null && handle != null) {
            manager.removeViewImmediate(handle);
            handle = null;
        }
    }

    private void persistWindowIdentity(View bubble) {
        AccessibilityNodeInfo node = bubble.createAccessibilityNodeInfo();
        int windowId = node.getWindowId();
        node.recycle();
        int[] location = new int[2];
        bubble.getLocationOnScreen(location);
        Rect bounds = new Rect(location[0], location[1], location[0] + bubble.getWidth(), location[1] + bubble.getHeight());
        WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) bubble.getRootView().getLayoutParams();
        String status = "enabled=" + getSystemService(AccessibilityManager.class).isEnabled() +
                ",attached=" + bubble.isAttachedToWindow() + ",shown=" + bubble.isShown() +
                ",windowFocus=" + bubble.hasWindowFocus() + ",screenBounds=" + bounds +
                ",windowId=" + windowId + ",type=" + attrs.type + ",flags=" + attrs.flags;
        try {
            Files.write(new File(getFilesDir(), "edge-state.txt").toPath(), status.getBytes(StandardCharsets.UTF_8));
            Files.write(new File(getFilesDir(), "edge-window-id.txt").toPath(),
                    Integer.toString(windowId).getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void persist() {
        try {
            Files.write(new File(getFilesDir(), "edge-touches.txt").toPath(),
                    Integer.toString(touches).getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
