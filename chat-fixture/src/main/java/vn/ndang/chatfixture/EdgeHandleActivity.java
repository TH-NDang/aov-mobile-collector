package vn.ndang.chatfixture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
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

/** CI-only foreign touchable overlay. The app has no account, network, or game data. */
public class EdgeHandleActivity extends Activity {
    private static final int EDGE_LEFT = 1;
    private static final int EDGE_TOP = 100;
    private static final int EDGE_WIDTH = 60;
    private static final int EDGE_HEIGHT = 200;
    private int touches;
    private WindowManager overlayManager;
    private View overlayView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("close", false)) { finish(); return; }
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        // A non-focusable, touchless activity is merely the fixture controller.
        // The real receiver is a separately permissioned TYPE_APPLICATION_OVERLAY
        // above the focused full-screen game. The old TYPE_APPLICATION child/host
        // was visible but omitted by API34 AccessibilityService.getWindows().
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        setFinishOnTouchOutside(false);
        setContentView(new View(this));
        View decor = getWindow().getDecorView();
        if (!Settings.canDrawOverlays(this)) {
            throw new IllegalStateException("CI fixture does not have SYSTEM_ALERT_WINDOW app-op");
        }
        TextView handle = new TextView(this);
        overlayManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                EDGE_WIDTH, EDGE_HEIGHT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = EDGE_LEFT;
        params.y = EDGE_TOP;
        handle.setText("CI\nedge");
        handle.setTextColor(Color.WHITE);
        handle.setBackgroundColor(Color.rgb(130, 65, 20));
        handle.setContentDescription("Synthetic foreign edge input receiver");
        handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        handle.setAccessibilityPaneTitle("Synthetic foreign edge input receiver");
        handle.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (!handle.isShown() || handle.getWidth() == 0 || handle.getHeight() == 0) return;
                handle.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                // Announce this real, visible pane without requesting input or accessibility focus.
                handle.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                persistWindowIdentity(handle, decor);
            }
        });
        handle.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                touches++;
                persist();
                view.performClick();
            }
            return true;
        });
        overlayManager.addView(handle, params);
        overlayView = handle;
        persist();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("close", false)) finish();
    }

    @Override protected void onDestroy() {
        if (overlayView != null && overlayManager != null) {
            overlayManager.removeViewImmediate(overlayView);
            overlayView = null;
        }
        super.onDestroy();
    }

    private void persistWindowIdentity(View handle, View decor) {
        AccessibilityNodeInfo node = handle.createAccessibilityNodeInfo();
        int windowId = node.getWindowId();
        node.recycle();
        AccessibilityNodeInfo hostNode = decor.createAccessibilityNodeInfo();
        int hostWindowId = hostNode.getWindowId();
        hostNode.recycle();
        int[] location = new int[2];
        handle.getLocationOnScreen(location);
        Rect bounds = new Rect(location[0], location[1], location[0] + handle.getWidth(), location[1] + handle.getHeight());
        WindowManager.LayoutParams child = (WindowManager.LayoutParams) handle.getRootView().getLayoutParams();
        String stateText = "enabled=" + getSystemService(AccessibilityManager.class).isEnabled() +
                ",attached=" + handle.isAttachedToWindow() + ",shown=" + handle.isShown() +
                ",windowFocus=" + handle.hasWindowFocus() + ",screenBounds=" + bounds +
                ",windowId=" + windowId + ",type=" + child.type + ",flags=" + child.flags +
                ",hostWindowId=" + hostWindowId + ",hostFlags=" + getWindow().getAttributes().flags;
        try {
            Files.write(new File(getFilesDir(), "edge-state.txt").toPath(), stateText.getBytes(StandardCharsets.UTF_8));
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
