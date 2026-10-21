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
import android.widget.PopupWindow;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** CI-only foreign application window. No overlay permission, account, network, or game data. */
public class EdgeHandleActivity extends Activity {
    private int touches;
    private PopupWindow panel;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("close", false)) { finish(); return; }
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        setFinishOnTouchOutside(false);
        // Only supplies an attached application token. This touchless base window is not a receiver.
        setContentView(new View(this));
        View decor = getWindow().getDecorView();
        decor.setElevation(0f);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 0;
        params.width = 1;
        params.height = 1;
        params.setFitInsetsTypes(0);
        getWindow().setAttributes(params);

        TextView handle = new TextView(this);
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
        panel = new PopupWindow(handle, 60, 200, false);
        panel.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL);
        panel.setTouchable(true);
        panel.setTouchModal(false);
        panel.setOutsideTouchable(false);
        panel.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        panel.setIsLaidOutInScreen(true);
        panel.setAttachedInDecor(false);
        // Keep normal display clipping; disabling it would add FLAG_LAYOUT_NO_LIMITS.
        panel.setClippingEnabled(true);
        panel.setIsClippedToScreen(false);
        panel.setElevation(0f);
        panel.setAnimationStyle(0);
        panel.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        decor.post(() -> {
            if (!isFinishing() && !isDestroyed()) {
                // showAtLocation uses this actual attached decor's window token, not an app token.
                panel.showAtLocation(decor, Gravity.TOP | Gravity.LEFT, 1, 100);
            }
        });
        persist();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("close", false)) finish();
    }

    @Override protected void onDestroy() {
        if (panel != null) panel.dismiss();
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
