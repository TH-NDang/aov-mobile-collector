package vn.ndang.chatfixture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
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

/** CI-only foreign application window. No overlay permission, account, network, or game data. */
public class EdgeHandleActivity extends Activity {
    private static final int EDGE_LEFT = 1;
    private static final int EDGE_TOP = 100;
    private static final int EDGE_WIDTH = 60;
    private static final int EDGE_HEIGHT = 200;
    private int touches;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("close", false)) { finish(); return; }
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        // A single *touchable*, non-focusable TYPE_APPLICATION edge window is a real
        // input target and eligible for Accessibility window discovery. The old
        // touchless base activity + child PopupWindow was visible in screenshots but
        // invisible to service.getWindows() on API34, so it could not test preflight.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        setFinishOnTouchOutside(false);
        // Only supplies an attached application token. This touchless base window is not a receiver.
        TextView handle = new TextView(this);
        setContentView(handle);
        View decor = getWindow().getDecorView();
        decor.setElevation(0f);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = EDGE_LEFT;
        params.y = EDGE_TOP;
        params.width = EDGE_WIDTH;
        params.height = EDGE_HEIGHT;
        params.setFitInsetsTypes(0);
        getWindow().setAttributes(params);

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
        persist();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("close", false)) finish();
    }

    @Override protected void onDestroy() {
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
